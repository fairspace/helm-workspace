package io.fairspace.saturn.controller.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.fairspace.saturn.config.properties.ViewsProperties;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

@JsonInclude(NON_NULL)
public record FacetDto(
        String name,
        String title,
        ViewsProperties.ColumnType type,
        List<ValueDto> values,
        Boolean booleanValue,
        Object min,
        Object max) {}
