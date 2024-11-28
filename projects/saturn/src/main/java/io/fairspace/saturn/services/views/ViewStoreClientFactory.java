package io.fairspace.saturn.services.views;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import io.fairspace.saturn.config.properties.ViewDatabaseProperties;
import io.fairspace.saturn.config.properties.ViewsProperties;
import io.fairspace.saturn.config.properties.ViewsProperties.ColumnType;
import io.fairspace.saturn.config.properties.ViewsProperties.View;
import io.fairspace.saturn.vocabulary.FS;

import static io.fairspace.saturn.services.views.Table.idColumn;
import static io.fairspace.saturn.services.views.Table.valueColumn;

@Slf4j
@Component
@ConditionalOnProperty(value = "application.view-database.enabled", havingValue = "true")
public class ViewStoreClientFactory {

    public static final Set<String> protectedResources = Set.of(FS.COLLECTION_URI, FS.DIRECTORY_URI, FS.FILE_URI);

    private final MaterializedViewService materializedViewService;

    private final ViewStoreClient.ViewStoreConfiguration configuration;

    public final DataSource dataSource;

    public ViewStoreClientFactory(
            ViewsProperties viewsProperties,
            ViewDatabaseProperties viewDatabaseProperties,
            MaterializedViewService materializedViewService,
            DataSource dataSource,
            ViewStoreClient.ViewStoreConfiguration configuration)
            throws SQLException {
        log.debug("Initializing the database connection");

        this.dataSource = dataSource;
        this.materializedViewService = materializedViewService;
        this.configuration = configuration;

        try (var connection = dataSource.getConnection()) {
            log.debug("Database connection: {}", connection.getMetaData().getDatabaseProductName());
        }

        createOrUpdateTable(new Table(
                "label",
                List.of(idColumn(), valueColumn("type", ColumnType.Text), valueColumn("label", ColumnType.Text))));

        // todo: configuration is initialized within the loop below, do the initialization in constructor
        for (View view : viewsProperties.views) {
            createOrUpdateView(view);
        }
        if (viewDatabaseProperties.isMvRefreshOnStartRequired()) {
            materializedViewService.createOrUpdateAllMaterializedViews();
        } else {
            log.warn("Skipping materialized view refresh on start");
        }
    }

    public ViewStoreClient build() throws SQLException {
        return new ViewStoreClient(getConnection(), configuration, materializedViewService);
    }

    public Connection getConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public String databaseTypeForColumnType(ColumnType type) {
        return switch (type) {
            case Text, Term -> "text";
            case Date -> "timestamp";
            case Number -> "numeric";
            case Boolean -> "boolean";
            case Identifier -> "text not null";
            case Set, TermSet -> throw new IllegalArgumentException("No database type for column type set.");
        };
    }

    Map<String, ColumnMetadata> getColumnMetadata(Connection connection, String table) throws SQLException {
        log.debug("Fetching metadata for {} ...", table);
        var resultSet = connection.getMetaData().getColumns(null, null, table, null);
        var result = new LinkedHashMap<String, ColumnMetadata>();
        while (resultSet.next()) {
            String columnName = resultSet.getString("COLUMN_NAME");
            String dataTypeName = resultSet.getString("TYPE_NAME");
            Boolean nullable = resultSet.getBoolean("NULLABLE");
            var metadata = ColumnMetadata.builder()
                    .type(dataTypeName)
                    .nullable(nullable)
                    .build();
            result.put(columnName, metadata);
        }
        return result;
    }

    void createOrUpdateTable(Table table) throws SQLException {
        try (var connection = getConnection()) {
            log.debug("Check if table {} exists ...", table.name);
            var resultSet = connection.getMetaData().getTables(null, null, table.name, null);
            var tableExists = resultSet.next();
            if (!tableExists) {
                createTable(table, connection);
            } else {
                updateTable(table, connection);
            }
        }
    }

    void createOrUpdateJoinTable(Table table) throws SQLException {
        try (var connection = getConnection()) {
            log.debug("Check if table {} exists ...", table.name);
            var resultSet = connection.getMetaData().getTables(null, null, table.name, null);
            var tableExists = resultSet.next();

            if (!tableExists) {
                createTable(table, connection);
                createIndexesIfNotExist(table, connection);
            } else {
                updateTable(table, connection);
                createIndexesIfNotExist(table, connection);
            }
        }
    }

    private void updateTable(Table table, Connection connection) throws SQLException {
        var columnMetadata = getColumnMetadata(connection, table.name);
        var newColumns = table.columns.stream()
                .filter(column -> !column.type.isSet() && !columnMetadata.containsKey(column.name))
                .collect(Collectors.toList());
        try {
            log.debug("New columns: {}", new ObjectMapper().writeValueAsString(newColumns));
        } catch (JsonProcessingException e) {
            var message = "Error during mapping of view columns";
            log.error(message, e);
            throw new IllegalStateException(message, e);
        }
        // Update existing table
        if (!newColumns.isEmpty()) {
            connection.setAutoCommit(true);
            var command = newColumns.stream()
                    .map(column -> String.format(
                            "alter table %s add column %s %s",
                            table.name, column.name, databaseTypeForColumnType(column.type)))
                    .collect(Collectors.joining("; "));
            log.debug(command);
            connection.createStatement().execute(command);
            connection.setAutoCommit(false);
            log.info("Table {} updated.", table.name);
        }
    }

    private void createTable(Table table, Connection connection) throws SQLException {
        // Create new table
        connection.setAutoCommit(true);
        var columnSpecification = table.columns.stream()
                .filter(column -> !column.type.isSet())
                .map(column -> String.format("%s %s", column.name, databaseTypeForColumnType(column.type)))
                .collect(Collectors.joining(", "));
        var keys = table.columns.stream()
                .filter(column -> column.type == ColumnType.Identifier)
                .map(column -> column.name)
                .collect(Collectors.joining(", "));
        var command =
                String.format("create table %s ( %s, primary key ( %s ) )", table.name, columnSpecification, keys);
        log.debug(command);
        connection.createStatement().execute(command);
        connection.setAutoCommit(false);
        log.info("Table {} created.", table.name);
    }

    private void createIndexesIfNotExist(Table table, Connection connection) throws SQLException {

        var keys = table.columns.stream()
                .filter(column -> column.type == ColumnType.Identifier)
                .map(column -> column.name)
                .toList();

        connection.setAutoCommit(true);

        for (var column : keys) {
            var indexName = String.format("%s_%s_idx", table.name, column);
            var command = String.format("CREATE INDEX IF NOT EXISTS %s ON %s (%s)", indexName, table.name, column);

            log.debug(command);
            connection.createStatement().execute(command);
            log.info("Index {} created.", indexName);
        }

        connection.setAutoCommit(false);
    }

    void validateViewConfig(ViewsProperties.View view) {
        if (view.columns.stream().anyMatch(column -> "id".equalsIgnoreCase(column.name))) {
            throw new IllegalArgumentException("Forbidden to override the built-in column 'id' of view " + view.name);
        }
        if (view.columns.stream().anyMatch(column -> "label".equalsIgnoreCase(column.name))) {
            throw new IllegalArgumentException(
                    "Forbidden to override the built-in column 'label' of view " + view.name);
        }
        if (view.name.equalsIgnoreCase("resource")
                && view.columns.stream().anyMatch(column -> "collection".equalsIgnoreCase(column.name))) {
            throw new IllegalArgumentException(
                    "Forbidden to override the built-in column 'collection' of view " + view.name);
        }
        if (!view.name.equalsIgnoreCase("resource") && view.types.stream().anyMatch(protectedResources::contains)) {
            throw new IllegalArgumentException("Forbidden built-in type specified for view " + view.name);
        }
    }

    void createOrUpdateView(ViewsProperties.View view) throws SQLException {
        // Add view table
        validateViewConfig(view);
        var columns = new ArrayList<Table.ColumnDefinition>();
        columns.add(idColumn());
        columns.add(valueColumn("label", ColumnType.Text));
        if (view.name.equalsIgnoreCase("resource")) {
            columns.add(valueColumn("collection", ColumnType.Text));
        }
        for (var column : view.columns) {
            if (column.type.isSet()) {
                continue;
            }
            columns.add(valueColumn(column.name, column.type));
        }
        var table = new Table(view.name.toLowerCase(), columns);
        createOrUpdateTable(table);
        configuration.viewTables.put(view.name, table);
        // Add property tables
        var setColumns =
                view.columns.stream().filter(column -> column.type.isSet()).toList();
        for (ViewsProperties.View.Column column : setColumns) {
            var propertyTableColumns = new ArrayList<Table.ColumnDefinition>();
            propertyTableColumns.add(idColumn(view.name));
            propertyTableColumns.add(valueColumn(column.name, ColumnType.Identifier));
            var name = String.format("%s_%s", view.name.toLowerCase(), column.name.toLowerCase());
            var propertyTable = new Table(name, propertyTableColumns);
            createOrUpdateTable(propertyTable);
            configuration.propertyTables.putIfAbsent(view.name, new HashMap<>());
            configuration.propertyTables.get(view.name).put(column.name, propertyTable);
        }
        if (view.join != null) {
            // Add join tables
            for (ViewsProperties.View.JoinView join : view.join) {
                var joinTable = getJoinTable(join, view);
                createOrUpdateJoinTable(joinTable);
                configuration.joinTables.putIfAbsent(view.name, new HashMap<>());
                configuration.joinTables.get(view.name).put(join.view, joinTable);
                var joinView = configuration.viewConfig.get(join.view);
            }
        }
    }

    public static Table getJoinTable(View.JoinView join, View view) {
        String left = join.reverse ? join.view : view.name;
        String right = join.reverse ? view.name : join.view;
        var name = String.format("%s_%s", left.toLowerCase(), right.toLowerCase());
        return new Table(name, Arrays.asList(idColumn(left), idColumn(right)));
    }

    @Data
    @Builder
    private static class ColumnMetadata {
        private String type;
        private Boolean nullable;
    }
}
