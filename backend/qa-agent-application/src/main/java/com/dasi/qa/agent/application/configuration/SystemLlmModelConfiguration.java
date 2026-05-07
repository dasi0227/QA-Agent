package com.dasi.qa.agent.application.configuration;

import com.dasi.qa.agent.infrastructure.properties.SupervisorLlmProperties;
import com.dasi.qa.agent.infrastructure.properties.WebSearchLlmProperties;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
@EnableConfigurationProperties({SupervisorLlmProperties.class, WebSearchLlmProperties.class})
public class SystemLlmModelConfiguration {

    @Bean("supervisorModel")
    public ChatModel supervisorChatModel(SupervisorLlmProperties properties) {
        return build(properties.getBaseUrl(), properties.getApiKey(), properties.getModel());
    }

    @Bean("webSearchModel")
    public ChatModel webSearchChatModel(WebSearchLlmProperties properties) {
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
