package com.dasi.qa.agent.application.configuration;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static com.dasi.qa.agent.types.constant.DefaultConstant.MAX_MESSAGE;

@Configuration
public class AgentConfiguration {

    @Bean
    public ChatMemoryProvider qaGenerationChatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.withMaxMessages(MAX_MESSAGE);
    }

}
