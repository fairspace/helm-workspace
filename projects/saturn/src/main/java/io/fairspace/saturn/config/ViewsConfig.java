package io.fairspace.saturn.config;

import java.io.IOException;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.ResourceUtils;

import io.fairspace.saturn.config.properties.ViewsProperties;
import io.fairspace.saturn.services.views.ViewStoreClient;

@Slf4j
@Configuration
public class ViewsConfig {

    @Bean
    public ViewsProperties viewsProperties(@Qualifier("yamlObjectMapper") ObjectMapper yamlObjectMapper) {
        var viewsProperties = loadViewsConfig(yamlObjectMapper);
        viewsProperties.init();
        return viewsProperties;
    }

    @Bean
    ViewStoreClient.ViewStoreConfiguration viewStoreConfiguration(ViewsProperties viewsProperties) {
        return new ViewStoreClient.ViewStoreConfiguration(viewsProperties);
    }

    private static ViewsProperties loadViewsConfig(@Qualifier("yamlObjectMapper") ObjectMapper objectMapper) {
        try {
            var settingsFile = ResourceUtils.getFile("views.yaml");
            if (settingsFile.exists()) {
                return objectMapper.readValue(settingsFile, ViewsProperties.class);
            }
        } catch (IOException e) {
            log.error("Error loading search configuration", e);
            throw new RuntimeException("Error loading search configuration", e);
        }
        return new ViewsProperties();
    }
}
