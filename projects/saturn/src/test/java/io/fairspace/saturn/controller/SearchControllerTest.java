package io.fairspace.saturn.controller;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import io.fairspace.saturn.controller.dto.SearchResultDto;
import io.fairspace.saturn.controller.dto.SearchResultsDto;
import io.fairspace.saturn.controller.dto.request.FileSearchRequest;
import io.fairspace.saturn.controller.dto.request.LookupSearchRequest;
import io.fairspace.saturn.services.search.FileSearchService;
import io.fairspace.saturn.services.search.SearchService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SearchController.class)
class SearchControllerTest extends BaseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SearchService searchService;

    @MockitoBean
    private FileSearchService fileSearchService;

    @Test
    void testSearchFiles() throws Exception {
        var mockResults = List.of(
                SearchResultDto.builder()
                        .id("file1.txt")
                        .label("File 1")
                        .type("text")
                        .comment("First file")
                        .build(),
                SearchResultDto.builder()
                        .id("file2.txt")
                        .label("File 2")
                        .type("text")
                        .comment("Second file")
                        .build());
        when(fileSearchService.searchFiles(any(FileSearchRequest.class))).thenReturn(mockResults);

        mockMvc.perform(
                        post("/search/files")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                    "query": "test query",
                                    "parentIRI": "parent/iri"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(
                        content()
                                .json(
                                        """
                    {
                        "results": [
                            {"id": "file1.txt", "label": "File 1", "type": "text", "comment": "First file"},
                            {"id": "file2.txt", "label": "File 2", "type": "text", "comment": "Second file"}
                        ],
                        "query": "test query"
                    }
                    """));
    }

    @Test
    void testLookupSearch() throws Exception {
        var mockResults = List.of(
                SearchResultDto.builder()
                        .id("file1.txt")
                        .label("File 1")
                        .type("text")
                        .comment("First file")
                        .build(),
                SearchResultDto.builder()
                        .id("file2.txt")
                        .label("File 2")
                        .type("text")
                        .comment("Second file")
                        .build());
        var resultsDTO = SearchResultsDto.builder()
                .results(mockResults)
                .query("test query")
                .build();
        when(searchService.getLookupSearchResults(any(LookupSearchRequest.class)))
                .thenReturn(resultsDTO);

        mockMvc.perform(
                        post("/search/lookup")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                {
                                    "query": "lookup query",
                                    "resourceType": "resourceType"
                                }
                                """))
                .andExpect(status().isOk()) // Expect 200 OK
                .andExpect(
                        content()
                                .json(
                                        """
                    {
                        "results": [
                            {"id": "file1.txt", "label": "File 1", "type": "text", "comment": "First file"},
                            {"id": "file2.txt", "label": "File 2", "type": "text", "comment": "Second file"}
                        ],
                        "query": "test query"
                    }
                    """)); // Verify JSON response
    }
}
