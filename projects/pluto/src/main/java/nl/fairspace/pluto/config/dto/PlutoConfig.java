package nl.fairspace.pluto.config.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ConfigurationProperties(prefix = "pluto")
@Configuration
@Data
public class PlutoConfig {
    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class MetadataSource {
        @NotBlank private String name;
        @NotBlank private String label;
        @NotBlank private String url;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Storage {
        @NotBlank private String name;
        @NotBlank private String label;
        @NotBlank private String url;
        private String searchUrl;
        private String rootDirectoryIri;
    }

    private String sessionCookieName = "JSESSIONID";
    private boolean forceHttps = true;
    private List<String> domains = Collections.emptyList();
    private Map<String, Storage> storages = new LinkedHashMap<>();
    private Map<String, MetadataSource> metadataSources = new LinkedHashMap<>();
    private String downstreamServiceHealthUrl;
    private String staticHtmlLocation;
}

