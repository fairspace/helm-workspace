package io.fairspace.saturn;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.testcontainers.containers.PostgreSQLContainer;

import io.fairspace.saturn.config.properties.SearchProperties;
import io.fairspace.saturn.config.properties.ViewDatabaseProperties;

public class PostgresAwareTest {

    protected static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @BeforeClass
    public static void beforeAll() {
        postgres.start();
    }

    @AfterClass
    public static void afterAll() {
        postgres.stop();
    }

    protected ViewDatabaseProperties buildViewDatabaseConfig() {
        var viewDatabase = new ViewDatabaseProperties();
        viewDatabase.setUrl(postgres.getJdbcUrl());
        viewDatabase.setUsername(postgres.getUsername());
        viewDatabase.setPassword(postgres.getPassword());
        viewDatabase.setMaxPoolSize(5);
        return viewDatabase;
    }

    protected SearchProperties buildSearchProperties() {
        SearchProperties searchProperties = new SearchProperties();
        searchProperties.setCountRequestTimeout(60000);
        searchProperties.setPageRequestTimeout(10000);
        searchProperties.setMaxJoinItems(50);
        return searchProperties;
    }

    public DataSource getDataSource(ViewDatabaseProperties viewDatabaseProperties) {
        var databaseConfig = getHikariConfig(viewDatabaseProperties);
        return new HikariDataSource(databaseConfig);
    }

    private HikariConfig getHikariConfig(ViewDatabaseProperties viewDatabaseProperties) {
        var databaseConfig = new HikariConfig();
        databaseConfig.setJdbcUrl(viewDatabaseProperties.getUrl());
        databaseConfig.setUsername(viewDatabaseProperties.getUsername());
        databaseConfig.setPassword(viewDatabaseProperties.getPassword());
        databaseConfig.setAutoCommit(viewDatabaseProperties.isAutoCommitEnabled());
        databaseConfig.setConnectionTimeout(viewDatabaseProperties.getConnectionTimeout());
        databaseConfig.setMaximumPoolSize(viewDatabaseProperties.getMaxPoolSize());
        return databaseConfig;
    }
}
