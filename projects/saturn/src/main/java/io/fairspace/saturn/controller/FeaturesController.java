package io.fairspace.saturn.controller;

import java.util.Set;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.fairspace.saturn.config.enums.Feature;
import io.fairspace.saturn.config.properties.FeatureProperties;

@RestController
@RequestMapping("/features")
@RequiredArgsConstructor
public class FeaturesController {

    private final FeatureProperties featureProperties;

    @GetMapping("/")
    public ResponseEntity<Set<Feature>> getFeatures() {
        return ResponseEntity.ok(featureProperties.getFeatures());
    }
}
