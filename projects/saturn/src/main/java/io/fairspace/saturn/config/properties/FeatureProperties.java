package io.fairspace.saturn.config.properties;

import java.util.Set;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import io.fairspace.saturn.config.enums.Feature;

@Data
@Component
@ConfigurationProperties(prefix = "application")
public class FeatureProperties {

    private Set<Feature> features;
}
