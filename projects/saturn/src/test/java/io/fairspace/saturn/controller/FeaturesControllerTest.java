package io.fairspace.saturn.controller;

import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.fairspace.saturn.config.enums.Feature;
import io.fairspace.saturn.config.properties.FeatureProperties;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FeaturesController.class)
class FeaturesControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private FeatureProperties featureProperties;

    @Test
    void testGetFeatures() throws Exception {
        // Mock the response from featureProperties
        Set<Feature> features = Set.of(Feature.ExtraStorage);
        when(featureProperties.getFeatures()).thenReturn(features);

        // Perform GET request and verify the response
        mockMvc.perform(get("/features/").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[\"ExtraStorage\"]"));
    }
}
