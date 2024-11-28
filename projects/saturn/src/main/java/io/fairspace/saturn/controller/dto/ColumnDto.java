package io.fairspace.saturn.controller.dto;

import io.fairspace.saturn.config.properties.ViewsProperties;

public record ColumnDto(String name, String title, ViewsProperties.ColumnType type, Integer displayIndex) {}
