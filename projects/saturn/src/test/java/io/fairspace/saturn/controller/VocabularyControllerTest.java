package io.fairspace.saturn.controller;

import org.apache.jena.rdf.model.Model;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.apache.jena.riot.RDFDataMgr.loadModel;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(VocabularyController.class)
class VocabularyControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @TestConfiguration
    static class CustomVocabularyConfig {
        @Bean
        public Model vocabulary() {
            return loadModel("vocabulary.ttl");
        }
    }

    @Test
    void testGetVocabularyWithJsonLd() throws Exception {
        mockMvc.perform(get("/vocabulary/").header(HttpHeaders.ACCEPT, "application/ld+json"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/ld+json"));
    }
}
