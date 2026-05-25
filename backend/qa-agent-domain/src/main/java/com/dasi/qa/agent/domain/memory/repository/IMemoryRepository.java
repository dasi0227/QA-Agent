package com.dasi.qa.agent.domain.memory.repository;

import com.dasi.qa.agent.domain.memory.model.vo.MemoryIngestContext;
import com.dasi.qa.agent.domain.memory.model.dto.Memory;
import com.dasi.qa.agent.domain.memory.model.dto.MemoryEvidence;
import com.dasi.qa.agent.types.dto.response.memory.UserMemoryDetailResponse;
import com.dasi.qa.agent.types.dto.response.memory.UserMemoryResponse;

import java.util.List;

public interface IMemoryRepository {

    List<UserMemoryResponse> listActiveMemories(String userId);

    UserMemoryDetailResponse detailMemory(String memoryId, String userId);

    void hideMemory(String memoryId, String userId);

    MemoryIngestContext getIngestContext(String sessionId, String userId);

    Memory findMemoryByKey(String userId, String memoryType, String targetType, String targetKey);

    Memory findActiveMemoryById(String memoryId, String userId);

    void createMemory(Memory memory);

    void updateMemory(Memory memory);

    boolean existsEvidence(String memoryId, String sessionItemId);

    void createEvidence(MemoryEvidence evidence);
}
