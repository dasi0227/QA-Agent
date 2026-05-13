package com.dasi.qa.agent.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "qa-agent.llm.rewriter")
public class RewriterLlmProperties {

    private String baseUrl;

    private String apiKey;

    private String model;
}
