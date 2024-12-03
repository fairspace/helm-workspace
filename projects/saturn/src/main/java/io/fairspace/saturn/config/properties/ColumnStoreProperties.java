package io.fairspace.saturn.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "application.view-column-store")
public class ColumnStoreProperties {
    private boolean enabled;
    private String url;
    private String username;
    private String password;
    private boolean autoCommitEnabled;
    private int maxPoolSize;
    private int connectionTimeout;
}
