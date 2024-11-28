package io.fairspace.saturn.controller;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import io.fairspace.saturn.config.properties.ViewsProperties;
import io.fairspace.saturn.controller.dto.CountDto;
import io.fairspace.saturn.controller.dto.FacetDto;
import io.fairspace.saturn.controller.dto.ValueDto;
import io.fairspace.saturn.controller.dto.ViewDto;
import io.fairspace.saturn.controller.dto.ViewPageDto;
import io.fairspace.saturn.controller.dto.request.CountRequest;
import io.fairspace.saturn.controller.dto.request.ViewRequest;
import io.fairspace.saturn.services.views.QueryService;
import io.fairspace.saturn.services.views.ViewService;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ViewController.class)
public class ViewControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ViewService viewService;

    @MockBean(name = "queryService")
    private QueryService queryService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testGetViewsSuccess() throws Exception {
        var viewDto = new ViewDto("view1", "View 1", List.of(), 100L);

        when(viewService.getViews()).thenReturn(List.of(viewDto));

        mockMvc.perform(get("/views/").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.views", hasSize(1)))
                .andExpect(jsonPath("$.views[0].name", is("view1")))
                .andExpect(jsonPath("$.views[0].title", is("View 1")))
                .andExpect(jsonPath("$.views[0].columns", hasSize(0)))
                .andExpect(jsonPath("$.views[0].maxDisplayCount", is(100))); // Max display count is null
    }

    @Test
    public void testGetViewDataSuccess() throws Exception {
        // Mock request body and response
        var viewRequest = new ViewRequest();
        viewRequest.setView("view1");
        viewRequest.setPage(1);
        viewRequest.setSize(10);

        var row = Map.of("view1_column1", Set.of(new ValueDto("label1", "value1")));
        var viewPageDto = ViewPageDto.builder()
                .rows(List.of(row))
                .hasNext(false)
                .timeout(false)
                .totalCount(100L)
                .totalPages(10L)
                .build();

        when(queryService.retrieveViewPage(viewRequest)).thenReturn(viewPageDto);

        mockMvc.perform(post("/views/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(viewRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", hasSize(1)))
                .andExpect(jsonPath("$.rows[0]['view1_column1']", hasSize(1)))
                .andExpect(jsonPath("$.rows[0]['view1_column1'][0].value", is("value1")))
                .andExpect(jsonPath("$.hasNext", is(false)))
                .andExpect(jsonPath("$.timeout", is(false)))
                .andExpect(jsonPath("$.totalCount", is(100)))
                .andExpect(jsonPath("$.totalPages", is(10)));
    }

    @Test
    public void testGetFacetsSuccess() throws Exception {
        // Mock data for getFacets
        var facetDto = new FacetDto("facet1", "Facet 1", ViewsProperties.ColumnType.Set, List.of(), null, null, null);

        when(viewService.getFacets()).thenReturn(List.of(facetDto));

        mockMvc.perform(get("/views/facets").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.facets", hasSize(1)))
                .andExpect(jsonPath("$.facets[0].name", is("facet1")))
                .andExpect(jsonPath("$.facets[0].title", is("Facet 1")))
                .andExpect(jsonPath(
                        "$.facets[0].type", is(ViewsProperties.ColumnType.Set.getName()))); // Empty options list
    }

    @Test
    public void testCountSuccess() throws Exception {
        // Mock request body and response
        var countRequest = new CountRequest();
        countRequest.setView("view1");
        countRequest.setFilters(List.of());

        var countDto = new CountDto(100, false);

        when(queryService.count(countRequest)).thenReturn(countDto);

        mockMvc.perform(post("/views/count")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(countRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count", is(100)))
                .andExpect(jsonPath("$.timeout", is(false)));
    }

    @Test
    public void testGetViewDataValidationFailure() throws Exception {
        // Test validation error (e.g., invalid request body)
        var invalidRequestBody = new ViewRequest();
        invalidRequestBody.setPage(0); // Invalid page (must be >= 1)
        invalidRequestBody.setSize(0); // Invalid size (must be >= 1)

        mockMvc.perform(post("/views/")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequestBody)))
                .andExpect(status().isBadRequest());
    }
}
