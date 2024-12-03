package io.fairspace.saturn.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.fairspace.saturn.config.properties.ColumnStoreProperties;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(value = "application.view-column-store.enabled", havingValue = "true")
public class ColumnStoreConfig {

    @Bean
    public SqlSessionFactory sqlSessionFactory(ColumnStoreProperties columnStoreProperties) throws Exception {
        SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
        var databaseConfig = getHikariConfig(columnStoreProperties);
        factoryBean.setDataSource(new HikariDataSource(databaseConfig));
        return factoryBean.getObject();
    }

    private HikariConfig getHikariConfig(ColumnStoreProperties columnStoreProperties) {
        var databaseConfig = new HikariConfig();
        databaseConfig.setJdbcUrl(columnStoreProperties.getUrl());
        databaseConfig.setUsername(columnStoreProperties.getUsername());
        databaseConfig.setPassword(columnStoreProperties.getPassword());
        databaseConfig.setAutoCommit(columnStoreProperties.isAutoCommitEnabled());
        databaseConfig.setConnectionTimeout(columnStoreProperties.getConnectionTimeout());
        databaseConfig.setMaximumPoolSize(columnStoreProperties.getMaxPoolSize());
        return databaseConfig;
    }
}
