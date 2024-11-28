package io.fairspace.saturn.config;

import org.apache.jena.query.Dataset;
import org.apache.jena.sparql.core.DatasetImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.Nullable;

import io.fairspace.saturn.config.properties.JenaProperties;
import io.fairspace.saturn.config.properties.ViewsProperties;
import io.fairspace.saturn.rdf.SaturnDatasetFactory;
import io.fairspace.saturn.rdf.search.FilteredDatasetGraph;
import io.fairspace.saturn.services.metadata.MetadataPermissions;
import io.fairspace.saturn.services.views.ViewStoreClientFactory;

@Configuration
public class RdfStorageConfig {

    @Value("${application.publicUrl}")
    private String publicUrl;

    @Bean
    public Dataset dataset(
            ViewsProperties viewsProperties,
            JenaProperties jenaProperties,
            @Nullable ViewStoreClientFactory viewStoreClientFactory) {
        return SaturnDatasetFactory.connect(viewsProperties, jenaProperties, viewStoreClientFactory, publicUrl);
    }

    @Bean
    public Dataset filteredDataset(Dataset dataset, MetadataPermissions metadataPermissions) {
        var filteredDatasetGraph = new FilteredDatasetGraph(dataset.asDatasetGraph(), metadataPermissions);
        return DatasetImpl.wrap(filteredDatasetGraph);
    }
}
