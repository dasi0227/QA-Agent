package com.dasi.qa.agent.infrastructure.repository;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.DraftResult;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.PlanResult;
import com.dasi.qa.agent.domain.agent.model.enumeration.ErrorType;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GeneratePhase;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GenerateStatus;
import com.dasi.qa.agent.domain.agent.model.vo.UserLlmModelVO;
import com.dasi.qa.agent.domain.agent.model.vo.UserProfileAllowVO;
import com.dasi.qa.agent.domain.agent.model.vo.UserProfileInfoVO;
import com.dasi.qa.agent.domain.agent.model.vo.UserProfileStyleVO;
import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.infrastructure.persistent.entity.DocumentChunk;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaGenerationTask;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaGenerationTaskMessage;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaItem;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaSetDocumentRef;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaSet;
import com.dasi.qa.agent.infrastructure.persistent.entity.SourceDocument;
import com.dasi.qa.agent.infrastructure.persistent.entity.UserProfile;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.DocumentChunkMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaGenerationTaskMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaGenerationTaskMessageMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaItemMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaSetDocumentRefMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaSetMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.SourceDocumentMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.UserProfileMapper;
import com.dasi.qa.agent.types.constant.RedisConstant;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
import com.dasi.qa.agent.types.dto.response.qa.TaskListItemResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskMessageResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskStatusResponse;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.result.ResultCode;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

@Repository
public class AgentRepository implements IAgentRepository {

    private final QaGenerationTaskMapper taskMapper;
    private final QaGenerationTaskMessageMapper taskMessageMapper;
    private final UserProfileMapper userProfileMapper;
    private final SourceDocumentMapper sourceDocumentMapper;
    private final QaSetMapper qaSetMapper;
    private final QaItemMapper qaItemMapper;
    private final QaSetDocumentRefMapper qaSetDocumentRefMapper;
    private final DocumentChunkMapper documentChunkMapper;

    public AgentRepository(QaGenerationTaskMapper taskMapper,
                           QaGenerationTaskMessageMapper taskMessageMapper,
                           UserProfileMapper userProfileMapper,
                           SourceDocumentMapper sourceDocumentMapper,
                           QaSetMapper qaSetMapper,
                           QaItemMapper qaItemMapper,
                           QaSetDocumentRefMapper qaSetDocumentRefMapper,
                           DocumentChunkMapper documentChunkMapper) {
        this.taskMapper = taskMapper;
        this.taskMessageMapper = taskMessageMapper;
        this.userProfileMapper = userProfileMapper;
        this.sourceDocumentMapper = sourceDocumentMapper;
        this.qaSetMapper = qaSetMapper;
        this.qaItemMapper = qaItemMapper;
        this.qaSetDocumentRefMapper = qaSetDocumentRefMapper;
        this.documentChunkMapper = documentChunkMapper;
    }

    @Override
    public void createGenerationTask(String taskId, String userId, CreateQaSetRequest request, UserProfileAllowVO allow) {
        LocalDateTime now = LocalDateTime.now();
        QaGenerationTask entity = new QaGenerationTask();
        entity.setId(taskId);
        entity.setUserId(userId);
        entity.setTitle(request.getTitle());
        entity.setUserPrompt(request.getUserPrompt());
        entity.setDocumentIdsJson(JSON.toJSONString(request.getDocumentIds()));
        entity.setStatus(GenerateStatus.PENDING.name());
        entity.setStage(GeneratePhase.INIT.getGenerateStage());
        entity.setAllowGeneralKnowledge(Boolean.TRUE.equals(allow.getAllowGeneralKnowledge()));
        entity.setAllowWebSearch(Boolean.TRUE.equals(allow.getAllowWebSearch()));
        entity.setRequestedQuestionCount(questionCount(request));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        taskMapper.insert(entity);
    }

    @Override
    public void updateTaskStatus(String taskId, GenerateStatus status, GeneratePhase phase) {
        QaGenerationTask entity = requireTask(taskId);
        entity.setStatus(status.name());
        entity.setStage(phase.getGenerateStage());
        if (entity.getStartedAt() == null) {
            entity.setStartedAt(LocalDateTime.now());
        }
        taskMapper.updateById(entity);
    }

    @Override
    public void markTaskCompleted(String taskId, String qaSetId) {
        QaGenerationTask entity = requireTask(taskId);
        entity.setQaSetId(qaSetId);
        entity.setStatus(GenerateStatus.SOLVED.name());
        entity.setStage(GeneratePhase.COMPLETE.getGenerateStage());
        entity.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(entity);
    }

    @Override
    public void markTaskFailed(String taskId, ErrorType errorType, String errorMessage) {
        QaGenerationTask entity = requireTask(taskId);
        entity.setStatus(GenerateStatus.UNSOLVED.name());
        entity.setStage(GeneratePhase.FAIL.getGenerateStage());
        entity.setErrorCode(errorType.name());
        entity.setErrorMessage(errorMessage);
        entity.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(entity);
    }

    @Override
    public void appendTaskMessage(String taskId, String userId, String stage, String message, String content) {
        QaGenerationTaskMessage entity = new QaGenerationTaskMessage();
        entity.setId(UUID.randomUUID().toString());
        entity.setUserId(userId);
        entity.setTaskId(taskId);
        entity.setStage(stage);
        entity.setMessage(message);
        entity.setContent(content);
        entity.setCreatedAt(LocalDateTime.now());
        taskMessageMapper.insert(entity);
    }

    @Override
    public TaskStatusResponse getTaskStatus(String taskId, String userId) {
        QaGenerationTask entity = requireTask(taskId);
        checkUser(entity, userId);
        String documentNamesJson = getDocumentNames(entity.getDocumentIdsJson());
        return TaskStatusResponse.builder()
                .taskId(entity.getId())
                .userId(entity.getUserId())
                .title(entity.getTitle())
                .userPrompt(entity.getUserPrompt())
                .documentIdsJson(entity.getDocumentIdsJson())
                .documentNamesJson(documentNamesJson)
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

    private String getDocumentNames(String documentIdsJson) {
        if (documentIdsJson == null || documentIdsJson.isBlank()) {
            return "[]";
        }
        List<String> ids = JSON.parseArray(documentIdsJson, String.class);
        if (ids == null || ids.isEmpty()) {
            return "[]";
        }
        return JSON.toJSONString(sourceDocumentMapper.selectList(
                        new LambdaQueryWrapper<SourceDocument>()
                                .in(SourceDocument::getId, ids)
                                .select(SourceDocument::getFileName))
                .stream()
                .map(SourceDocument::getFileName)
                .toList());
    }

    @Override
    public List<TaskMessageResponse> getTaskMessages(String taskId, String userId) {
        QaGenerationTask task = requireTask(taskId);
        checkUser(task, userId);
        return taskMessageMapper.selectList(new LambdaQueryWrapper<QaGenerationTaskMessage>()
                        .eq(QaGenerationTaskMessage::getTaskId, taskId)
                        .orderByAsc(QaGenerationTaskMessage::getCreatedAt))
                .stream()
                .map(entity -> TaskMessageResponse.builder()
                        .id(entity.getId())
                        .taskId(entity.getTaskId())
                        .stage(entity.getStage())
                        .message(entity.getMessage())
                        .content(entity.getContent())
                        .createdAt(format(entity.getCreatedAt()))
                        .build())
                .toList();
    }

    @Override
    public List<TaskListItemResponse> getTaskList(String userId) {
        return taskMapper.selectList(new LambdaQueryWrapper<QaGenerationTask>()
                        .eq(QaGenerationTask::getUserId, userId)
                        .orderByDesc(QaGenerationTask::getCreatedAt))
                .stream()
                .map(entity -> TaskListItemResponse.builder()
                        .taskId(entity.getId())
                        .title(entity.getTitle())
                        .status(entity.getStatus())
                        .stage(entity.getStage())
                        .qaSetId(entity.getQaSetId())
                        .createdAt(format(entity.getCreatedAt()))
                        .build())
                .toList();
    }

    @Override
    public UserLlmModelVO getUserLlmModel(String userId) {
        UserProfile profile = userProfileMapper.selectById(userId);
        if (profile == null) {
            return null;
        }
        return new UserLlmModelVO(profile.getLlmBaseUrl(), profile.getLlmApiKey(), profile.getLlmModelName());
    }

    @Override
    public UserProfileInfoVO getUserProfileInfo(String userId) {
        UserProfile profile = userProfileMapper.selectById(userId);
        if (profile == null) {
            return null;
        }
        return new UserProfileInfoVO(
                profile.getTargetRole(),
                profile.getTargetDomain(),
                profile.getTargetCompany(),
                profile.getMajor(),
                profile.getGrade(),
                profile.getStage()
        );
    }

    @Override
    public UserProfileStyleVO getUserProfileStyle(String userId) {
        UserProfile profile = userProfileMapper.selectById(userId);
        if (profile == null) {
            return null;
        }
        return new UserProfileStyleVO(
                profile.getAnswerStyle(),
                profile.getFeedbackStyle()
        );
    }

    @Override
    public UserProfileAllowVO getUserProfileAllow(String userId) {
        UserProfile profile = userProfileMapper.selectById(userId);
        if (profile == null) {
            return null;
        }
        return new UserProfileAllowVO(
                profile.getAllowGeneralKnowledge(),
                profile.getAllowWebSearch(),
                profile.getAllowFallback()
        );
    }

    @Override
    public String getDocumentsSummary(List<String> documentIds, String userId) {
        if (documentIds == null || documentIds.isEmpty()) {
            return "";
        }
        List<SourceDocument> documents = sourceDocumentMapper.selectList(
                new LambdaQueryWrapper<SourceDocument>()
                        .in(SourceDocument::getId, documentIds)
                        .eq(SourceDocument::getUserId, userId)
                        .eq(SourceDocument::getDeleted, false));
        StringBuilder sb = new StringBuilder();
        for (SourceDocument doc : documents) {
            sb.append("# ").append(doc.getFileName()).append("\n");
            List<DocumentChunk> chunks = documentChunkMapper.selectList(
                    new LambdaQueryWrapper<DocumentChunk>()
                            .eq(DocumentChunk::getDocumentId, doc.getId())
                            .orderByAsc(DocumentChunk::getChunkIndex));
            for (DocumentChunk chunk : chunks) {
                if (chunk.getSummary() != null && !chunk.getSummary().isBlank()) {
                    sb.append(chunk.getSummary()).append("\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString().trim();
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    @CacheEvict(cacheNames = {RedisConstant.QA_SET_CACHE, RedisConstant.QA_ITEM_CACHE}, allEntries = true)
    public String saveGeneratedQaSet(String taskId, String userId, CreateQaSetRequest request,
                                     PlanResult planResult, List<DraftResult> draftResults) {
        String qaSetId = UUID.randomUUID().toString();
        QaSet qaSet = new QaSet();
        qaSet.setId(qaSetId);
        qaSet.setUserId(userId);
        qaSet.setTaskId(taskId);
        qaSet.setTitle(title(request, planResult));
        qaSet.setDescription(planResult.getDescription());
        qaSet.setModuleTagsJson(JSON.toJSONString(moduleTags(draftResults)));
        qaSet.setQuestionCount(draftResults.size());
        qaSet.setPracticeCount(0);
        qaSetMapper.insert(qaSet);

        int sortOrder = 1;
        for (DraftResult draftResult : draftResults) {
            QaItem item = new QaItem();
            item.setId(UUID.randomUUID().toString());
            item.setQaSetId(qaSetId);
            item.setUserId(userId);
            item.setQuestion(draftResult.getQuestion());
            item.setKnowledgeNote(draftResult.getKnowledgeNote());
            item.setAnswer(draftResult.getAnswer());
            item.setModuleTag(draftResult.getTag());
            item.setDifficulty(draftResult.getDifficulty());
            item.setTip(draftResult.getTip());
            item.setSourceChunkIdsJson(JSON.toJSONString(draftResult.getSourceChunkIds() != null ? draftResult.getSourceChunkIds() : List.of()));
            item.setSortOrder(sortOrder++);
            qaItemMapper.insert(item);
        }

        for (String documentId : request.getDocumentIds()) {
            QaSetDocumentRef ref = new QaSetDocumentRef();
            ref.setId(UUID.randomUUID().toString());
            ref.setQaSetId(qaSetId);
            ref.setDocumentId(documentId);
            ref.setCreatedAt(LocalDateTime.now());
            qaSetDocumentRefMapper.insert(ref);
            sourceDocumentMapper.update(null,
                    new LambdaUpdateWrapper<SourceDocument>()
                            .setSql("reference_count = reference_count + 1")
                            .eq(SourceDocument::getId, documentId));
        }
        return qaSetId;
    }

    private QaGenerationTask requireTask(String taskId) {
        QaGenerationTask entity = taskMapper.selectById(taskId);
        if (entity == null) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        return entity;
    }

    private void checkUser(QaGenerationTask entity, String userId) {
        if (!userId.equals(entity.getUserId())) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
    }

    private List<String> moduleTags(List<DraftResult> draftResults) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (DraftResult draftResult : draftResults) {
            if (draftResult.getTag() != null && !draftResult.getTag().isBlank()) {
                for (String tag : draftResult.getTag().split(",")) {
                    String trimmed = tag.trim();
                    if (!trimmed.isEmpty()) {
                        tags.add(trimmed);
                    }
                }
            }
        }
        return new ArrayList<>(tags);
    }

    private String title(CreateQaSetRequest request, PlanResult planResult) {
        boolean usePlanTitle = "未命名问答集".equals(request.getTitle())
                && planResult != null && planResult.getTitle() != null && !planResult.getTitle().isBlank();
        return usePlanTitle ? planResult.getTitle() : request.getTitle();
    }

    private int questionCount(CreateQaSetRequest request) {
        return request.getRequestedQuestionCount() == null ? 0 : request.getRequestedQuestionCount();
    }

    private String format(LocalDateTime time) {
        return time == null ? null : time.toString().replace('T', ' ');
    }
}
