package io.fairspace.saturn.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.fairspace.saturn.config.properties.ViewDatabaseProperties;

@Configuration
@ConditionalOnProperty(value = "application.view-database.enabled", havingValue = "true")
public class SecondaryStorageConfig {

    @Bean
    public DataSource dataSource(ViewDatabaseProperties viewDatabaseProperties) {
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
