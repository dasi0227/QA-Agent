package com.dasi.qa.agent.infrastructure.repository;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.dasi.qa.agent.domain.agent.model.DraftItem;
import com.dasi.qa.agent.domain.agent.model.PlanResult;
import com.dasi.qa.agent.domain.agent.model.vo.UserLlmModelVO;
import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaGenerationTaskEntity;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaGenerationTaskMessageEntity;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaItemEntity;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaSetDocumentRefEntity;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaSetEntity;
import com.dasi.qa.agent.infrastructure.persistent.entity.SourceDocumentEntity;
import com.dasi.qa.agent.infrastructure.persistent.entity.UserProfileEntity;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaGenerationTaskMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaGenerationTaskMessageMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaItemMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaSetDocumentRefMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaSetMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.SourceDocumentMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.UserProfileMapper;
import com.dasi.qa.agent.types.dto.request.qa.CreateTaskRequest;
import com.dasi.qa.agent.types.dto.response.qa.TaskMessageResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskStatusResponse;
import com.dasi.qa.agent.types.enumeration.ErrorType;
import com.dasi.qa.agent.types.enumeration.GenerationStage;
import com.dasi.qa.agent.types.enumeration.GenerationStatus;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.result.ResultCode;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
public class AgentRepository implements IAgentRepository {

    private final QaGenerationTaskMapper taskMapper;
    private final QaGenerationTaskMessageMapper taskMessageMapper;
    private final UserProfileMapper userProfileMapper;
    private final SourceDocumentMapper sourceDocumentMapper;
    private final QaSetMapper qaSetMapper;
    private final QaItemMapper qaItemMapper;
    private final QaSetDocumentRefMapper qaSetDocumentRefMapper;

    public AgentRepository(QaGenerationTaskMapper taskMapper,
                           QaGenerationTaskMessageMapper taskMessageMapper,
                           UserProfileMapper userProfileMapper,
                           SourceDocumentMapper sourceDocumentMapper,
                           QaSetMapper qaSetMapper,
                           QaItemMapper qaItemMapper,
                           QaSetDocumentRefMapper qaSetDocumentRefMapper) {
        this.taskMapper = taskMapper;
        this.taskMessageMapper = taskMessageMapper;
        this.userProfileMapper = userProfileMapper;
        this.sourceDocumentMapper = sourceDocumentMapper;
        this.qaSetMapper = qaSetMapper;
        this.qaItemMapper = qaItemMapper;
        this.qaSetDocumentRefMapper = qaSetDocumentRefMapper;
    }

    @Override
    public void createGenerationTask(String taskId, String userId, CreateTaskRequest request) {
        LocalDateTime now = LocalDateTime.now();
        QaGenerationTaskEntity entity = new QaGenerationTaskEntity();
        entity.setId(taskId);
        entity.setUserId(userId);
        entity.setTitle(title(request));
        entity.setNote(request.getUserPrompt());
        entity.setDocumentIdsJson(JSON.toJSONString(request.getDocumentIds()));
        entity.setStatus(GenerationStatus.PENDING.name());
        entity.setStage(GenerationStage.PENDING.name());
        entity.setAllowGeneralKnowledge(Boolean.TRUE.equals(request.getAllowGeneralKnowledge()));
        entity.setAllowWebSearch(Boolean.TRUE.equals(request.getAllowWebSearch()));
        entity.setRequestedQuestionCount(questionCount(request));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        taskMapper.insert(entity);
    }

    @Override
    public void updateTaskStage(String taskId, GenerationStatus status, GenerationStage stage) {
        QaGenerationTaskEntity entity = requireTask(taskId);
        entity.setStatus(status.name());
        entity.setStage(stage.name());
        if (entity.getStartedAt() == null) {
            entity.setStartedAt(LocalDateTime.now());
        }
        taskMapper.updateById(entity);
    }

    @Override
    public void markTaskCompleted(String taskId, String qaSetId) {
        QaGenerationTaskEntity entity = requireTask(taskId);
        entity.setQaSetId(qaSetId);
        entity.setStatus(GenerationStatus.COMPLETED.name());
        entity.setStage(GenerationStage.COMPLETED.name());
        entity.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(entity);
    }

    @Override
    public void markTaskFailed(String taskId, ErrorType errorType, String errorMessage) {
        QaGenerationTaskEntity entity = requireTask(taskId);
        entity.setStatus(GenerationStatus.FAILED.name());
        entity.setStage(GenerationStage.FAILED.name());
        entity.setErrorCode(errorType.name());
        entity.setErrorMessage(errorMessage);
        entity.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(entity);
    }

    @Override
    public void appendTaskMessage(String taskId, GenerationStage stage, String message) {
        QaGenerationTaskMessageEntity entity = new QaGenerationTaskMessageEntity();
        entity.setId(UUID.randomUUID().toString());
        entity.setTaskId(taskId);
        entity.setStage(stage.name());
        entity.setMessage(message);
        entity.setCreatedAt(LocalDateTime.now());
        taskMessageMapper.insert(entity);
    }

    @Override
    public TaskStatusResponse getTaskStatus(String taskId, String userId) {
        QaGenerationTaskEntity entity = requireTask(taskId);
        checkUser(entity, userId);
        return TaskStatusResponse.builder()
                .taskId(entity.getId())
                .userId(entity.getUserId())
                .qaSetId(entity.getQaSetId())
                .status(entity.getStatus())
                .stage(entity.getStage())
                .errorCode(entity.getErrorCode())
                .errorMessage(entity.getErrorMessage())
                .requestedQuestionCount(entity.getRequestedQuestionCount())
                .createdAt(format(entity.getCreatedAt()))
                .startedAt(format(entity.getStartedAt()))
                .completedAt(format(entity.getCompletedAt()))
                .build();
    }

    @Override
    public List<TaskMessageResponse> getTaskMessages(String taskId, String userId) {
        QaGenerationTaskEntity task = requireTask(taskId);
        checkUser(task, userId);
        return taskMessageMapper.selectList(new LambdaQueryWrapper<QaGenerationTaskMessageEntity>()
                        .eq(QaGenerationTaskMessageEntity::getTaskId, taskId)
                        .orderByAsc(QaGenerationTaskMessageEntity::getCreatedAt))
                .stream()
                .map(entity -> TaskMessageResponse.builder()
                        .id(entity.getId())
                        .taskId(entity.getTaskId())
                        .stage(entity.getStage())
                        .message(entity.getMessage())
                        .createdAt(format(entity.getCreatedAt()))
                        .build())
                .toList();
    }

    @Override
    public UserLlmModelVO getUserLlmModel(String userId) {
        UserProfileEntity profile = userProfileMapper.selectById(userId);
        if (profile == null) {
            return null;
        }
        return new UserLlmModelVO(profile.getLlmBaseUrl(), profile.getLlmApiKey(), profile.getLlmModelName());
    }

    @Override
    public String getDocumentsSummary(List<String> documentIds, String userId) {
        if (documentIds == null || documentIds.isEmpty()) {
            return "";
        }
        List<SourceDocumentEntity> documents = sourceDocumentMapper.selectList(
                new LambdaQueryWrapper<SourceDocumentEntity>()
                        .in(SourceDocumentEntity::getId, documentIds)
                        .eq(SourceDocumentEntity::getUserId, userId)
                        .eq(SourceDocumentEntity::getDeleted, false));
        return documents.stream()
                .map(document -> "# " + document.getFileName() + "\n"
                        + safe(document.getSummary()) + "\n"
                        + safe(document.getNormalizedContent()) + "\n"
                        + safe(document.getRawContent()))
                .collect(Collectors.joining("\n\n"));
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public String saveGeneratedQaSet(String taskId, String userId, CreateTaskRequest request,
                                     PlanResult planResult, List<DraftItem> draftItems) {
        String qaSetId = UUID.randomUUID().toString();
        QaSetEntity qaSet = new QaSetEntity();
        qaSet.setId(qaSetId);
        qaSet.setUserId(userId);
        qaSet.setTaskId(taskId);
        qaSet.setTitle(planResult.title() == null || planResult.title().isBlank() ? title(request) : planResult.title());
        qaSet.setDescription(planResult.description());
        qaSet.setModuleTagsJson(JSON.toJSONString(moduleTags(draftItems)));
        qaSet.setQuestionCount(draftItems.size());
        qaSet.setPracticeCount(0);
        qaSetMapper.insert(qaSet);

        int sortOrder = 1;
        for (DraftItem draftItem : draftItems) {
            QaItemEntity item = new QaItemEntity();
            item.setId(UUID.randomUUID().toString());
            item.setQaSetId(qaSetId);
            item.setUserId(userId);
            item.setQuestion(draftItem.question());
            item.setKnowledgeNote(draftItem.knowledgeNote());
            item.setAnswer(draftItem.answer());
            item.setModuleTag(draftItem.moduleTag());
            item.setDifficulty(draftItem.difficulty() == null ? null : draftItem.difficulty().name());
            item.setConflictTip(draftItem.conflictTip());
            item.setSourceChunkIdsJson(JSON.toJSONString(draftItem.sourceChunkIds() == null
                    ? List.of() : draftItem.sourceChunkIds()));
            item.setSortOrder(sortOrder++);
            qaItemMapper.insert(item);
        }

        for (String documentId : request.getDocumentIds()) {
            QaSetDocumentRefEntity ref = new QaSetDocumentRefEntity();
            ref.setId(UUID.randomUUID().toString());
            ref.setQaSetId(qaSetId);
            ref.setDocumentId(documentId);
            ref.setCreatedAt(LocalDateTime.now());
            qaSetDocumentRefMapper.insert(ref);
        }
        return qaSetId;
    }

    private QaGenerationTaskEntity requireTask(String taskId) {
        QaGenerationTaskEntity entity = taskMapper.selectById(taskId);
        if (entity == null) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        return entity;
    }

    private void checkUser(QaGenerationTaskEntity entity, String userId) {
        if (!userId.equals(entity.getUserId())) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
    }

    private List<String> moduleTags(List<DraftItem> draftItems) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (DraftItem draftItem : draftItems) {
            if (draftItem.moduleTag() != null && !draftItem.moduleTag().isBlank()) {
                tags.add(draftItem.moduleTag());
            }
        }
        return new ArrayList<>(tags);
    }

    private String title(CreateTaskRequest request) {
        return request.getTitle() == null || request.getTitle().isBlank() ? "生成问答集" : request.getTitle();
    }

    private int questionCount(CreateTaskRequest request) {
        return request.getRequestedQuestionCount() == null ? 0 : request.getRequestedQuestionCount();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String format(LocalDateTime time) {
        return time == null ? null : time.toString().replace('T', ' ');
    }
}
