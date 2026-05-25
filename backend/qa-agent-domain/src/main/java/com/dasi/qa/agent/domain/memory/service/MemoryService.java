package com.dasi.qa.agent.domain.memory.service;

import com.dasi.qa.agent.domain.memory.repository.IMemoryRepository;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.types.dto.request.memory.MemoryHideRequest;
import com.dasi.qa.agent.types.dto.response.memory.UserMemoryDetailResponse;
import com.dasi.qa.agent.types.dto.response.memory.UserMemoryResponse;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import com.dasi.qa.agent.types.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@Slf4j
public class MemoryService implements IMemoryService {

    private final IMemoryRepository memoryRepository;
    private final IContextUtil contextUtil;

    public MemoryService(IMemoryRepository memoryRepository,
                         IContextUtil contextUtil) {
        this.memoryRepository = memoryRepository;
        this.contextUtil = contextUtil;
    }

    @Override
    public List<UserMemoryResponse> list() {
        return memoryRepository.listActiveMemories(contextUtil.getUserId());
    }

    @Override
    public UserMemoryDetailResponse detail(String memoryId) {
        if (!StringUtils.hasText(memoryId)) {
            throw new ApiException(ResultCode.BAD_REQUEST, "记忆 ID 不能为空");
        }
        return memoryRepository.detailMemory(memoryId, contextUtil.getUserId());
    }

    @Override
    public void hide(MemoryHideRequest request) {
        memoryRepository.hideMemory(request.getMemoryId(), contextUtil.getUserId());
    }
}
