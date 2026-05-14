package com.dasi.qa.agent.application.configuration;

import com.dasi.qa.agent.infrastructure.properties.RewriterLlmProperties;
import com.dasi.qa.agent.infrastructure.properties.SummarizerLlmProperties;
import com.dasi.qa.agent.infrastructure.properties.SupervisorLlmProperties;
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
@EnableConfigurationProperties({SupervisorLlmProperties.class, WebSearchLlmProperties.class, RewriterLlmProperties.class, SummarizerLlmProperties.class})
public class LlmModelConfiguration {

    @Bean("supervisorModel")
    public ChatModel supervisorChatModel(SupervisorLlmProperties properties) {
        log.info("【配置】系统模型 SupervisorModel: baseUrl={}, model={}", properties.getBaseUrl(), properties.getModel());
        return build(properties.getBaseUrl(), properties.getApiKey(), properties.getModel());
    }

    @Bean("webSearchModel")
    public ChatModel webSearchChatModel(WebSearchLlmProperties properties) {
        log.info("【配置】系统模型 WebSearchModel: baseUrl={}, model={}", properties.getBaseUrl(), properties.getModel());
        return build(properties.getBaseUrl(), properties.getApiKey(), properties.getModel());
    }

    @Bean("rewriterModel")
    public ChatModel rewriterChatModel(RewriterLlmProperties properties) {
        log.info("【配置】系统模型 RewriterModel: baseUrl={}, model={}", properties.getBaseUrl(), properties.getModel());
        return build(properties.getBaseUrl(), properties.getApiKey(), properties.getModel());
    }

    @Bean("summarizerModel")
    public ChatModel summarizerChatModel(SummarizerLlmProperties properties) {
        log.info("【配置】系统模型 SummarizerModel: baseUrl={}, model={}", properties.getBaseUrl(), properties.getModel());
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
