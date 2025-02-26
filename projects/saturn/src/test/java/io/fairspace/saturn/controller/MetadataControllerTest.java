package io.fairspace.saturn.controller;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.fairspace.saturn.services.metadata.MetadataService;

import static io.fairspace.saturn.controller.enums.CustomMediaType.TEXT_TURTLE;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(MetadataController.class)
public class MetadataControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MetadataService metadataService;

    @Test
    public void testGetMetadata() throws Exception {
        Model mockModel = ModelFactory.createDefaultModel(); // Create an empty Jena model for testing
        mockModel.add(
                mockModel.createResource("http://example.com"),
                mockModel.createProperty("http://example.com/property"),
                "test-value");
        Mockito.when(metadataService.get(eq("http://example.com"), eq(false))).thenReturn(mockModel);

        mockMvc.perform(get("/metadata/")
                        .param("subject", "http://example.com")
                        .param("withValueProperties", "false")
                        .header("Accept", TEXT_TURTLE))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("http://example.com")))
                .andExpect(header().string("Content-Type", TEXT_TURTLE + ";charset=UTF-8"));
    }

    @Test
    public void testPutMetadata() throws Exception {
        String body =
                """
                @prefix ex: <http://example.com/> .
                ex:subject ex:property "value" .
                """;

        mockMvc.perform(put("/metadata/").content(body).contentType(TEXT_TURTLE).param("doViewsUpdate", "true"))
                .andExpect(status().isNoContent());

        Mockito.verify(metadataService).put(any(Model.class), eq(true));
    }

    @Test
    public void testPatchMetadata() throws Exception {
        String body =
                """
                @prefix ex: <http://example.com/> .
                ex:subject ex:property "updated-value" .
                """;

        mockMvc.perform(patch("/metadata/")
                        .content(body)
                        .contentType(TEXT_TURTLE)
                        .param("doViewsUpdate", "false"))
                .andExpect(status().isNoContent());

        Mockito.verify(metadataService).patch(any(Model.class), eq(false));
    }

    @Test
    public void testDeleteMetadataBySubject() throws Exception {
        Mockito.when(metadataService.softDelete(any())).thenReturn(true);

        mockMvc.perform(delete("/metadata/").param("subject", "http://example.com"))
                .andExpect(status().isNoContent());

        Mockito.verify(metadataService).softDelete(any());
    }

    @Test
    public void testDeleteMetadataByModel() throws Exception {
        String body =
                """
                @prefix ex: <http://example.com/> .
                ex:subject ex:property "value" .
                """;

        mockMvc.perform(delete("/metadata/")
                        .content(body)
                        .contentType(TEXT_TURTLE)
                        .param("doViewsUpdate", "true"))
                .andExpect(status().isNoContent());

        Mockito.verify(metadataService).delete(any(Model.class), eq(true));
    }

    @Test
    public void testDeleteMetadataSubjectNotFound() throws Exception {
        Mockito.when(metadataService.softDelete(any())).thenReturn(false);

        mockMvc.perform(delete("/metadata/").param("subject", "http://example.com"))
                .andExpect(status().isBadRequest());
    }
}
