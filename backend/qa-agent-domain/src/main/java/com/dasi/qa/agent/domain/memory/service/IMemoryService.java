package com.dasi.qa.agent.domain.memory.service;

import com.dasi.qa.agent.types.dto.request.memory.MemoryHideRequest;
import com.dasi.qa.agent.types.dto.response.memory.UserMemoryDetailResponse;
import com.dasi.qa.agent.types.dto.response.memory.UserMemoryResponse;

import java.util.List;

public interface IMemoryService {

    List<UserMemoryResponse> list();

    UserMemoryDetailResponse detail(String memoryId);

    void hide(MemoryHideRequest request);
}
