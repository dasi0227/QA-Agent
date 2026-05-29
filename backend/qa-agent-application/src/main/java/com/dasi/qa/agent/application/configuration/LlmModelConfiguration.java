package com.dasi.qa.agent.application.configuration;

import com.dasi.qa.agent.infrastructure.properties.WebSearchLlmProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Slf4j
@Configuration
@EnableConfigurationProperties({WebSearchLlmProperties.class})
public class LlmModelConfiguration {

    @Bean("webSearchModel")
    public ChatModel webSearchChatModel(WebSearchLlmProperties properties) {
        log.info("【配置】WebSearchModel: baseUrl={}, model={}", properties.getBaseUrl(), properties.getModel());
        return build(properties.getBaseUrl(), properties.getApiKey(), properties.getModel());
    }

    private ChatModel build(String baseUrl, String apiKey, String model) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(model)
                .timeout(Duration.ofSeconds(60))
                .maxRetries(1)
                .build();
    }
}
