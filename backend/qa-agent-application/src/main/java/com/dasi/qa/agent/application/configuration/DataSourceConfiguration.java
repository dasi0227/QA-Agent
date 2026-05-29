package com.dasi.qa.agent.application.configuration;

import com.dasi.qa.agent.infrastructure.properties.DatasourceProperties;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@Slf4j
@Configuration
@EnableConfigurationProperties(DatasourceProperties.class)
public class DataSourceConfiguration {

    @Bean(name = "mysqlDataSource")
    @Primary
    public DataSource mysqlDataSource(DatasourceProperties properties) {
        var node = properties.getMysql();
        log.info("【配置】MySQL DataSource: host={}, port={}, database={}, maxPoolSize={}", node.getHost(), node.getPort(), node.getDatabase(), node.getMaximumPoolSize());
        return buildDataSource("qa-agent-mysql", node);
    }

    @Bean(name = "postgresDataSource")
    public DataSource postgresDataSource(DatasourceProperties properties) {
        var node = properties.getPostgres();
        log.info("【配置】PostgreSQL DataSource: host={}, port={}, database={}, maxPoolSize={}", node.getHost(), node.getPort(), node.getDatabase(), node.getMaximumPoolSize());
        return buildDataSource("qa-agent-postgres", node);
    }

    @Bean(name = "mysqlTransactionManager")
    public DataSourceTransactionManager mysqlTransactionManager(
        @Qualifier("mysqlDataSource") DataSource dataSource
    ) {
        return new DataSourceTransactionManager(dataSource);
    }

    @Bean(name = "postgresTransactionManager")
    public DataSourceTransactionManager postgresTransactionManager(
        @Qualifier("postgresDataSource") DataSource dataSource
    ) {
        return new DataSourceTransactionManager(dataSource);
    }

    private HikariDataSource buildDataSource(String poolName, DatasourceProperties.Node node) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setPoolName(poolName);
        dataSource.setJdbcUrl(node.getJdbcUrl());
        dataSource.setDriverClassName(node.getDriverClassName());
        dataSource.setUsername(node.getUsername());
        dataSource.setPassword(node.getPassword());
        dataSource.setMinimumIdle(node.getMinimumIdle());
        dataSource.setMaximumPoolSize(node.getMaximumPoolSize());
        dataSource.setConnectionTimeout(node.getConnectionTimeoutMs());
        return dataSource;
    }
}
