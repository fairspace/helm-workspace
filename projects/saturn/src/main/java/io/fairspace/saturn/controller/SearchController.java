package io.fairspace.saturn.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.fairspace.saturn.controller.dto.SearchResultsDto;
import io.fairspace.saturn.controller.dto.request.FileSearchRequest;
import io.fairspace.saturn.controller.dto.request.LookupSearchRequest;
import io.fairspace.saturn.services.search.FileSearchService;
import io.fairspace.saturn.services.search.SearchService;

@RestController
@RequestMapping("/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    private final FileSearchService fileSearchService;

    @PostMapping(value = "/files")
    public ResponseEntity<SearchResultsDto> searchFiles(@RequestBody FileSearchRequest request) {
        var searchResult = fileSearchService.searchFiles(request);
        var resultDto = SearchResultsDto.builder()
                .results(searchResult)
                .query(request.getQuery())
                .build();
        return ResponseEntity.ok(resultDto);
    }

    @PostMapping(value = "/lookup")
    public ResponseEntity<SearchResultsDto> lookupSearch(@RequestBody LookupSearchRequest request) {
        var results = searchService.getLookupSearchResults(request);
        return ResponseEntity.ok(results);
    }
}
