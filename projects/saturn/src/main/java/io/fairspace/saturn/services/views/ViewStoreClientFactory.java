package io.fairspace.saturn.services.views;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.fairspace.saturn.mapper.ViewMapper;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;

import io.fairspace.saturn.config.Config;
import io.fairspace.saturn.config.ViewsConfig;
import io.fairspace.saturn.config.ViewsConfig.ColumnType;
import io.fairspace.saturn.vocabulary.FS;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.TransactionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;

import static io.fairspace.saturn.services.views.Table.idColumn;
import static io.fairspace.saturn.services.views.Table.valueColumn;

@Slf4j
public class ViewStoreClientFactory {

    public ViewStoreClient build() throws SQLException {
        return new ViewStoreClient(getPostgresConnection(), configuration);
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

    public static final Set<String> protectedResources = Set.of(FS.COLLECTION_URI, FS.DIRECTORY_URI, FS.FILE_URI);

    final ViewStoreClient.ViewStoreConfiguration configuration;
    public final DataSource dataSource;
    private final SqlSessionFactory sqlSessionFactory;

    public ViewStoreClientFactory(ViewsConfig viewsConfig, Config dbConfig, Config.Search search)
            throws SQLException {

        configuration = new ViewStoreClient.ViewStoreConfiguration(viewsConfig);

        dataSource = initPostgres(viewsConfig, dbConfig.viewDatabase, search);
        sqlSessionFactory = initClickhouse(dbConfig.viewColumnDatabase);

    }

    public Connection getPostgresConnection() throws SQLException {
        return dataSource.getConnection();
    }

    public SqlSessionFactory getSqlSessionFactory() {
        return sqlSessionFactory;
    }

    private HikariDataSource initPostgres(ViewsConfig viewsConfig, Config.ViewDatabase viewDatabase, Config.Search search) {
        log.debug("Initializing the Postgresql database connection");
        var databaseConfig = new HikariConfig();
        databaseConfig.setJdbcUrl(viewDatabase.url);
        databaseConfig.setUsername(viewDatabase.username);
        databaseConfig.setPassword(viewDatabase.password);
        databaseConfig.setAutoCommit(viewDatabase.autoCommit);
        databaseConfig.setConnectionTimeout(viewDatabase.connectionTimeout);
        databaseConfig.setMaximumPoolSize(viewDatabase.maxPoolSize);

        HikariDataSource postgres = new HikariDataSource(databaseConfig);

        try (var connection = postgres.getConnection()) {
            log.debug("Postgresql connection: {}", connection.getMetaData().getDatabaseProductName());
            createOrUpdateTable(
                new Table(
                    "label",
                    List.of(idColumn(), valueColumn("type", ColumnType.Text), valueColumn("label", ColumnType.Text))
                ),
                connection
            );
            for (ViewsConfig.View view : viewsConfig.views) {
                createOrUpdateView(view, connection);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        return postgres;
    }

    private SqlSessionFactory initClickhouse(Config.ViewColumnDatabase dbConfig) {

        log.debug("Initializing the Clickhouse database connection");
        var databaseConfig = new HikariConfig();
        databaseConfig.setJdbcUrl(dbConfig.url);
        databaseConfig.setUsername(dbConfig.username);
        databaseConfig.setPassword(dbConfig.password);
        databaseConfig.setAutoCommit(dbConfig.autoCommit);
        databaseConfig.setConnectionTimeout(dbConfig.connectionTimeout);
        databaseConfig.setMaximumPoolSize(dbConfig.maxPoolSize);

        HikariDataSource clickhouse = new HikariDataSource(databaseConfig);

        try (var connection = clickhouse.getConnection()) {
            log.debug("Clickhouse connection: {}", connection.getMetaData().getDatabaseProductName());
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

        TransactionFactory transactionFactory = new JdbcTransactionFactory();
        Environment environment = new Environment("development", transactionFactory, clickhouse);
        Configuration mybatisConfig = new Configuration(environment);
        mybatisConfig.addMapper(ViewMapper.class);

        return new SqlSessionFactoryBuilder().build(mybatisConfig);
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

    void createOrUpdateTable(Table table, Connection connection) throws SQLException {
        log.debug("Check if table {} exists ...", table.name);
        var resultSet = connection.getMetaData().getTables(null, null, table.name, null);
        var tableExists = resultSet.next();
        if (!tableExists) {
            createTable(table, connection);
        } else {
            updateTable(table, connection);
        }
    }

    void createOrUpdateJoinTable(Table table, Connection connection) throws SQLException {
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

    void validateViewConfig(ViewsConfig.View view) {
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

    void createOrUpdateView(ViewsConfig.View view, Connection connection) throws SQLException {
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
        createOrUpdateTable(table, connection);
        configuration.viewTables.put(view.name, table);
        // Add property tables
        var setColumns =
                view.columns.stream().filter(column -> column.type.isSet()).toList();
        for (ViewsConfig.View.Column column : setColumns) {
            var propertyTableColumns = new ArrayList<Table.ColumnDefinition>();
            propertyTableColumns.add(idColumn(view.name));
            propertyTableColumns.add(valueColumn(column.name, ColumnType.Identifier));
            var name = String.format("%s_%s", view.name.toLowerCase(), column.name.toLowerCase());
            var propertyTable = new Table(name, propertyTableColumns);
            createOrUpdateTable(propertyTable, connection);
            configuration.propertyTables.putIfAbsent(view.name, new HashMap<>());
            configuration.propertyTables.get(view.name).put(column.name, propertyTable);
        }
        if (view.join != null) {
            // Add join tables
            for (ViewsConfig.View.JoinView join : view.join) {
                var joinTable = getJoinTable(join, view);
                createOrUpdateJoinTable(joinTable, connection);
                configuration.joinTables.putIfAbsent(view.name, new HashMap<>());
                configuration.joinTables.get(view.name).put(join.view, joinTable);
                var joinView = configuration.viewConfig.get(join.view);
            }
        }
    }

    public static Table getJoinTable(ViewsConfig.View.JoinView join, ViewsConfig.View view) {
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
