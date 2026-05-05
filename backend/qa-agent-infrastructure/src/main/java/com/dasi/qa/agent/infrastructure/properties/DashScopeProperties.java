package com.dasi.qa.agent.infrastructure.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "qa-agent.dashscope")
public class DashScopeProperties {

    private String apiKey;

    private String embeddingModel;

    private String rerankModel;

    private String llmModel = "qwen-turbo";
}
