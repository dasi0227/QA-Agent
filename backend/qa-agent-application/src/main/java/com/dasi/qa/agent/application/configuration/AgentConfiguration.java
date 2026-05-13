package com.dasi.qa.agent.application.configuration;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfiguration {

    public static final int MAX_MESSAGE = 20;

    @Bean
    public ChatMemoryProvider qaGenerationChatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.withMaxMessages(MAX_MESSAGE);
    }

}
