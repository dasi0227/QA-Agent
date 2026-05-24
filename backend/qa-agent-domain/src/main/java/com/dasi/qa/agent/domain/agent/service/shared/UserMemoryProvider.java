package com.dasi.qa.agent.domain.agent.service.shared;

import org.springframework.stereotype.Component;

@Component
public class UserMemoryProvider {

    public String getGenerationMemory(String userId) {
        return "[]";
    }
}
