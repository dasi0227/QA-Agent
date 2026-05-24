package com.dasi.qa.agent.infrastructure.repository;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dasi.qa.agent.domain.memory.model.MemoryIngestContext;
import com.dasi.qa.agent.domain.memory.model.MemoryIngestItem;
import com.dasi.qa.agent.domain.memory.model.UserMemory;
import com.dasi.qa.agent.domain.memory.model.UserMemoryEvidence;
import com.dasi.qa.agent.domain.memory.model.enumeration.MemoryStatus;
import com.dasi.qa.agent.domain.memory.repository.IMemoryRepository;
import com.dasi.qa.agent.infrastructure.persistent.entity.PracticeSession;
import com.dasi.qa.agent.infrastructure.persistent.entity.PracticeSessionItem;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaItem;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaSet;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.PracticeSessionItemMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.PracticeSessionMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaItemMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaSetMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.UserMemoryEvidenceMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.UserMemoryMapper;
import com.dasi.qa.agent.types.dto.response.practice.JudgeDetail;
import com.dasi.qa.agent.types.dto.response.memory.UserMemoryDetailResponse;
import com.dasi.qa.agent.types.dto.response.memory.UserMemoryEvidenceResponse;
import com.dasi.qa.agent.types.dto.response.memory.UserMemoryResponse;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import com.dasi.qa.agent.types.exception.ApiException;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class MemoryRepository implements IMemoryRepository {

    private final UserMemoryMapper userMemoryMapper;
    private final UserMemoryEvidenceMapper userMemoryEvidenceMapper;
    private final PracticeSessionMapper practiceSessionMapper;
    private final PracticeSessionItemMapper practiceSessionItemMapper;
    private final QaSetMapper qaSetMapper;
    private final QaItemMapper qaItemMapper;

    public MemoryRepository(UserMemoryMapper userMemoryMapper,
                            UserMemoryEvidenceMapper userMemoryEvidenceMapper,
                            PracticeSessionMapper practiceSessionMapper,
                            PracticeSessionItemMapper practiceSessionItemMapper,
                            QaSetMapper qaSetMapper,
                            QaItemMapper qaItemMapper) {
        this.userMemoryMapper = userMemoryMapper;
        this.userMemoryEvidenceMapper = userMemoryEvidenceMapper;
        this.practiceSessionMapper = practiceSessionMapper;
        this.practiceSessionItemMapper = practiceSessionItemMapper;
        this.qaSetMapper = qaSetMapper;
        this.qaItemMapper = qaItemMapper;
    }

    @Override
    public List<UserMemoryResponse> listActiveMemories(String userId) {
        return userMemoryMapper.selectList(new LambdaQueryWrapper<com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory>()
                        .eq(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory::getUserId, userId)
                        .eq(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory::getStatus, MemoryStatus.ACTIVE.name())
                        .orderByDesc(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory::getLastSeenAt)
                        .orderByDesc(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory::getUpdatedAt))
                .stream()
                .map(this::toMemoryResponse)
                .toList();
    }

    @Override
    public UserMemoryDetailResponse detailMemory(String memoryId, String userId) {
        com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory memory = requireMemory(memoryId, userId);
        List<UserMemoryEvidenceResponse> evidence = userMemoryEvidenceMapper.selectList(
                        new LambdaQueryWrapper<com.dasi.qa.agent.infrastructure.persistent.entity.UserMemoryEvidence>()
                                .eq(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemoryEvidence::getMemoryId, memoryId)
                                .eq(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemoryEvidence::getUserId, userId)
                                .orderByDesc(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemoryEvidence::getCreatedAt))
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
        userMemoryMapper.update(null, new LambdaUpdateWrapper<com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory>()
                .eq(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory::getId, memoryId)
                .eq(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory::getUserId, userId)
                .set(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory::getStatus, MemoryStatus.HIDDEN.name())
                .set(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory::getHiddenAt, LocalDateTime.now())
                .set(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory::getUpdatedAt, LocalDateTime.now()));
    }

    @Override
    public MemoryIngestContext getIngestContext(String sessionId, String userId) {
        PracticeSession session = practiceSessionMapper.selectById(sessionId);
        if (session == null || !userId.equals(session.getUserId())) {
            throw new ApiException(ResultCode.NOT_FOUND, "练习记录不存在");
        }
        QaSet qaSet = qaSetMapper.selectById(session.getQaSetId());
        if (qaSet == null || !userId.equals(qaSet.getUserId())) {
            throw new ApiException(ResultCode.NOT_FOUND, "题集不存在");
        }
        List<PracticeSessionItem> sessionItems = practiceSessionItemMapper.selectList(
                new LambdaQueryWrapper<PracticeSessionItem>()
                        .eq(PracticeSessionItem::getSessionId, sessionId)
                        .eq(PracticeSessionItem::getUserId, userId)
                        .orderByAsc(PracticeSessionItem::getSortOrder));
        List<MemoryIngestItem> items = sessionItems.stream()
                .map(item -> toIngestItem(item, userId))
                .toList();
        List<UserMemory> existing = userMemoryMapper.selectList(
                        new LambdaQueryWrapper<com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory>()
                                .eq(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory::getUserId, userId)
                                .eq(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory::getStatus, MemoryStatus.ACTIVE.name())
                                .orderByDesc(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory::getLastSeenAt)
                                .last("LIMIT 20"))
                .stream()
                .map(this::toDomainMemory)
                .toList();
        return MemoryIngestContext.builder()
                .sessionId(session.getId())
                .userId(session.getUserId())
                .qaSetId(session.getQaSetId())
                .qaSetTitle(qaSet.getTitle())
                .totalQuestions(session.getTotalQuestions())
                .score(session.getScore())
                .accuracy(session.getAccuracy() == null ? "" : session.getAccuracy().toPlainString())
                .perfectCount(session.getPerfectCount())
                .correctCount(session.getCorrectCount())
                .deficientCount(session.getDeficientCount())
                .wrongCount(session.getWrongCount())
                .unknownCount(session.getUnknownCount())
                .memoryClueJson(StringUtils.hasText(session.getMemoryClueJson()) ? session.getMemoryClueJson() : "[]")
                .items(items)
                .existingMemories(existing)
                .build();
    }

    @Override
    public UserMemory findMemoryByKey(String userId, String memoryType, String targetType, String targetKey) {
        com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory entity = userMemoryMapper.selectOne(
                new LambdaQueryWrapper<com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory>()
                        .eq(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory::getUserId, userId)
                        .eq(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory::getMemoryType, memoryType)
                        .eq(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory::getTargetType, targetType)
                        .eq(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory::getTargetKey, targetKey));
        return entity == null ? null : toDomainMemory(entity);
    }

    @Override
    public UserMemory findActiveMemoryById(String memoryId, String userId) {
        com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory entity = userMemoryMapper.selectOne(
                new LambdaQueryWrapper<com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory>()
                        .eq(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory::getId, memoryId)
                        .eq(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory::getUserId, userId)
                        .eq(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory::getStatus, MemoryStatus.ACTIVE.name()));
        return entity == null ? null : toDomainMemory(entity);
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void createMemory(UserMemory memory) {
        userMemoryMapper.insert(toEntityMemory(memory));
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void updateMemory(UserMemory memory) {
        userMemoryMapper.updateById(toEntityMemory(memory));
    }

    @Override
    public boolean existsEvidence(String memoryId, String sessionItemId) {
        return userMemoryEvidenceMapper.selectCount(
                new LambdaQueryWrapper<com.dasi.qa.agent.infrastructure.persistent.entity.UserMemoryEvidence>()
                        .eq(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemoryEvidence::getMemoryId, memoryId)
                        .eq(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemoryEvidence::getSessionItemId, sessionItemId)) > 0;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void createEvidence(UserMemoryEvidence evidence) {
        userMemoryEvidenceMapper.insert(toEntityEvidence(evidence));
    }

    private MemoryIngestItem toIngestItem(PracticeSessionItem item, String userId) {
        QaItem qaItem = qaItemMapper.selectById(item.getQaItemId());
        if (qaItem == null || !userId.equals(qaItem.getUserId())) {
            throw new ApiException(ResultCode.NOT_FOUND, "题目不存在");
        }
        JudgeDetail judgeDetail = judgeDetail(item.getFeedbackJudgeDetail());
        return MemoryIngestItem.builder()
                .sessionItemId(item.getId())
                .qaItemId(item.getQaItemId())
                .question(StringUtils.hasText(item.getQuestionSnapshot()) ? item.getQuestionSnapshot() : qaItem.getQuestion())
                .moduleTag(StringUtils.hasText(item.getModuleTagSnapshot()) ? item.getModuleTagSnapshot() : qaItem.getModuleTag())
                .difficulty(StringUtils.hasText(item.getDifficultySnapshot()) ? item.getDifficultySnapshot() : qaItem.getDifficulty())
                .standardAnswer(StringUtils.hasText(item.getStandardAnswerSnapshot()) ? item.getStandardAnswerSnapshot() : qaItem.getAnswer())
                .userAnswer(item.getUserAnswer())
                .result(item.getResult())
                .score(item.getScore())
                .feedbackSummary(item.getFeedbackSummary())
                .missingPointsJson(judgeDetail == null || judgeDetail.getMissingPoints() == null ? "[]" : JSON.toJSONString(judgeDetail.getMissingPoints()))
                .wrongPointsJson(judgeDetail == null || judgeDetail.getWrongPoints() == null ? "[]" : JSON.toJSONString(judgeDetail.getWrongPoints()))
                .sourceChunkIdsJson(StringUtils.hasText(item.getSourceChunkIdsSnapshotJson()) ? item.getSourceChunkIdsSnapshotJson() : "[]")
                .build();
    }

    private JudgeDetail judgeDetail(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return JSON.parseObject(json, JudgeDetail.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory requireMemory(String memoryId, String userId) {
        com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory memory = userMemoryMapper.selectOne(
                new LambdaQueryWrapper<com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory>()
                        .eq(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory::getId, memoryId)
                        .eq(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory::getUserId, userId));
        if (memory == null) {
            throw new ApiException(ResultCode.NOT_FOUND, "记忆不存在");
        }
        return memory;
    }

    private UserMemoryResponse toMemoryResponse(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory entity) {
        UserMemoryResponse response = new UserMemoryResponse();
        response.setId(entity.getId());
        response.setCreatedAt(time(entity.getCreatedAt()));
        response.setUpdatedAt(time(entity.getUpdatedAt()));
        response.setMemoryType(entity.getMemoryType());
        response.setTargetType(entity.getTargetType());
        response.setTargetKey(entity.getTargetKey());
        response.setTitle(entity.getTitle());
        response.setSummary(entity.getSummary());
        response.setDetail(entity.getDetail());
        response.setConfidence(entity.getConfidence());
        response.setSupportCount(entity.getSupportCount());
        response.setStatus(entity.getStatus());
        response.setFirstSeenAt(time(entity.getFirstSeenAt()));
        response.setLastSeenAt(time(entity.getLastSeenAt()));
        response.setHiddenAt(time(entity.getHiddenAt()));
        response.setLatestSessionId(entity.getLatestSessionId());
        response.setLatestQaSetId(entity.getLatestQaSetId());
        return response;
    }

    private UserMemoryEvidenceResponse toEvidenceResponse(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemoryEvidence entity) {
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
        response.setMemoryClueJson(entity.getMemoryClueJson());
        response.setEvidenceSummary(entity.getEvidenceSummary());
        return response;
    }

    private UserMemory toDomainMemory(com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory entity) {
        return UserMemory.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .memoryType(entity.getMemoryType())
                .targetType(entity.getTargetType())
                .targetKey(entity.getTargetKey())
                .title(entity.getTitle())
                .summary(entity.getSummary())
                .detail(entity.getDetail())
                .confidence(entity.getConfidence())
                .supportCount(entity.getSupportCount())
                .status(entity.getStatus())
                .firstSeenAt(entity.getFirstSeenAt())
                .lastSeenAt(entity.getLastSeenAt())
                .hiddenAt(entity.getHiddenAt())
                .latestSessionId(entity.getLatestSessionId())
                .latestQaSetId(entity.getLatestQaSetId())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }

    private com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory toEntityMemory(UserMemory memory) {
        return com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory.builder()
                .id(memory.getId())
                .userId(memory.getUserId())
                .memoryType(memory.getMemoryType())
                .targetType(memory.getTargetType())
                .targetKey(memory.getTargetKey())
                .title(memory.getTitle())
                .summary(memory.getSummary())
                .detail(memory.getDetail())
                .confidence(memory.getConfidence())
                .supportCount(memory.getSupportCount())
                .status(memory.getStatus())
                .firstSeenAt(memory.getFirstSeenAt())
                .lastSeenAt(memory.getLastSeenAt())
                .hiddenAt(memory.getHiddenAt())
                .latestSessionId(memory.getLatestSessionId())
                .latestQaSetId(memory.getLatestQaSetId())
                .createdAt(memory.getCreatedAt())
                .updatedAt(memory.getUpdatedAt())
                .build();
    }

    private com.dasi.qa.agent.infrastructure.persistent.entity.UserMemoryEvidence toEntityEvidence(UserMemoryEvidence evidence) {
        return com.dasi.qa.agent.infrastructure.persistent.entity.UserMemoryEvidence.builder()
                .id(evidence.getId())
                .memoryId(evidence.getMemoryId())
                .userId(evidence.getUserId())
                .sessionId(evidence.getSessionId())
                .sessionItemId(evidence.getSessionItemId())
                .qaSetId(evidence.getQaSetId())
                .qaItemId(evidence.getQaItemId())
                .moduleTag(evidence.getModuleTag())
                .questionSnapshot(evidence.getQuestionSnapshot())
                .result(evidence.getResult())
                .score(evidence.getScore())
                .sourceChunkIdsJson(evidence.getSourceChunkIdsJson())
                .memoryClueJson(evidence.getMemoryClueJson())
                .evidenceSummary(evidence.getEvidenceSummary())
                .createdAt(evidence.getCreatedAt())
                .build();
    }

    private String time(LocalDateTime value) {
        return value == null ? null : value.toString();
    }
}
