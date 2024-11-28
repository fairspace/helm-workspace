package io.fairspace.saturn.controller.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

public record ViewDto(
        String name,
        String title,
        List<ColumnDto> columns,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long maxDisplayCount) {}
