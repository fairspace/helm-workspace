package io.fairspace.saturn.services.search;

import java.util.List;

import io.fairspace.saturn.controller.dto.SearchResultDto;
import io.fairspace.saturn.controller.dto.request.FileSearchRequest;

public interface FileSearchService {
    List<SearchResultDto> searchFiles(FileSearchRequest request);
}
