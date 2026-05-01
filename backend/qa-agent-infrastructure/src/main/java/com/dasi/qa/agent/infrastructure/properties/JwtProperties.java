package com.dasi.qa.agent.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "qa-agent.jwt")
public class JwtProperties {

    private String secret;

    private String issuer;

    private Long accessTokenTtlMinutes = 15L;

    private Long refreshTokenTtlDays = 7L;
}
