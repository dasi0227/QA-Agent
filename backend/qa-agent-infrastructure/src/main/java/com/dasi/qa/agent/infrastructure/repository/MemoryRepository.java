package com.dasi.qa.agent.infrastructure.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dasi.qa.agent.domain.agent.service.memory.model.enumeration.MemoryStatus;
import com.dasi.qa.agent.domain.memory.repository.IMemoryRepository;
import com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory;
import com.dasi.qa.agent.infrastructure.persistent.entity.UserMemoryEvidence;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.UserMemoryEvidenceMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.UserMemoryMapper;
import com.dasi.qa.agent.types.dto.response.memory.UserMemoryDetailResponse;
import com.dasi.qa.agent.types.dto.response.memory.UserMemoryEvidenceResponse;
import com.dasi.qa.agent.types.dto.response.memory.UserMemoryResponse;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import com.dasi.qa.agent.types.exception.ApiException;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class MemoryRepository implements IMemoryRepository {

    private final UserMemoryMapper userMemoryMapper;
    private final UserMemoryEvidenceMapper userMemoryEvidenceMapper;

    public MemoryRepository(UserMemoryMapper userMemoryMapper,
                            UserMemoryEvidenceMapper userMemoryEvidenceMapper) {
        this.userMemoryMapper = userMemoryMapper;
        this.userMemoryEvidenceMapper = userMemoryEvidenceMapper;
    }

    @Override
    public List<UserMemoryResponse> listActiveMemories(String userId) {
        return userMemoryMapper.selectList(new LambdaQueryWrapper<UserMemory>()
                        .eq(UserMemory::getUserId, userId)
                        .eq(UserMemory::getStatus, MemoryStatus.ACTIVE.name())
                        .orderByDesc(UserMemory::getLastSeenAt)
                        .orderByDesc(UserMemory::getUpdatedAt))
                .stream()
                .map(this::toMemoryResponse)
                .toList();
    }

    @Override
    public UserMemoryDetailResponse detailMemory(String memoryId, String userId) {
        UserMemory memory = requireMemory(memoryId, userId);
        List<UserMemoryEvidenceResponse> evidence = userMemoryEvidenceMapper.selectList(
                        new LambdaQueryWrapper<UserMemoryEvidence>()
                                .eq(UserMemoryEvidence::getMemoryId, memoryId)
                                .eq(UserMemoryEvidence::getUserId, userId)
                                .orderByDesc(UserMemoryEvidence::getCreatedAt))
                .stream()
                .map(this::toEvidenceResponse)
                .toList();
        return UserMemoryDetailResponse.builder()
                .memory(toMemoryResponse(memory))
                .evidenceList(evidence)
                .build();
    }

    @Override
    public void hideMemory(String memoryId, String userId) {
        requireMemory(memoryId, userId);
        userMemoryMapper.update(null, new LambdaUpdateWrapper<UserMemory>()
                .eq(UserMemory::getId, memoryId)
                .eq(UserMemory::getUserId, userId)
                .set(UserMemory::getStatus, MemoryStatus.HIDDEN.name())
                .set(UserMemory::getHiddenAt, LocalDateTime.now())
                .set(UserMemory::getUpdatedAt, LocalDateTime.now()));
    }

    private UserMemory requireMemory(String memoryId, String userId) {
        UserMemory memory = userMemoryMapper.selectOne(
                new LambdaQueryWrapper<UserMemory>()
                        .eq(UserMemory::getId, memoryId)
                        .eq(UserMemory::getUserId, userId));
        if (memory == null) {
            throw new ApiException(ResultCode.NOT_FOUND, "记忆不存在");
        }
        return memory;
    }

    private UserMemoryResponse toMemoryResponse(UserMemory entity) {
        UserMemoryResponse response = new UserMemoryResponse();
        response.setId(entity.getId());
        response.setCreatedAt(time(entity.getCreatedAt()));
        response.setUpdatedAt(time(entity.getUpdatedAt()));
        response.setMemoryType(entity.getMemoryType());
        response.setTargetType(entity.getTargetType());
        response.setTargetKey(entity.getTargetKey());
        response.setSummary(entity.getSummary());
        response.setContent(entity.getContent());
        response.setSupportCount(entity.getSupportCount());
        response.setStatus(entity.getStatus());
        response.setFirstSeenAt(time(entity.getFirstSeenAt()));
        response.setLastSeenAt(time(entity.getLastSeenAt()));
        response.setHiddenAt(time(entity.getHiddenAt()));
        response.setLatestSessionId(entity.getLatestSessionId());
        response.setLatestQaSetId(entity.getLatestQaSetId());
        return response;
    }

    private UserMemoryEvidenceResponse toEvidenceResponse(UserMemoryEvidence entity) {
        UserMemoryEvidenceResponse response = new UserMemoryEvidenceResponse();
        response.setId(entity.getId());
        response.setCreatedAt(time(entity.getCreatedAt()));
        response.setMemoryId(entity.getMemoryId());
        response.setSessionId(entity.getSessionId());
        response.setSessionItemId(entity.getSessionItemId());
        response.setQaSetId(entity.getQaSetId());
        response.setQaItemId(entity.getQaItemId());
        response.setModuleTag(entity.getModuleTag());
        response.setQuestionSnapshot(entity.getQuestionSnapshot());
        response.setResult(entity.getResult());
        response.setScore(entity.getScore());
        response.setSourceChunkIdsJson(entity.getSourceChunkIdsJson());
        response.setEvidenceSummary(entity.getEvidenceSummary());
        return response;
    }

    private String time(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

}
