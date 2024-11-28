package io.fairspace.saturn.services.maintenance;

import java.sql.SQLException;
import java.util.Date;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import lombok.NonNull;
import lombok.extern.log4j.Log4j2;
import org.apache.jena.query.Dataset;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.tdb2.DatabaseMgr;
import org.apache.jena.tdb2.store.DatasetGraphSwitchable;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import io.fairspace.saturn.config.properties.ViewsProperties;
import io.fairspace.saturn.rdf.transactions.TxnIndexDatasetGraph;
import io.fairspace.saturn.rdf.transactions.TxnLogDatasetGraph;
import io.fairspace.saturn.services.AccessDeniedException;
import io.fairspace.saturn.services.ConflictException;
import io.fairspace.saturn.services.NotAvailableException;
import io.fairspace.saturn.services.users.UserService;
import io.fairspace.saturn.services.views.ViewService;
import io.fairspace.saturn.services.views.ViewStoreClientFactory;
import io.fairspace.saturn.services.views.ViewUpdater;

@Log4j2
@Service
public class MaintenanceService {

    public static final String SERVICE_NOT_AVAILABLE = "Service not available";
    public static final String MAINTENANCE_IS_IN_PROGRESS = "Maintenance is in progress.";

    private final ThreadPoolExecutor threadpool =
            new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new LinkedBlockingQueue<>());

    private final ViewsProperties viewsProperties;
    private final UserService userService;
    private final Dataset dataset;
    private final ViewStoreClientFactory viewStoreClientFactory;
    private final ViewService viewService;
    private final String publicUrl;

    public MaintenanceService(
            ViewsProperties viewsProperties,
            @NonNull UserService userService,
            @Qualifier("dataset") @NonNull Dataset dataset,
            @Nullable ViewStoreClientFactory viewStoreClientFactory,
            ViewService viewService,
            @Value("${application.publicUrl}") String publicUrl) {
        this.viewsProperties = viewsProperties;
        this.userService = userService;
        this.dataset = dataset;
        this.viewStoreClientFactory = viewStoreClientFactory;
        this.viewService = viewService;
        this.publicUrl = publicUrl;
    }

    public boolean disabled() {
        return viewStoreClientFactory == null;
    }

    public boolean active() {
        return threadpool.getActiveCount() > 0;
    }

    public synchronized void startRecreateIndexTask() {
        if (!userService.currentUser().isAdmin()) {
            throw new AccessDeniedException();
        }
        if (disabled()) {
            throw new NotAvailableException(SERVICE_NOT_AVAILABLE);
        }
        if (active()) {
            log.info(MAINTENANCE_IS_IN_PROGRESS);
            throw new ConflictException(MAINTENANCE_IS_IN_PROGRESS);
        }

        threadpool.submit(() -> {
            log.info("Start asynchronous reindexing task");
            recreateIndex();
            viewService.refreshCaches();
            log.info("Asynchronous reindexing task has finished.");
        });
    }

    public void compactRdfStorageTask() {
        if (!userService.currentUser().isAdmin()) {
            throw new AccessDeniedException();
        }
        if (active()) {
            log.info(MAINTENANCE_IS_IN_PROGRESS);
            throw new ConflictException(MAINTENANCE_IS_IN_PROGRESS);
        }

        threadpool.submit(() -> {
            log.info("Compacting RDF storage started");
            try {
                var ds = unwrap(dataset.asDatasetGraph());
                if (ds == null) {
                    log.warn("Compacting RDF storage is not supported for this storage type");
                    return;
                }
                DatabaseMgr.compact(ds, true);
            } catch (Exception e) {
                log.error("Error compacting RDF storage", e);
                throw new RuntimeException("Error compacting RDF storage", e);
            }
            log.info("Compacting RDF storage finished");
        });
    }

    /**
     * Only use this method in a secure and synchonisized way, see 'recreateIndex()'
     */
    public void recreateIndex() {
        try (var viewStoreClient = viewStoreClientFactory.build();
                var viewUpdater =
                        new ViewUpdater(viewsProperties, viewStoreClient, dataset.asDatasetGraph(), publicUrl)) {
            var start = new Date().getTime();
            // Index entities
            for (var view : viewsProperties.views) {
                viewUpdater.recreateIndexForView(viewStoreClient, view);
            }
            viewUpdater.commit();
            log.info("View index recreated in {}ms.", new Date().getTime() - start);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to recreate index", e);
        }
    }

    /**
     * Unwrap the dataset graph to the underlying dataset graph that supports
     * compacting.
     *
     * @param dsg the dataset graph
     * @return the underlying dataset graph that supports compacting
     */
    public static DatasetGraph unwrap(DatasetGraph dsg) {
        return switch (dsg) {
            case null -> dsg;
            case DatasetGraphSwitchable ignored -> dsg;
            case TxnLogDatasetGraph txnLogDatasetGraph -> unwrap(txnLogDatasetGraph.getDatasetGraph());
            case TxnIndexDatasetGraph txnIndexDatasetGraph -> unwrap(txnIndexDatasetGraph.getDatasetGraph());
            default -> null;
        };
    }
}
