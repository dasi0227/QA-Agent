package com.dasi.qa.agent.application.configuration;

import com.dasi.qa.agent.infrastructure.properties.DashScopeProperties;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DashScopeProperties.class)
public class RagConfiguration {

    @Bean
    public QwenEmbeddingModel qwenEmbeddingModel(DashScopeProperties properties) {
        return QwenEmbeddingModel.builder()
                .apiKey(properties.getApiKey())
                .modelName(properties.getEmbeddingModel())
                .build();
    }
}
