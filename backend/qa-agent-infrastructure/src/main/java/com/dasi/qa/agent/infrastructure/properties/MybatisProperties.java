package com.dasi.qa.agent.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "qa-agent.mybatis")
public class MybatisProperties {

    private String mysqlMapperLocations;

    private String postgresMapperLocations;

    private boolean mapUnderscoreToCamelCase;

    private boolean cacheEnabled;

    private boolean overflow;

    private Long maxLimit;
}
