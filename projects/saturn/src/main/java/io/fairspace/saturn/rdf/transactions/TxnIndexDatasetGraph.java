package io.fairspace.saturn.rdf.transactions;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.jena.graph.Node;
import org.apache.jena.query.ReadWrite;
import org.apache.jena.query.TxnType;
import org.apache.jena.query.text.changes.TextQuadAction;
import org.apache.jena.sparql.core.DatasetGraph;

import io.fairspace.saturn.config.properties.ViewsProperties;
import io.fairspace.saturn.rdf.AbstractChangesAwareDatasetGraph;
import io.fairspace.saturn.services.views.ViewStoreClientFactory;
import io.fairspace.saturn.services.views.ViewUpdater;

import static io.fairspace.saturn.services.users.UserService.currentUserAsSymbol;

@Slf4j
public class TxnIndexDatasetGraph extends AbstractChangesAwareDatasetGraph {

    private final ViewsProperties viewsProperties;
    private final DatasetGraph dsg;
    private final ViewStoreClientFactory viewStoreClientFactory;
    private final String publicUrl;
    // One set of updated subjects if write transactions are handled sequentially.
    // If many write transactions can be active simultaneously, this set needs to be
    // tied to the active thread.
    private final Set<Node> updatedSubjects = new HashSet<>();

    public TxnIndexDatasetGraph(
            ViewsProperties viewsProperties,
            DatasetGraph dsg,
            ViewStoreClientFactory viewStoreClientFactory,
            String publicUrl) {
        super(dsg);
        this.viewsProperties = viewsProperties;
        this.dsg = dsg;
        this.viewStoreClientFactory = viewStoreClientFactory;
        this.publicUrl = publicUrl;
    }

    private void markSubject(Node subject) {
        updatedSubjects.add(subject);
    }

    /**
     * Collects changes
     */
    @Override
    protected void onChange(TextQuadAction action, Node graph, Node subject, Node predicate, Node object) {
        switch (action) {
            case ADD, DELETE -> markSubject(subject);
        }
    }

    public DatasetGraph getDatasetGraph() {
        return dsg;
    }

    @Override
    public void begin(TxnType type) {
        begin(TxnType.convert(type));
        if (isInWriteTransaction()) {
            updatedSubjects.clear();
        }
    }

    @SneakyThrows
    @Override
    public void commit() {
        if (isInWriteTransaction()) {
            if (isExtraStorageTransaction()) {
                updatedSubjects.clear();
            } else {
                var sessionKey = currentUserAsSymbol();
                var doViewsUpdate = dsg.getContext()
                        .get(sessionKey, Boolean.FALSE); // false by default, should be set explicitly to switch it off
                if (doViewsUpdate) {
                    log.info("Commit {} updated subjects", updatedSubjects.size());
                    var start = new Date().getTime();
                    try (var viewStoreClient = viewStoreClientFactory.build();
                            var viewUpdater = new ViewUpdater(viewsProperties, viewStoreClient, dsg, publicUrl)) {
                        updatedSubjects.forEach(viewUpdater::updateSubject);
                        viewUpdater.commit();
                        log.debug(
                                "Updating {} subjects took {}ms", updatedSubjects.size(), new Date().getTime() - start);
                    } catch (Exception e) {
                        log.error(
                                "Updating {} subjects failed after {}ms",
                                updatedSubjects.size(),
                                new Date().getTime() - start,
                                e);
                        throw e;
                    } finally {
                        updatedSubjects.clear();
                    }
                } else {
                    log.debug("Skipping views update");
                }
            }
        }
        super.commit();
    }

    @Override
    public void abort() {
        super.abort();
        if (isInWriteTransaction()) {
            log.debug("Aborting transaction");
            updatedSubjects.clear();
        }
    }

    private boolean isInWriteTransaction() {
        return transactionMode() == ReadWrite.WRITE;
    }

    private boolean isExtraStorageTransaction() {
        return updatedSubjects.stream()
                .anyMatch(r -> r.isURI() && r.getURI().startsWith(publicUrl + "/api/extra-storage"));
    }
}
