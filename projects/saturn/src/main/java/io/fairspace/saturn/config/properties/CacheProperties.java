package io.fairspace.saturn.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "application.cache")
public class CacheProperties {

    private Cache facets = new Cache();

    private Cache views = new Cache();

    @Data
    public static class Cache {

        private String name;

        private boolean autoRefreshEnabled;

        private Long refreshFrequencyInHours;
    }
}
