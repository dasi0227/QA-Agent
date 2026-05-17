package com.dasi.qa.agent.domain.agent.service.generate.support;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.StringUtils;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = LlmTest.TestConfig.class)
public class LlmTest {

    @Value("${SUPERVISOR_LLM_BASE_URL:}")
    private String baseUrl;

    @Value("${SUPERVISOR_LLM_API_KEY:}")
    private String apiKey;

    @Value("${SUPERVISOR_LLM_MODEL:}")
    private String modelName;

    @Test
    void supervisorShouldReplyHello() {
        assumeTrue(hasText(baseUrl), "SUPERVISOR_LLM_BASE_URL is not configured");
        assumeTrue(hasText(apiKey), "SUPERVISOR_LLM_API_KEY is not configured");
        assumeTrue(hasText(modelName), "SUPERVISOR_LLM_MODEL is not configured");

        System.out.println("baseUrl=" + baseUrl);
        System.out.println("modelName=" + modelName);
        System.out.println("apiKeySuffix=" + apiKey.substring(Math.max(0, apiKey.length() - 4)));

        ChatModel supervisor = OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(30))
                .maxRetries(0)
                .build();

        ChatResponse response = supervisor.chat(
                SystemMessage.from("你是 QA_Agent 的 supervisor 连通性测试模型，请简短回复。"),
                UserMessage.from("hello")
        );

        assertNotNull(response);
        assertNotNull(response.aiMessage());
        assertTrue(hasText(response.aiMessage().text()));
        System.out.println("Supervisor response: " + response.aiMessage().text());
    }

    private boolean hasText(String value) {
        return StringUtils.hasText(value);
    }

    @Configuration
    @PropertySource(value = {
            "file:qa-agent-application/src/main/resources/.env",
            "file:../qa-agent-application/src/main/resources/.env",
            "file:backend/qa-agent-application/src/main/resources/.env"
    }, ignoreResourceNotFound = true)
    static class TestConfig {

        @Bean
        static PropertySourcesPlaceholderConfigurer propertySourcesPlaceholderConfigurer() {
            return new PropertySourcesPlaceholderConfigurer();
        }
    }
}
