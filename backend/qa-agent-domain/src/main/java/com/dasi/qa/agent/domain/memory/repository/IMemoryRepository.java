package com.dasi.qa.agent.domain.memory.repository;

import com.dasi.qa.agent.types.dto.response.memory.UserMemoryDetailResponse;
import com.dasi.qa.agent.types.dto.response.memory.UserMemoryResponse;

import java.util.List;

public interface IMemoryRepository {

    List<UserMemoryResponse> listActiveMemories(String userId);

    UserMemoryDetailResponse detailMemory(String memoryId, String userId);

    void hideMemory(String memoryId, String userId);
}
