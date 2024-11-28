package io.fairspace.saturn.services.views;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.*;

import io.fairspace.saturn.config.properties.ViewsProperties;

import static io.fairspace.saturn.config.ViewsConfig.*;

@Data
public class Table {
    @Data
    @Builder
    public static class ColumnDefinition {
        String name;
        ViewsProperties.ColumnType type;
    }

    public static ColumnDefinition idColumn() {
        return idColumn(null);
    }

    public static ColumnDefinition idColumn(String prefix) {
        return ColumnDefinition.builder()
                .name(prefix == null ? "id" : prefix.toLowerCase() + "_id")
                .type(ViewsProperties.ColumnType.Identifier)
                .build();
    }

    public static ColumnDefinition valueColumn(String name, ViewsProperties.ColumnType type) {
        return ColumnDefinition.builder().name(name.toLowerCase()).type(type).build();
    }

    String name;
    List<ColumnDefinition> columns;

    private final Map<String, ColumnDefinition> columnsById;

    public Table(String name, List<ColumnDefinition> columns) {
        this.name = name;
        this.columns = columns;
        this.columnsById = columns.stream()
                .collect(Collectors.toMap(colDef -> colDef.getName().toLowerCase(), Function.identity()));
    }

    public ColumnDefinition getColumn(String name) {
        return columnsById.get(name.toLowerCase());
    }
}
