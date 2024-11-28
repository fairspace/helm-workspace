package io.fairspace.saturn.services.views;

import java.sql.SQLTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import io.milton.resource.CollectionResource;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import io.fairspace.saturn.controller.dto.CountDto;
import io.fairspace.saturn.controller.dto.ValueDto;
import io.fairspace.saturn.controller.dto.ViewPageDto;
import io.fairspace.saturn.controller.dto.request.CountRequest;
import io.fairspace.saturn.controller.dto.request.ViewRequest;
import io.fairspace.saturn.rdf.transactions.Transactions;
import io.fairspace.saturn.rdf.transactions.TxnIndexDatasetGraph;

import static io.fairspace.saturn.webdav.PathUtils.getCollectionNameByUri;

import static java.lang.Integer.min;

/**
 * JDBC implementation of the query service. Depends on the
 * {@link TxnIndexDatasetGraph} dataset graph wrapper to store
 * changes to the metadata graph in a separate view database.
 * Queries are performed by the {@link ViewStoreReader}.
 * The database is initialised by the {@link ViewStoreClientFactory}.
 */
@Log4j2
public class JdbcQueryService implements QueryService {

    private final Transactions transactions;
    private final CollectionResource rootSubject;
    private final ViewStoreReader viewStoreReader;

    public JdbcQueryService(
            Transactions transactions, CollectionResource rootSubject, ViewStoreReader viewStoreReader) {
        this.transactions = transactions;
        this.rootSubject = rootSubject;
        this.viewStoreReader = viewStoreReader;
    }

    @SneakyThrows
    protected void applyCollectionsFilterIfRequired(String view, List<ViewFilter> filters) {
        boolean collectionsFilterRequired = view.equalsIgnoreCase("Resource")
                || filters.stream()
                        .anyMatch(filter -> filter.getField().split("_")[0].equalsIgnoreCase("Resource"));
        if (!collectionsFilterRequired) {
            return;
        }
        var collections = transactions.calculateRead(m -> rootSubject.getChildren().stream()
                .map(collection -> (Object) getCollectionNameByUri(rootSubject.getUniqueId(), collection.getUniqueId()))
                .collect(Collectors.toList()));
        if (filters.stream().anyMatch(filter -> filter.getField().equalsIgnoreCase("Resource_collection"))) {
            // Update existing filters in place
            filters.stream()
                    .filter(filter -> filter.getField().equalsIgnoreCase("Resource_collection"))
                    .forEach(filter -> filter.setValues(filter.values.stream()
                            .map(value -> getCollectionNameByUri(rootSubject.getUniqueId(), value.toString()))
                            .filter(collections::contains)
                            .collect(Collectors.toList())));
            return;
        }
        // Add collection name filter
        filters.add(ViewFilter.builder()
                .field("Resource_collection")
                .values(collections)
                .build());
    }

    @SneakyThrows
    public ViewPageDto retrieveViewPage(ViewRequest request) {
        int page = (request.getPage() != null && request.getPage() >= 1) ? request.getPage() : 1;
        int size = (request.getSize() != null && request.getSize() >= 1) ? request.getSize() : 20;
        var filters = new ArrayList<ViewFilter>();
        if (request.getFilters() != null) {
            filters.addAll(request.getFilters());
        }
        applyCollectionsFilterIfRequired(request.getView(), filters);
        try {
            List<Map<String, Set<ValueDto>>> rows = viewStoreReader.retrieveRows(
                    request.getView(), filters, (page - 1) * size, size + 1, request.includeJoinedViews());
            var pageBuilder = ViewPageDto.builder()
                    .rows(rows.subList(0, min(size, rows.size())))
                    .hasNext(rows.size() > size);
            if (request.includeCounts()) {
                long count = viewStoreReader.countRows(request.getView(), filters);
                pageBuilder = pageBuilder.totalCount(count).totalPages(count / size + ((count % size > 0) ? 1 : 0));
            }
            return pageBuilder.build();
        } catch (SQLTimeoutException e) {
            return ViewPageDto.builder()
                    .rows(Collections.emptyList())
                    .timeout(true)
                    .build();
        }
    }

    @SneakyThrows
    public CountDto count(CountRequest request) {
        var filters = request.getFilters();
        if (filters == null) {
            filters = new ArrayList<>();
        }
        applyCollectionsFilterIfRequired(request.getView(), filters);
        try {
            return new CountDto(viewStoreReader.countRows(request.getView(), filters), false);
        } catch (SQLTimeoutException e) {
            return new CountDto(0, true);
        }
    }
}
