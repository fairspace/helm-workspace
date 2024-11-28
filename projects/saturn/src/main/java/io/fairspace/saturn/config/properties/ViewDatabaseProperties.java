package io.fairspace.saturn.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "application.view-database")
public class ViewDatabaseProperties {

    private boolean enabled;
    private String url;
    private String username;
    private String password;
    private boolean autoCommitEnabled;
    private int maxPoolSize;
    private int connectionTimeout;
    private boolean mvRefreshOnStartRequired;
}
