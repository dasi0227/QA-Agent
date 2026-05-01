package com.dasi.qa.agent.application.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "qa-agent.datasource")
public class DatasourceProperties {

    private Node mysql = new Node();

    private Node postgres = new Node();

    @Data
    public static class Node {

        private String host;

        private Integer port;

        private String database;

        private String username;

        private String password;

        private String jdbcUrl;

        private String driverClassName;

        private Integer minimumIdle;

        private Integer maximumPoolSize;

        private Long connectionTimeoutMs;
    }
}
