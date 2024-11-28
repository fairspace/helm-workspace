package io.fairspace.saturn.config.properties;

import java.io.File;

import com.google.common.annotations.VisibleForTesting;
import lombok.Data;
import org.apache.jena.tdb2.params.StoreParams;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "application.jena")
public class JenaProperties {

    // TODO: this is a terrible idea to make it static, it's a quick fix for SparqlUtils & RequestContext
    private static String metadataBaseIRI;

    private File datasetPath;

    private File transactionLogPath;

    private boolean bulkTransactions;

    private long sparqlQueryTimeout;

    private final StoreParams storeParams;

    public static String getMetadataBaseIri() {
        return JenaProperties.metadataBaseIRI;
    }

    @VisibleForTesting
    public static void setMetadataBaseIRI(String metadataBaseIRI) {
        JenaProperties.metadataBaseIRI = metadataBaseIRI;
    }

    // Not a common practice, but a way to make the value available in a static context
    @Autowired
    public JenaProperties(
            @Value("${application.jena.metadataBaseIRI}") String metadataBaseIRI,
            StoreParamsProperties storeParamsProperties) {
        JenaProperties.metadataBaseIRI = metadataBaseIRI;
        this.storeParams = storeParamsProperties.buildStoreParams();
    }
}
