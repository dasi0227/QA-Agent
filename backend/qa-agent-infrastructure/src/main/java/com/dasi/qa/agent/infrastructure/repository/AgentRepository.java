package com.dasi.qa.agent.infrastructure.repository;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.dasi.qa.agent.domain.agent.service.assess.model.command.AssessSaveCommand;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessItemDetail;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.SessionContext;
import com.dasi.qa.agent.domain.agent.service.assist.model.context.AssistContext;
import com.dasi.qa.agent.domain.agent.service.assist.model.result.AssistResult;
import com.dasi.qa.agent.domain.agent.service.complete.model.context.CompleteContext;
import com.dasi.qa.agent.domain.agent.service.complete.model.result.CompleteResult;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.DraftResult;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.GeneratedQaSetSaveResult;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.PlanResult;
import com.dasi.qa.agent.domain.qa.model.enumeration.CompleteStatus;
import com.dasi.qa.agent.types.enumeration.AgentErrorType;
import com.dasi.qa.agent.domain.agent.service.feedback.model.command.FeedbackSaveCommand;
import com.dasi.qa.agent.domain.agent.model.vo.PracticeVO;
import com.dasi.qa.agent.domain.agent.service.memory.model.vo.SessionSource;
import com.dasi.qa.agent.domain.agent.service.memory.model.vo.SessionSource.SessionSourceItem;
import com.dasi.qa.agent.domain.agent.service.memory.model.dto.Memory;
import com.dasi.qa.agent.domain.agent.service.memory.model.dto.MemoryEvidence;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GeneratePhase;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GenerateStatus;
import com.dasi.qa.agent.domain.agent.model.vo.UserLlmModelVO;
import com.dasi.qa.agent.domain.agent.model.vo.UserProfileAllowVO;
import com.dasi.qa.agent.domain.agent.model.vo.UserProfileInfoVO;
import com.dasi.qa.agent.domain.agent.model.vo.UserProfileStyleVO;
import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.util.IIdUtil;
import com.dasi.qa.agent.infrastructure.persistent.entity.DocumentChunk;
import com.dasi.qa.agent.infrastructure.persistent.entity.PracticeSession;
import com.dasi.qa.agent.infrastructure.persistent.entity.PracticeSessionItem;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaGenerationTask;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaGenerationTaskMessage;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaItem;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaSetDocumentRef;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaSet;
import com.dasi.qa.agent.infrastructure.persistent.entity.SourceDocument;
import com.dasi.qa.agent.infrastructure.persistent.entity.UserMemory;
import com.dasi.qa.agent.infrastructure.persistent.entity.UserMemoryEvidence;
import com.dasi.qa.agent.infrastructure.persistent.entity.UserProfile;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.DocumentChunkMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.PracticeSessionItemMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.PracticeSessionMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaGenerationTaskMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaGenerationTaskMessageMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaItemMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaSetDocumentRefMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaSetMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.SourceDocumentMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.UserMemoryEvidenceMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.UserMemoryMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.UserProfileMapper;
import com.dasi.qa.agent.types.constant.RedisConstant;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
import com.dasi.qa.agent.types.dto.response.practice.AssessResponse;
import com.dasi.qa.agent.types.dto.response.practice.JudgeDetail;
import com.dasi.qa.agent.domain.agent.model.vo.ChunkVO;
import com.dasi.qa.agent.types.dto.response.qa.TaskListItemResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskMessageResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskStatusResponse;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

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
    private final PracticeSessionMapper practiceSessionMapper;
    private final PracticeSessionItemMapper practiceSessionItemMapper;
    private final UserMemoryMapper userMemoryMapper;
    private final UserMemoryEvidenceMapper userMemoryEvidenceMapper;
    private final IIdUtil idUtil;

    public AgentRepository(QaGenerationTaskMapper taskMapper,
                           QaGenerationTaskMessageMapper taskMessageMapper,
                           UserProfileMapper userProfileMapper,
                           SourceDocumentMapper sourceDocumentMapper,
                           QaSetMapper qaSetMapper,
                           QaItemMapper qaItemMapper,
                           QaSetDocumentRefMapper qaSetDocumentRefMapper,
                           DocumentChunkMapper documentChunkMapper,
                           PracticeSessionMapper practiceSessionMapper,
                           PracticeSessionItemMapper practiceSessionItemMapper,
                           UserMemoryMapper userMemoryMapper,
                           UserMemoryEvidenceMapper userMemoryEvidenceMapper,
                           IIdUtil idUtil) {
        this.taskMapper = taskMapper;
        this.taskMessageMapper = taskMessageMapper;
        this.userProfileMapper = userProfileMapper;
        this.sourceDocumentMapper = sourceDocumentMapper;
        this.qaSetMapper = qaSetMapper;
        this.qaItemMapper = qaItemMapper;
        this.qaSetDocumentRefMapper = qaSetDocumentRefMapper;
        this.documentChunkMapper = documentChunkMapper;
        this.practiceSessionMapper = practiceSessionMapper;
        this.practiceSessionItemMapper = practiceSessionItemMapper;
        this.userMemoryMapper = userMemoryMapper;
        this.userMemoryEvidenceMapper = userMemoryEvidenceMapper;
        this.idUtil = idUtil;
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
        entity.setAllowReferMemory(Boolean.TRUE.equals(allow.getAllowReferMemory()));
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
    public void markTaskFailed(String taskId, AgentErrorType agentErrorType, String errorMessage) {
        QaGenerationTask entity = requireTask(taskId);
        entity.setStatus(GenerateStatus.UNSOLVED.name());
        entity.setStage(GeneratePhase.FAIL.getGenerateStage());
        entity.setErrorCode(agentErrorType.name());
        entity.setErrorMessage(errorMessage);
        entity.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(entity);
    }

    @Override
    public void markTaskCanceled(String taskId) {
        QaGenerationTask entity = requireTask(taskId);
        entity.setStatus(GenerateStatus.CANCELED.name());
        entity.setStage(GeneratePhase.FAIL.getGenerateStage());
        entity.setCompletedAt(LocalDateTime.now());
        taskMapper.updateById(entity);
    }

    @Override
    public void appendTaskMessage(String taskId, String userId, String stage, String message, String content) {
        QaGenerationTaskMessage entity = new QaGenerationTaskMessage();
        entity.setId(idUtil.nextId());
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
        if (!StringUtils.hasText(documentIdsJson)) {
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
                profile.getAllowReferMemory(),
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
                if (StringUtils.hasText(chunk.getSummary())) {
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
    public GeneratedQaSetSaveResult saveGeneratedQaSet(String taskId, String userId, CreateQaSetRequest request,
                                                       PlanResult planResult, List<DraftResult> draftResults) {
        String qaSetId = idUtil.nextId();
        List<String> qaItemIds = new ArrayList<>();
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
            item.setId(idUtil.nextId());
            item.setQaSetId(qaSetId);
            item.setUserId(userId);
            item.setQuestion(draftResult.getQuestion());
            item.setKnowledgeNote(draftResult.getKnowledgeNote());
            item.setAnswer(draftResult.getAnswer());
            item.setModuleTag(draftResult.getTag());
            item.setDifficulty(draftResult.getDifficulty());
            item.setKeywords("");
            item.setHint("");
            item.setSourceReliable(draftResult.getSourceReliable() == null ? Boolean.FALSE : draftResult.getSourceReliable());
            item.setSourceChunkIdsJson(JSON.toJSONString(draftResult.getSourceChunkIds() != null ? draftResult.getSourceChunkIds() : List.of()));
            item.setCompleteStatus(CompleteStatus.SOLVED.name());
            item.setSortOrder(sortOrder++);
            qaItemMapper.insert(item);
            qaItemIds.add(item.getId());
        }

        for (String documentId : request.getDocumentIds()) {
            QaSetDocumentRef ref = new QaSetDocumentRef();
            ref.setId(idUtil.nextId());
            ref.setQaSetId(qaSetId);
            ref.setDocumentId(documentId);
            ref.setCreatedAt(LocalDateTime.now());
            qaSetDocumentRefMapper.insert(ref);
            sourceDocumentMapper.update(null,
                    new LambdaUpdateWrapper<SourceDocument>()
                            .setSql("reference_count = reference_count + 1")
                            .eq(SourceDocument::getId, documentId));
        }
        return new GeneratedQaSetSaveResult(qaSetId, qaItemIds);
    }

    @Override
    public PracticeVO getPracticeVO(String sessionItemId, String userId) {
        PracticeSessionItem sessionItem = requirePracticeSessionItem(sessionItemId);
        PracticeSession session = requirePracticeSession(sessionItem.getSessionId());
        if (!userId.equals(session.getUserId())) {
            throw new ApiException(ResultCode.NOT_FOUND, "练习记录不存在");
        }
        QaItem qaItem = qaItemMapper.selectById(sessionItem.getQaItemId());
        if (qaItem == null || !userId.equals(qaItem.getUserId())) {
            throw new ApiException(ResultCode.NOT_FOUND, "题目不存在");
        }
        UserProfileStyleVO style = getUserProfileStyle(userId);
        List<ChunkVO> sourceChunks = feedbackSourceChunks(qaItem.getSourceChunkIdsJson(), userId);
        return PracticeVO.builder()
                .sessionItemId(sessionItem.getId())
                .sessionId(sessionItem.getSessionId())
                .qaItemId(qaItem.getId())
                .question(qaItem.getQuestion())
                .standardAnswer(qaItem.getAnswer())
                .knowledgeNote(qaItem.getKnowledgeNote())
                .sourceReliable(Boolean.TRUE.equals(qaItem.getSourceReliable()))
                .answerStyle(style == null ? "" : style.getAnswerStyle())
                .feedbackStyle(style == null ? "" : style.getFeedbackStyle())
                .sourceChunks(sourceChunks)
                .build();
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    @CacheEvict(cacheNames = {RedisConstant.PRACTICE_SESSION_CACHE, RedisConstant.PRACTICE_SESSION_ITEM_CACHE}, allEntries = true)
    public LocalDateTime saveFeedbackResult(String sessionItemId, String userId, FeedbackSaveCommand command) {
        PracticeSessionItem sessionItem = requirePracticeSessionItem(sessionItemId);
        PracticeSession session = requirePracticeSession(sessionItem.getSessionId());
        if (!userId.equals(session.getUserId())) {
            throw new ApiException(ResultCode.NOT_FOUND, "练习记录不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        practiceSessionItemMapper.update(null,
                new LambdaUpdateWrapper<PracticeSessionItem>()
                        .set(PracticeSessionItem::getUserAnswer, command.getUserAnswer())
                        .set(PracticeSessionItem::getStatus, "SUBMITTED")
                        .set(PracticeSessionItem::getUnknown, Boolean.TRUE.equals(command.getUnknown()))
                        .set(PracticeSessionItem::getResult, command.getResult().name())
                        .set(PracticeSessionItem::getScore, command.getScore())
                        .set(PracticeSessionItem::getFeedbackSummary, command.getFeedbackSummary())
                        .set(PracticeSessionItem::getFeedbackJudgeDetail, command.getJudgeDetail() != null ? JSON.toJSONString(command.getJudgeDetail()) : null)
                        .set(PracticeSessionItem::getFeedbackHintDetail, command.getHintDetail() != null ? JSON.toJSONString(command.getHintDetail()) : null)
                        .set(PracticeSessionItem::getAnsweredAt, now)
                        .set(PracticeSessionItem::getSubmittedAt, now)
                        .set(PracticeSessionItem::getUpdatedAt, now)
                        .eq(PracticeSessionItem::getId, sessionItemId));
        if (sessionItem.getAnsweredAt() == null) {
            practiceSessionMapper.update(null,
                    new LambdaUpdateWrapper<PracticeSession>()
                            .setSql("answered_count = answered_count + 1")
                            .set(PracticeSession::getUpdatedAt, now)
                            .eq(PracticeSession::getId, session.getId()));
        }
        return now;
    }

    @Override
    public SessionContext getAssessContext(String sessionId, String userId) {
        PracticeSession session = requirePracticeSession(sessionId);
        if (!userId.equals(session.getUserId())) {
            throw new ApiException(ResultCode.NOT_FOUND, "练习记录不存在");
        }
        QaSet qaSet = qaSetMapper.selectById(session.getQaSetId());
        if (qaSet == null || !userId.equals(qaSet.getUserId())) {
            throw new ApiException(ResultCode.NOT_FOUND, "题集不存在");
        }
        List<PracticeSessionItem> sessionItems = practiceSessionItemMapper.selectList(
                new LambdaQueryWrapper<PracticeSessionItem>()
                        .eq(PracticeSessionItem::getSessionId, sessionId)
                        .orderByAsc(PracticeSessionItem::getSortOrder));
        List<AssessItemDetail> items = new ArrayList<>();
        for (PracticeSessionItem sessionItem : sessionItems) {
            QaItem qaItem = qaItemMapper.selectById(sessionItem.getQaItemId());
            if (qaItem == null || !userId.equals(qaItem.getUserId())) {
                throw new ApiException(ResultCode.NOT_FOUND, "题目不存在");
            }
            JudgeDetail judgeDetail = judgeDetail(sessionItem.getFeedbackJudgeDetail());
            items.add(AssessItemDetail.builder()
                    .itemId(sessionItem.getId())
                    .question(qaItem.getQuestion())
                    .moduleTag(qaItem.getModuleTag())
                    .difficulty(qaItem.getDifficulty())
                    .standardAnswer(qaItem.getAnswer())
                    .userAnswer(sessionItem.getUserAnswer())
                    .result(sessionItem.getResult())
                    .score(sessionItem.getScore())
                    .feedbackSummary(sessionItem.getFeedbackSummary())
                    .missingPoints(judgeDetail == null || judgeDetail.getMissingPoints() == null ? List.of() : judgeDetail.getMissingPoints())
                    .wrongPoints(judgeDetail == null || judgeDetail.getWrongPoints() == null ? List.of() : judgeDetail.getWrongPoints())
                    .answeredAt(sessionItem.getAnsweredAt())
                    .build());
        }
        return SessionContext.builder()
                .sessionId(session.getId())
                .qaSetId(session.getQaSetId())
                .qaSetTitle(qaSet.getTitle())
                .totalQuestions(session.getTotalQuestions())
                .items(items)
                .build();
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    @CacheEvict(cacheNames = {RedisConstant.PRACTICE_SESSION_CACHE, RedisConstant.QA_SET_CACHE}, allEntries = true)
    public AssessResponse saveAssessResult(String sessionId, String userId, AssessSaveCommand command) {
        PracticeSession session = requirePracticeSession(sessionId);
        if (!userId.equals(session.getUserId())) {
            throw new ApiException(ResultCode.NOT_FOUND, "练习记录不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime finishedAt = session.getFinishedAt() == null ? now : session.getFinishedAt();
        String assessmentDetailJson = JSON.toJSONString(command.getAssessDetail());

        practiceSessionMapper.update(null,
                new LambdaUpdateWrapper<PracticeSession>()
                        .set(PracticeSession::getStatus, "FINISHED")
                        .set(PracticeSession::getScore, command.getScore())
                        .set(PracticeSession::getAccuracy, command.getAccuracy())
                        .set(PracticeSession::getPerfectCount, command.getPerfectCount())
                        .set(PracticeSession::getCorrectCount, command.getCorrectCount())
                        .set(PracticeSession::getDeficientCount, command.getDeficientCount())
                        .set(PracticeSession::getWrongCount, command.getWrongCount())
                        .set(PracticeSession::getUnknownCount, command.getUnknownCount())
                        .set(PracticeSession::getSummary, command.getAssessDetail().getOverallComment())
                        .set(PracticeSession::getAssessmentDetailJson, assessmentDetailJson)
                        .set(PracticeSession::getFinishedAt, finishedAt)
                        .set(PracticeSession::getUpdatedAt, now)
                        .eq(PracticeSession::getId, sessionId)
                        .eq(PracticeSession::getUserId, userId));
        refreshQaSetPracticeStats(session.getQaSetId(), now);
        return AssessResponse.builder()
                .sessionId(session.getId())
                .qaSetId(session.getQaSetId())
                .score(command.getScore())
                .accuracy(command.getAccuracy())
                .perfectCount(command.getPerfectCount())
                .correctCount(command.getCorrectCount())
                .deficientCount(command.getDeficientCount())
                .wrongCount(command.getWrongCount())
                .unknownCount(command.getUnknownCount())
                .summary(command.getAssessDetail().getOverallComment())
                .assessDetail(command.getAssessDetail())
                .finishedAt(finishedAt)
                .build();
    }

    @Override
    public AssistContext getAssistContext(String qaItemId, String userId) {
        QaItem qaItem = requireQaItem(qaItemId, userId);
        UserProfileStyleVO style = getUserProfileStyle(userId);
        return AssistContext.builder()
                .qaItemId(qaItem.getId())
                .question(qaItem.getQuestion())
                .standardAnswer(qaItem.getAnswer())
                .knowledgeNote(qaItem.getKnowledgeNote())
                .moduleTag(qaItem.getModuleTag())
                .difficulty(qaItem.getDifficulty())
                .sourceReliable(Boolean.TRUE.equals(qaItem.getSourceReliable()))
                .answerStyle(style == null ? "" : style.getAnswerStyle())
                .sourceChunks(feedbackSourceChunks(qaItem.getSourceChunkIdsJson(), userId))
                .build();
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    @CacheEvict(cacheNames = RedisConstant.QA_ITEM_CACHE, allEntries = true)
    public void saveAssistResult(String qaItemId, String userId, AssistResult result) {
        requireQaItem(qaItemId, userId);
        qaItemMapper.update(null,
                new LambdaUpdateWrapper<QaItem>()
                        .set(QaItem::getKeywords, result == null ? "" : result.getKeywords())
                        .set(QaItem::getHint, result == null ? "" : result.getHint())
                        .set(QaItem::getUpdatedAt, LocalDateTime.now())
                        .eq(QaItem::getId, qaItemId)
                        .eq(QaItem::getUserId, userId));
    }

    @Override
    public CompleteContext getCompleteContext(String qaItemId, String userId) {
        QaItem qaItem = requireQaItem(qaItemId, userId);
        UserProfileInfoVO profile = getUserProfileInfo(userId);
        UserProfileStyleVO style = getUserProfileStyle(userId);
        return CompleteContext.builder()
                .qaItemId(qaItem.getId())
                .question(qaItem.getQuestion())
                .answer(qaItem.getAnswer())
                .documentIds(qaSetDocumentIds(qaItem.getQaSetId()))
                .userProfile(profile)
                .answerStyle(style == null ? "" : style.getAnswerStyle())
                .build();
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    @CacheEvict(cacheNames = RedisConstant.QA_ITEM_CACHE, allEntries = true)
    public void saveCompleteResult(String qaItemId, String userId, CompleteResult result) {
        QaItem qaItem = requireQaItem(qaItemId, userId);
        if (result == null) {
            markQaItemCompleteFailed(qaItemId, userId);
            return;
        }
        qaItem.setAnswer(result.getAnswer());
        qaItem.setKnowledgeNote(result.getKnowledgeNote());
        qaItem.setModuleTag(result.getModuleTag());
        qaItem.setDifficulty(result.getDifficulty());
        qaItem.setSourceChunkIdsJson(result.getSourceChunkIds() != null && !result.getSourceChunkIds().isEmpty()
                ? JSON.toJSONString(result.getSourceChunkIds()) : "[]");
        qaItem.setSourceReliable(Boolean.TRUE.equals(result.getSourceReliable()));
        qaItem.setCompleteStatus(CompleteStatus.SOLVED.name());
        qaItem.setUpdatedAt(LocalDateTime.now());
        qaItemMapper.updateById(qaItem);
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    @CacheEvict(cacheNames = RedisConstant.QA_ITEM_CACHE, allEntries = true)
    public void markQaItemCompleteFailed(String qaItemId, String userId) {
        qaItemMapper.update(null,
                new LambdaUpdateWrapper<QaItem>()
                        .set(QaItem::getCompleteStatus, CompleteStatus.UNSOLVED.name())
                        .set(QaItem::getUpdatedAt, LocalDateTime.now())
                        .eq(QaItem::getId, qaItemId)
                        .eq(QaItem::getUserId, userId));
    }

    @Override
    public SessionSource getInvestContext(String sessionId, String userId) {
        PracticeSession session = requirePracticeSession(sessionId);
        if (!userId.equals(session.getUserId())) {
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
        List<SessionSourceItem> items = sessionItems.stream()
                .map(item -> toMemoryIngestItem(item, userId))
                .toList();
        return SessionSource.builder()
                .sessionId(session.getId())
                .userId(session.getUserId())
                .qaSetId(session.getQaSetId())
                .items(items)
                .build();
    }

    @Override
    public Memory findMemoryByKey(String userId, String memoryType, String targetType, String targetKey) {
        UserMemory entity = userMemoryMapper.selectOne(
                new LambdaQueryWrapper<UserMemory>()
                        .eq(UserMemory::getUserId, userId)
                        .eq(UserMemory::getMemoryType, memoryType)
                        .eq(UserMemory::getTargetType, targetType)
                        .eq(UserMemory::getTargetKey, targetKey));
        return entity == null ? null : toAgentMemory(entity);
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void createMemory(Memory memory) {
        userMemoryMapper.insert(toUserMemoryEntity(memory));
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void updateMemory(Memory memory) {
        userMemoryMapper.updateById(toUserMemoryEntity(memory));
    }

    @Override
    public boolean existsMemoryEvidence(String memoryId, String sessionItemId) {
        return userMemoryEvidenceMapper.selectCount(
                new LambdaQueryWrapper<UserMemoryEvidence>()
                        .eq(UserMemoryEvidence::getMemoryId, memoryId)
                        .eq(UserMemoryEvidence::getSessionItemId, sessionItemId)) > 0;
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void createMemoryEvidence(MemoryEvidence evidence) {
        userMemoryEvidenceMapper.insert(UserMemoryEvidence.builder()
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
                .evidenceSummary(evidence.getEvidenceSummary())
                .build());
    }

    private QaGenerationTask requireTask(String taskId) {
        QaGenerationTask entity = taskMapper.selectById(taskId);
        if (entity == null) {
            throw new ApiException(ResultCode.NOT_FOUND, "生成任务不存在");
        }
        return entity;
    }

    private void checkUser(QaGenerationTask entity, String userId) {
        if (!userId.equals(entity.getUserId())) {
            throw new ApiException(ResultCode.NOT_FOUND, "生成任务不存在");
        }
    }

    private PracticeSessionItem requirePracticeSessionItem(String sessionItemId) {
        PracticeSessionItem entity = practiceSessionItemMapper.selectById(sessionItemId);
        if (entity == null) {
            throw new ApiException(ResultCode.NOT_FOUND, "练习题目不存在");
        }
        return entity;
    }

    private PracticeSession requirePracticeSession(String sessionId) {
        PracticeSession entity = practiceSessionMapper.selectById(sessionId);
        if (entity == null) {
            throw new ApiException(ResultCode.NOT_FOUND, "练习记录不存在");
        }
        return entity;
    }

    private QaItem requireQaItem(String qaItemId, String userId) {
        QaItem entity = qaItemMapper.selectById(qaItemId);
        if (entity == null) {
            throw new ApiException(ResultCode.NOT_FOUND, "题目不存在");
        }
        if (!userId.equals(entity.getUserId())) {
            throw new ApiException(ResultCode.NOT_FOUND, "题目不存在");
        }
        return entity;
    }

    private List<String> qaSetDocumentIds(String qaSetId) {
        return qaSetDocumentRefMapper.selectList(new LambdaQueryWrapper<QaSetDocumentRef>()
                        .eq(QaSetDocumentRef::getQaSetId, qaSetId))
                .stream()
                .map(QaSetDocumentRef::getDocumentId)
                .toList();
    }

    private JudgeDetail judgeDetail(String feedbackJudgeDetail) {
        if (!StringUtils.hasText(feedbackJudgeDetail)) {
            return null;
        }
        try {
            return JSON.parseObject(feedbackJudgeDetail, JudgeDetail.class);
        } catch (Exception exception) {
            return null;
        }
    }

    private SessionSourceItem toMemoryIngestItem(PracticeSessionItem item, String userId) {
        QaItem qaItem = qaItemMapper.selectById(item.getQaItemId());
        if (qaItem == null || !userId.equals(qaItem.getUserId())) {
            throw new ApiException(ResultCode.NOT_FOUND, "题目不存在");
        }
        JudgeDetail detail = judgeDetail(item.getFeedbackJudgeDetail());
        return SessionSourceItem.builder()
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
                .missingPointsJson(detail == null || detail.getMissingPoints() == null ? "[]" : JSON.toJSONString(detail.getMissingPoints()))
                .wrongPointsJson(detail == null || detail.getWrongPoints() == null ? "[]" : JSON.toJSONString(detail.getWrongPoints()))
                .sourceChunkIdsJson(StringUtils.hasText(item.getSourceChunkIdsSnapshotJson()) ? item.getSourceChunkIdsSnapshotJson() : "[]")
                .build();
    }

    private Memory toAgentMemory(UserMemory entity) {
        return Memory.builder()
                .id(entity.getId())
                .userId(entity.getUserId())
                .memoryType(entity.getMemoryType())
                .targetType(entity.getTargetType())
                .targetKey(entity.getTargetKey())
                .content(entity.getContent())
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

    private UserMemory toUserMemoryEntity(Memory memory) {
        return UserMemory.builder()
                .id(memory.getId())
                .userId(memory.getUserId())
                .memoryType(memory.getMemoryType())
                .targetType(memory.getTargetType())
                .targetKey(memory.getTargetKey())
                .content(memory.getContent())
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

    private void refreshQaSetPracticeStats(String qaSetId, LocalDateTime now) {
        List<PracticeSession> sessions = practiceSessionMapper.selectList(
                new LambdaQueryWrapper<PracticeSession>()
                        .eq(PracticeSession::getQaSetId, qaSetId)
                        .eq(PracticeSession::getStatus, "FINISHED"));
        int practiceCount = sessions.size();
        Integer averageScore = null;
        Integer bestScore = null;
        BigDecimal averageAccuracy = null;
        BigDecimal bestAccuracy = null;
        LocalDateTime lastPracticedAt = null;
        if (!sessions.isEmpty()) {
            List<PracticeSession> scoredSessions = sessions.stream()
                    .filter(session -> session.getScore() != null && session.getAccuracy() != null)
                    .toList();
            if (!scoredSessions.isEmpty()) {
                averageScore = Math.round((float) scoredSessions.stream()
                        .mapToInt(PracticeSession::getScore)
                        .sum() / scoredSessions.size());
                bestScore = scoredSessions.stream()
                        .map(PracticeSession::getScore)
                        .max(Integer::compareTo)
                        .orElse(null);
                averageAccuracy = scoredSessions.stream()
                        .map(PracticeSession::getAccuracy)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(scoredSessions.size()), 2, RoundingMode.HALF_UP);
                bestAccuracy = scoredSessions.stream()
                        .map(PracticeSession::getAccuracy)
                        .max(BigDecimal::compareTo)
                        .orElse(null);
            }
            lastPracticedAt = sessions.stream()
                    .map(PracticeSession::getFinishedAt)
                    .filter(time -> time != null)
                    .max(LocalDateTime::compareTo)
                    .orElse(null);
        }
        qaSetMapper.update(null,
                new LambdaUpdateWrapper<QaSet>()
                        .set(QaSet::getPracticeCount, practiceCount)
                        .set(QaSet::getAverageScore, averageScore)
                        .set(QaSet::getBestScore, bestScore)
                        .set(QaSet::getAverageAccuracy, averageAccuracy)
                        .set(QaSet::getBestAccuracy, bestAccuracy)
                        .set(QaSet::getLastPracticedAt, lastPracticedAt)
                        .set(QaSet::getUpdatedAt, now)
                        .eq(QaSet::getId, qaSetId));
    }

    private List<ChunkVO> feedbackSourceChunks(String sourceChunkIdsJson, String userId) {
        List<String> chunkIds = parseChunkIds(sourceChunkIdsJson);
        if (chunkIds.isEmpty()) {
            return List.of();
        }
        List<DocumentChunk> chunks = documentChunkMapper.selectList(
                new LambdaQueryWrapper<DocumentChunk>()
                        .in(DocumentChunk::getId, chunkIds)
                        .eq(DocumentChunk::getUserId, userId));
        Map<String, DocumentChunk> chunkMap = new LinkedHashMap<>();
        for (DocumentChunk chunk : chunks) {
            chunkMap.put(chunk.getId(), chunk);
        }
        List<ChunkVO> results = new ArrayList<>();
        for (String chunkId : chunkIds) {
            DocumentChunk chunk = chunkMap.get(chunkId);
            if (chunk != null) {
                results.add(ChunkVO.builder()
                        .chunkId(chunk.getId())
                        .documentId(chunk.getDocumentId())
                        .titlePath(chunk.getHeadingPath())
                        .summary(chunk.getSummary())
                        .content(chunk.getContent())
                        .build());
            }
        }
        return results;
    }

    private List<String> parseChunkIds(String sourceChunkIdsJson) {
        if (!StringUtils.hasText(sourceChunkIdsJson)) {
            return List.of();
        }
        try {
            return JSON.parseArray(sourceChunkIdsJson, String.class);
        } catch (Exception exception) {
            return List.of();
        }
    }

    private List<String> moduleTags(List<DraftResult> draftResults) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (DraftResult draftResult : draftResults) {
            if (StringUtils.hasText(draftResult.getTag())) {
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
                && planResult != null && StringUtils.hasText(planResult.getTitle());
        return usePlanTitle ? planResult.getTitle() : request.getTitle();
    }

    private int questionCount(CreateQaSetRequest request) {
        return request.getRequestedQuestionCount() == null ? 0 : request.getRequestedQuestionCount();
    }

    private String format(LocalDateTime time) {
        return time == null ? null : time.toString().replace('T', ' ');
    }
}
