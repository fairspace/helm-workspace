package io.fairspace.saturn.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "application.search")
public class SearchProperties {

    private int pageRequestTimeout;

    private int countRequestTimeout;

    /**
     * maxJoinItems is used to limit number of joined entries (from the join view) to decrease the response size
     */
    private int maxJoinItems;
}
