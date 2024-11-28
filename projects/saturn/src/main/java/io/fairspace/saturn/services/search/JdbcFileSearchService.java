package io.fairspace.saturn.services.search;

import java.util.List;
import java.util.stream.Collectors;

import io.milton.resource.CollectionResource;
import lombok.SneakyThrows;
import lombok.extern.log4j.Log4j2;

import io.fairspace.saturn.controller.dto.SearchResultDto;
import io.fairspace.saturn.controller.dto.request.FileSearchRequest;
import io.fairspace.saturn.rdf.transactions.Transactions;
import io.fairspace.saturn.services.views.ViewStoreReader;

import static io.fairspace.saturn.webdav.PathUtils.getCollectionNameByUri;

@Log4j2
public class JdbcFileSearchService implements FileSearchService {

    private final Transactions transactions;
    private final CollectionResource rootSubject;
    private final ViewStoreReader viewStoreReader;

    public JdbcFileSearchService(
            Transactions transactions, CollectionResource rootSubject, ViewStoreReader viewStoreReader) {
        this.transactions = transactions;
        this.rootSubject = rootSubject;
        this.viewStoreReader = viewStoreReader;
    }

    @SneakyThrows
    public List<SearchResultDto> searchFiles(FileSearchRequest request) {
        var collectionsForUser = transactions.calculateRead(m -> rootSubject.getChildren().stream()
                .map(collection -> getCollectionNameByUri(rootSubject.getUniqueId(), collection.getUniqueId()))
                .collect(Collectors.toList()));

        return viewStoreReader.searchFiles(request, collectionsForUser);
    }
}
