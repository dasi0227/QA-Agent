package com.dasi.qa.agent.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "qa-agent.thread-pool")
public class ThreadPoolProperties {

    private Integer corePoolSize;

    private Integer maxPoolSize;

    private Integer queueCapacity;

    private Integer keepAliveSeconds;

    private String threadNamePrefix;

    private Boolean waitForTasksToCompleteOnShutdown;

    private Integer awaitTerminationSeconds;
}
