package io.fairspace.saturn.services.views;

import java.sql.*;
import java.time.*;
import java.util.*;
import java.util.function.*;
import java.util.stream.*;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.*;
import org.apache.commons.lang3.tuple.*;

import io.fairspace.saturn.config.*;
import io.fairspace.saturn.config.ViewsConfig.*;
import io.fairspace.saturn.services.views.Table.*;
import org.apache.ibatis.session.SqlSessionFactory;

import static io.fairspace.saturn.services.views.Table.idColumn;
import static io.fairspace.saturn.services.views.Table.valueColumn;

@Slf4j
public class ViewStoreClient implements AutoCloseable {

    public static class ViewStoreConfiguration {
        final Map<String, View> viewConfig;
        final Map<String, Table> viewTables = new HashMap<>();
        final Map<String, Map<String, Table>> propertyTables = new HashMap<>();
        final Map<String, Map<String, Table>> joinTables = new HashMap<>();

        ViewStoreConfiguration(ViewsConfig viewsConfig) {
            viewConfig = viewsConfig.views.stream().collect(Collectors.toMap(view -> view.name, Function.identity()));
        }
    }

    @SneakyThrows
    public static void setQueryValue(PreparedStatement query, int index, Object value) {
        if (value == null) {
            query.setNull(index, Types.NULL);
        } else if (value instanceof Boolean) {
            query.setBoolean(index, (Boolean) value);
        } else if (value instanceof Number) {
            query.setFloat(index, ((Number) value).floatValue());
        } else if (value instanceof Instant) {
            query.setTimestamp(index, Timestamp.from((Instant) value));
        } else if (value instanceof LocalDate) {
            query.setTimestamp(index, Timestamp.valueOf(((LocalDate) value).atStartOfDay()));
        } else if (value instanceof Collection) {
            throw new IllegalArgumentException("Unexpected value of collection type.");
        } else {
            query.setString(index, value.toString());
        }
    }

    private final Connection connection;

    @Getter
    private final ViewStoreConfiguration configuration;

    public ViewStoreClient(Connection connection, ViewStoreConfiguration configuration) {
        this.connection = connection;
        this.configuration = configuration;
    }

    @Override
    public void close() throws SQLException {
        connection.close();
    }

    public void commit() throws SQLException {
        this.connection.commit();
    }

    public void deleteRow(String view, String uri) throws SQLException {
        var viewTable = configuration.viewTables.get(view);
        try (var query = connection.prepareStatement("delete from " + viewTable.name + " where id = ?")) {
            query.setString(1, uri);
            var deletedCount = query.executeUpdate();
            log.debug("Deleted {} rows from view {}", deletedCount, view);
        }
    }

    Set<String> retrieveValues(String joinTable, String view, String id, ColumnDefinition column) throws SQLException {
        try (var query = connection.prepareStatement(
                "select " + column.name + " from " + joinTable + " where " + idColumn(view).name + " = ?")) {
            query.setString(1, id);
            var result = query.executeQuery();
            var links = new HashSet<String>();
            while (result.next()) {
                links.add(result.getString(1));
            }
            return links;
        }
    }

    int deleteValues(
            Table table, ColumnDefinition idColumn, String id, ColumnDefinition valueColumn, Collection<String> values)
            throws SQLException {
        var deleteSql =
                "delete from " + table.name + " where " + idColumn.name + " = ? " + " and " + valueColumn.name + " = ?";
        try (var delete = connection.prepareStatement(deleteSql)) {
            for (String value : values) {
                delete.setString(1, id);
                delete.setString(2, value);
                delete.addBatch();
            }
            return Arrays.stream(delete.executeBatch()).sum();
        }
    }

    int insertValues(
            Table table,
            ColumnDefinition idColumn,
            ColumnDefinition valueColumn,
            Collection<Pair<String, String>> values)
            throws SQLException {
        var insertSql =
                "insert into " + table.name + " ( " + idColumn.name + ", " + valueColumn.name + " ) values ( ?, ? )";
        try (var insert = connection.prepareStatement(insertSql)) {
            for (var value : values) {
                insert.setString(1, value.getKey());
                insert.setString(2, value.getValue());
                insert.addBatch();
            }
            return Arrays.stream(insert.executeBatch()).sum();
        }
    }

    public void updateValues(String view, String id, String property, Set<String> values) throws SQLException {
        var propertyTable = configuration.propertyTables.get(view).get(property);
        var valueColumn = valueColumn(property, ColumnType.Text);
        var existing = retrieveValues(propertyTable.name, view, id, valueColumn);

        var deleteCount = deleteValues(
                propertyTable,
                idColumn(view),
                id,
                valueColumn,
                existing.stream().filter(value -> !values.contains(value)).collect(Collectors.toList()));

        var insertCount = insertValues(
                propertyTable,
                idColumn(view),
                valueColumn,
                values.stream()
                        .filter(value -> !existing.contains(value))
                        .map(value -> Pair.of(id, value))
                        .collect(Collectors.toList()));

        log.debug("Deleted {}, inserted {} values for {}_{}", deleteCount, insertCount, view, property);
    }

    public void updateLinks(String view, String id, String joinView, Set<String> links) throws SQLException {
        var joinTable = configuration.joinTables.get(view).get(joinView);
        var existing = retrieveValues(joinTable.name, view, id, idColumn(joinView));

        var deleteCount = deleteValues(
                joinTable,
                idColumn(view),
                id,
                idColumn(joinView),
                existing.stream().filter(link -> !links.contains(link)).collect(Collectors.toList()));

        var insertCount = insertValues(
                joinTable,
                idColumn(view),
                idColumn(joinView),
                links.stream()
                        .filter(link -> !existing.contains(link))
                        .map(link -> Pair.of(id, link))
                        .collect(Collectors.toList()));

        log.debug("Deleted {}, inserted {} links for {} - {}", deleteCount, insertCount, view, joinView);
    }

    boolean rowExists(String table, String id) throws SQLException {
        try (var query = connection.prepareStatement("select exists ( select 1 from " + table + " where id = ? )")) {
            query.setString(1, id);
            var result = query.executeQuery();
            result.next();
            return result.getBoolean(1);
        }
    }

    public void addLabel(String id, String type, String label) throws SQLException {
        if (!rowExists("label", id)) {
            try (var insert = connection.prepareStatement("insert into label (id, type, label) values (?, ?, ?)")) {
                insert.setString(1, id);
                insert.setString(2, type);
                insert.setString(3, label);
                insert.executeUpdate();
                log.debug("Inserted label '{}' for {} (type {})", label, id, type);
            }
        }
    }

    public int updateRows(String view, List<Map<String, Object>> rows, boolean bulkInsert) throws SQLException {
        var viewTable = configuration.viewTables.get(view);
        var config = configuration.viewConfig.get(view);
        // Find the columns in the rows of type different from Set
        var columnNames = rows.stream()
                .flatMap(row -> row.keySet().stream())
                .distinct()
                .filter(columnName -> config.columns.stream()
                        .noneMatch(column -> column.name.equalsIgnoreCase(columnName) && column.type.isSet()))
                .toList();
        if (columnNames.isEmpty()) {
            return 0;
        }
        var updateSql = "update " + viewTable.name + " set "
                + columnNames.stream().map(column -> column + " = ?").collect(Collectors.joining(", "))
                + " where id = ?";
        var insertSql = "insert into " + viewTable.name + " ( " + String.join(", ", columnNames)
                + " ) values ( " + columnNames.stream().map(column -> "?").collect(Collectors.joining(", "))
                + " )";
        try (var insert = connection.prepareStatement(insertSql);
                var update = connection.prepareStatement(updateSql)) {
            for (var row : rows) {
                var values = columnNames.stream()
                        .map(columnName -> row.getOrDefault(columnName, null))
                        .toList();
                var id = (String) row.get("id");
                var exists = (!bulkInsert) && rowExists(viewTable.name, id);
                if (exists) {
                    for (var i = 0; i < values.size(); i++) {
                        var value = values.get(i);
                        setQueryValue(update, i + 1, value);
                    }
                    update.setString(values.size() + 1, id);
                    update.addBatch();
                } else {
                    for (var i = 0; i < values.size(); i++) {
                        var value = values.get(i);
                        setQueryValue(insert, i + 1, value);
                    }
                    insert.addBatch();
                }
            }
            var insertCount = Arrays.stream(insert.executeBatch()).sum();
            if (insertCount > 0) {
                log.debug("Inserted {} rows into view {}", insertCount, view);
            }
            var updateCount = Arrays.stream(update.executeBatch()).sum();
            if (updateCount > 0) {
                log.debug("Updated {} rows of view {}", updateCount, view);
            }
            return insertCount + updateCount;
        }
    }

    public void truncateViewTables(String view) throws SQLException {
        var tables = new ArrayList<Table>();
        tables.add(configuration.viewTables.get(view));
        tables.addAll(configuration
                .propertyTables
                .getOrDefault(view, Collections.emptyMap())
                .values());
        var joins = configuration.viewConfig.get(view).join;
        if (joins != null) {
            joins.stream()
                    .filter(joinView -> !joinView.reverse)
                    .forEach(joinView ->
                            tables.add(configuration.joinTables.get(view).get(joinView.view)));
        }
        log.debug(
                "Truncating tables for view {}: {}",
                view,
                tables.stream().map(Table::getName).collect(Collectors.toList()));
        for (var table : tables) {
            var query = "truncate table " + table.name;
            try (var statement = connection.prepareStatement(query)) {
                statement.executeUpdate();
            }
        }
    }
}
