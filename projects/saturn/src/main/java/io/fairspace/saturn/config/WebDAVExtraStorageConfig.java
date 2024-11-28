package io.fairspace.saturn.config;

import java.io.File;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.fairspace.saturn.config.condition.ConditionalOnMultiValuedProperty;
import io.fairspace.saturn.config.properties.WebDavProperties;
import io.fairspace.saturn.rdf.transactions.Transactions;
import io.fairspace.saturn.services.users.UserService;
import io.fairspace.saturn.webdav.DavFactory;
import io.fairspace.saturn.webdav.WebDAVServlet;
import io.fairspace.saturn.webdav.blobstore.BlobStore;
import io.fairspace.saturn.webdav.blobstore.DeletableLocalBlobStore;

@Configuration
@ConditionalOnMultiValuedProperty(prefix = "application", name = "features", havingValue = "ExtraStorage")
public class WebDAVExtraStorageConfig {

    public static final String WEB_DAV_EXTRA_URL_PATH = "/api/extra-storage";

    @Value("${application.publicUrl}")
    private String publicUrl;

    @Bean
    public ServletRegistrationBean<WebDAVServlet> extraWebDavServletRegistrationBean(
            @Qualifier("extraDavServlet") WebDAVServlet extraDavServlet) {
        return new ServletRegistrationBean<>(extraDavServlet, "/extra-storage/*");
    }

    @Bean
    public BlobStore extraBlobStore(WebDavProperties webDavProperties) {
        return new DeletableLocalBlobStore(
                new File(webDavProperties.getExtraStorage().getBlobStorePath()));
    }

    @Bean
    public DavFactory extraDavFactory(
            @Qualifier("dataset") Dataset dataset,
            @Qualifier("extraBlobStore") BlobStore extraBlobStore,
            UserService userService,
            Transactions transactions,
            WebDavProperties webDavProperties,
            @Qualifier("userVocabulary") Model userVocabulary,
            @Qualifier("vocabulary") Model vocabulary) {
        var extraDavFactory = new DavFactory(
                dataset.getDefaultModel().createResource(publicUrl + WEB_DAV_EXTRA_URL_PATH),
                extraBlobStore,
                userService,
                dataset.getContext(),
                webDavProperties,
                userVocabulary,
                vocabulary);
        initExtraStorageRootDirectories(webDavProperties.getExtraStorage(), extraDavFactory, transactions);
        return extraDavFactory;
    }

    @Bean
    public WebDAVServlet extraDavServlet(
            @Qualifier("extraDavFactory") DavFactory davFactory,
            Transactions transactions,
            @Qualifier("extraBlobStore") BlobStore blobStore) {
        return new WebDAVServlet(davFactory, transactions, blobStore);
    }

    private void initExtraStorageRootDirectories(
            WebDavProperties.ExtraStorage extraStorage, DavFactory extraDavFactory, Transactions transactions) {
        transactions.calculateWrite(ds2 -> {
            for (var rc : extraStorage.getDefaultRootCollections()) {
                try {
                    extraDavFactory.root.createCollection(rc);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            return null;
        });
    }
}
