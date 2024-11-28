package io.fairspace.saturn.config.properties;

import java.util.List;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "application.web-dav")
public class WebDavProperties {

    // Path of the WebDAV's local blob store
    private String blobStorePath;

    private ExtraStorage extraStorage;

    @Data
    public static class ExtraStorage {

        private String blobStorePath;

        private List<String> defaultRootCollections;
    }
}
