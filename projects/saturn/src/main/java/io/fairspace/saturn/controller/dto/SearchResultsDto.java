package io.fairspace.saturn.controller.dto;

import java.util.List;

import lombok.Builder;

@Builder
public record SearchResultsDto(List<SearchResultDto> results, String query) {}
