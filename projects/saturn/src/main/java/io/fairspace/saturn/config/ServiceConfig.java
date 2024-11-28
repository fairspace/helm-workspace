package io.fairspace.saturn.config;

import org.apache.jena.query.Dataset;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import io.fairspace.saturn.config.properties.ViewsProperties;
import io.fairspace.saturn.rdf.transactions.Transactions;
import io.fairspace.saturn.services.search.FileSearchService;
import io.fairspace.saturn.services.search.JdbcFileSearchService;
import io.fairspace.saturn.services.search.SparqlFileSearchService;
import io.fairspace.saturn.services.views.JdbcQueryService;
import io.fairspace.saturn.services.views.QueryService;
import io.fairspace.saturn.services.views.SparqlQueryService;
import io.fairspace.saturn.services.views.ViewStoreClientFactory;
import io.fairspace.saturn.services.views.ViewStoreReader;
import io.fairspace.saturn.webdav.DavFactory;

import static io.fairspace.saturn.services.views.ViewStoreClientFactory.protectedResources;

@Configuration
public class ServiceConfig {

    @Bean
    public QueryService queryService(
            SparqlQueryService sparqlQueryService,
            @Nullable ViewStoreClientFactory viewStoreClientFactory,
            Transactions transactions,
            @Qualifier("davFactory") DavFactory davFactory,
            ViewStoreReader viewStoreReader) {
        return viewStoreClientFactory == null
                ? sparqlQueryService
                : new JdbcQueryService(transactions, davFactory.root, viewStoreReader);
    }

    @Bean
    public FileSearchService fileSearchService(
            @Qualifier("filteredDataset") Dataset filteredDataset,
            @Nullable ViewStoreClientFactory viewStoreClientFactory,
            ViewsProperties viewsProperties,
            Transactions transactions,
            @Qualifier("davFactory") DavFactory davFactory,
            ViewStoreReader viewStoreReader) {
        // File search should be done using JDBC for performance reasons. However, if the view store is not available,
        // or collections and files view is not configured, we fall back to using SPARQL queries on the RDF database
        // directly.
        boolean useSparqlFileSearchService = viewStoreClientFactory == null
                || viewsProperties.views.stream().noneMatch(view -> protectedResources.containsAll(view.types));

        return useSparqlFileSearchService
                ? new SparqlFileSearchService(filteredDataset)
                : new JdbcFileSearchService(transactions, davFactory.root, viewStoreReader);
    }
}
