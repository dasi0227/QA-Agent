package com.dasi.qa.agent.domain.agent.service.shared;

import com.dasi.qa.agent.domain.memory.repository.IMemoryRepository;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import com.dasi.qa.agent.types.dto.response.memory.UserMemoryResponse;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class UserMemoryProvider {

    private final IMemoryRepository memoryRepository;
    private final IJsonUtil jsonUtil;

    public UserMemoryProvider(IMemoryRepository memoryRepository, IJsonUtil jsonUtil) {
        this.memoryRepository = memoryRepository;
        this.jsonUtil = jsonUtil;
    }

    public String getGenerationMemory(String userId) {
        return jsonUtil.toJsonString(memoryRepository.listActiveMemories(userId).stream()
                .map(this::toGenerationMemory)
                .toList());
    }

    private Map<String, Object> toGenerationMemory(UserMemoryResponse memory) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("memoryType", memory.getMemoryType());
        value.put("memoryTypeText", memory.getMemoryTypeText());
        value.put("targetType", memory.getTargetType());
        value.put("targetKey", memory.getTargetKey());
        value.put("targetKeyText", memory.getTargetKeyText());
        value.put("content", memory.getContent());
        value.put("supportCount", memory.getSupportCount());
        return value;
    }
}
