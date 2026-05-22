package com.dasi.qa.agent.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "qa-agent.xxl-job")
public class XxlJobProperties {

    private String adminAddresses;

    private String appName;

    private int port;

    private String accessToken;

    private String logPath;

    private int logRetentionDays;
}
