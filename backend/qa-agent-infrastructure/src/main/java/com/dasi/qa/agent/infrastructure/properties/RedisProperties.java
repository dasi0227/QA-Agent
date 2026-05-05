package com.dasi.qa.agent.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "qa-agent.redis")
public class RedisProperties {

    private String host;

    private Integer port;

    private String password;

    private Integer database;
}
