package com.dasi.qa.agent.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "qa-agent.mail")
public class MailProperties {

    private String host;

    private Integer port;

    private String username;

    private String password;
}
