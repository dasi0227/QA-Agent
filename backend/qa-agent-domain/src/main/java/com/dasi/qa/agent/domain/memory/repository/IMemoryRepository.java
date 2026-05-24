package com.dasi.qa.agent.domain.memory.repository;

import com.dasi.qa.agent.domain.memory.model.MemoryIngestContext;
import com.dasi.qa.agent.domain.memory.model.UserMemory;
import com.dasi.qa.agent.domain.memory.model.UserMemoryEvidence;
import com.dasi.qa.agent.types.dto.response.memory.UserMemoryDetailResponse;
import com.dasi.qa.agent.types.dto.response.memory.UserMemoryResponse;

import java.util.List;

public interface IMemoryRepository {

    List<UserMemoryResponse> listActiveMemories(String userId);

    UserMemoryDetailResponse detailMemory(String memoryId, String userId);

    void hideMemory(String memoryId, String userId);

    MemoryIngestContext getIngestContext(String sessionId, String userId);

    UserMemory findMemoryByKey(String userId, String memoryType, String targetType, String targetKey);

    UserMemory findActiveMemoryById(String memoryId, String userId);

    void createMemory(UserMemory memory);

    void updateMemory(UserMemory memory);

    boolean existsEvidence(String memoryId, String sessionItemId);

    void createEvidence(UserMemoryEvidence evidence);
}
