package com.dasi.qa.agent.application.configuration;

import com.dasi.qa.agent.application.properties.DatasourceProperties;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;

import javax.sql.DataSource;

@Configuration
@EnableConfigurationProperties(DatasourceProperties.class)
public class DataSourceConfiguration {

    @Bean(name = "mysqlDataSource")
    @Primary
    public DataSource mysqlDataSource(DatasourceProperties properties) {
        return buildDataSource("qa-agent-mysql", properties.getMysql());
    }

    @Bean(name = "postgresDataSource")
    public DataSource postgresDataSource(DatasourceProperties properties) {
        return buildDataSource("qa-agent-postgres", properties.getPostgres());
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
