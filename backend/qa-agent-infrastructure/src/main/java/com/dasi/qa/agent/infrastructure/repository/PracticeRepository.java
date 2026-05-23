package com.dasi.qa.agent.infrastructure.repository;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.dasi.qa.agent.domain.practice.model.enumeration.PracticeFeedbackMode;
import com.dasi.qa.agent.domain.practice.model.vo.PracticeStateVO;
import com.dasi.qa.agent.domain.practice.repository.IPracticeRepository;
import com.dasi.qa.agent.infrastructure.persistent.entity.PracticeSession;
import com.dasi.qa.agent.infrastructure.persistent.entity.PracticeSessionItem;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaItem;
import com.dasi.qa.agent.infrastructure.persistent.entity.QaSet;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.PracticeSessionItemMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.PracticeSessionMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaItemMapper;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.QaSetMapper;
import com.dasi.qa.agent.types.constant.RedisConstant;
import com.dasi.qa.agent.types.dto.request.practice.ItemSaveRequest;
import com.dasi.qa.agent.types.dto.request.practice.PracticeInitRequest;
import com.dasi.qa.agent.types.dto.request.practice.PracticeQueryRequest;
import com.dasi.qa.agent.types.dto.response.BaseResponse;
import com.dasi.qa.agent.types.dto.response.practice.*;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import com.dasi.qa.agent.types.exception.ApiException;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.dasi.qa.agent.types.constant.StringConstant.DB_USER_ID;

@Repository
public class PracticeRepository implements IPracticeRepository {

    private final PracticeSessionMapper practiceSessionMapper;
    private final PracticeSessionItemMapper practiceSessionItemMapper;
    private final QaSetMapper qaSetMapper;
    private final QaItemMapper qaItemMapper;

    public PracticeRepository(
            PracticeSessionMapper practiceSessionMapper,
            PracticeSessionItemMapper practiceSessionItemMapper,
            QaSetMapper qaSetMapper,
            QaItemMapper qaItemMapper
    ) {
        this.practiceSessionMapper = practiceSessionMapper;
        this.practiceSessionItemMapper = practiceSessionItemMapper;
        this.qaSetMapper = qaSetMapper;
        this.qaItemMapper = qaItemMapper;
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.PRACTICE_SESSION_CACHE, allEntries = true)
    public PracticeDetailResponse initPractice(PracticeInitRequest request, String sessionId, List<String> sessionItemIds, String userId) {
        QaSet qaSet = requireQaSet(request.getQaSetId(), userId);
        List<QaItem> qaItems = startPracticeItems(request, userId);
        if (qaItems.isEmpty()) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        if ("RANDOM".equals(request.getMode())) {
            Collections.shuffle(qaItems);
        }
        LocalDateTime now = LocalDateTime.now();
        PracticeSession session = PracticeSession.builder()
                .id(sessionId)
                .userId(userId)
                .qaSetId(request.getQaSetId())
                .mode(StringUtils.hasText(request.getMode()) ? request.getMode() : "SEQUENTIAL")
                .feedbackMode(PracticeFeedbackMode.fromValue(request.getFeedbackMode()).name())
                .status("IN_PROGRESS")
                .selectedModule(request.getSelectedModule())
                .totalQuestions(qaItems.size())
                .answeredCount(0)
                .currentIndex(0)
                .durationSeconds(0)
                .perfectCount(0)
                .correctCount(0)
                .deficientCount(0)
                .wrongCount(0)
                .unknownCount(0)
                .startedAt(now)
                .lastActiveAt(now)
                .createdAt(now)
                .updatedAt(now)
                .build();
        practiceSessionMapper.insert(session);
        for (int i = 0; i < qaItems.size(); i++) {
            QaItem qaItem = qaItems.get(i);
            PracticeSessionItem item = PracticeSessionItem.builder()
                    .id(i < sessionItemIds.size() ? sessionItemIds.get(i) : UUID.randomUUID().toString())
                    .userId(userId)
                    .sessionId(sessionId)
                    .qaItemId(qaItem.getId())
                    .sortOrder(i + 1)
                    .status("UNANSWERED")
                    .unknown(false)
                    .questionSnapshot(qaItem.getQuestion())
                    .standardAnswerSnapshot(qaItem.getAnswer())
                    .knowledgeNoteSnapshot(qaItem.getKnowledgeNote())
                    .keywordsSnapshot(qaItem.getKeywords())
                    .hintSnapshot(qaItem.getHint())
                    .moduleTagSnapshot(qaItem.getModuleTag())
                    .difficultySnapshot(qaItem.getDifficulty())
                    .sourceChunkIdsSnapshotJson(qaItem.getSourceChunkIdsJson())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            practiceSessionItemMapper.insert(item);
        }
        return detailPractice(sessionId, userId, qaSet);
    }

    @Override
    public int countPracticeItems(PracticeInitRequest request, String userId) {
        return Math.toIntExact(qaItemMapper.selectCount(startPracticeItemWrapper(request, userId)));
    }

    @Override
    public PracticeStateResponse existPractice(String qaSetId, String userId) {
        PracticeSession session = practiceSessionMapper.selectOne(new LambdaQueryWrapper<PracticeSession>()
                .eq(PracticeSession::getQaSetId, qaSetId)
                .eq(PracticeSession::getUserId, userId)
                .eq(PracticeSession::getStatus, "IN_PROGRESS")
                .orderByDesc(PracticeSession::getLastActiveAt)
                .orderByDesc(PracticeSession::getUpdatedAt)
                .last("LIMIT 1"));
        if (session == null) {
            return null;
        }
        QaSet qaSet = requireQaSet(session.getQaSetId(), userId);
        return toFlowSession(session, qaSet);
    }

    @Override
    public PracticeDetailResponse detailPractice(String sessionId, String userId) {
        return detailPractice(sessionId, userId, null);
    }

    @Override
    public PracticeStateVO getPracticeState(String sessionId, String userId) {
        PracticeSession session = requirePracticeSession(sessionId, userId);
        return new PracticeStateVO(
                session.getId(),
                session.getUserId(),
                session.getStatus(),
                PracticeFeedbackMode.fromValue(session.getFeedbackMode())
        );
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.PRACTICE_SESSION_CACHE, allEntries = true)
    public PracticeItemResponse savePracticeAnswer(ItemSaveRequest request, String userId) {
        PracticeSession session = requirePracticeSession(request.getSessionId(), userId);
        PracticeSessionItem item = requireSessionItem(request.getSessionItemId(), request.getSessionId(), userId);
        LocalDateTime now = LocalDateTime.now();
        String nextStatus = "SUBMITTED".equals(item.getStatus()) ? "SUBMITTED" : (StringUtils.hasText(request.getUserAnswer()) ? "DRAFT" : "UNANSWERED");
        practiceSessionItemMapper.update(null, new LambdaUpdateWrapper<PracticeSessionItem>()
                .set(PracticeSessionItem::getUserAnswer, request.getUserAnswer())
                .set(PracticeSessionItem::getStatus, nextStatus)
                .set(!"SUBMITTED".equals(item.getStatus()) && StringUtils.hasText(request.getUserAnswer()), PracticeSessionItem::getUnknown, false)
                .set(PracticeSessionItem::getUpdatedAt, now)
                .eq(PracticeSessionItem::getId, request.getSessionItemId())
                .eq(PracticeSessionItem::getUserId, userId));
        touchSession(session.getId(), request.getCurrentIndex(), request.getDurationSeconds(), userId, now);
        refreshAnsweredCount(session.getId(), userId, now);
        return toFlowItem(requirePracticeSessionItem(request.getSessionItemId()), qaItemMapper.selectById(item.getQaItemId()));
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.PRACTICE_SESSION_CACHE, allEntries = true)
    public PracticeItemResponse markUnknownOnly(ItemSaveRequest request, String userId) {
        PracticeSession session = requirePracticeSession(request.getSessionId(), userId);
        PracticeSessionItem item = requireSessionItem(request.getSessionItemId(), request.getSessionId(), userId);
        LocalDateTime now = LocalDateTime.now();
        practiceSessionItemMapper.update(null, new LambdaUpdateWrapper<PracticeSessionItem>()
                .set(PracticeSessionItem::getUserAnswer, request.getUserAnswer())
                .set(PracticeSessionItem::getStatus, "UNKNOWN")
                .set(PracticeSessionItem::getUnknown, true)
                .set(PracticeSessionItem::getUpdatedAt, now)
                .eq(PracticeSessionItem::getId, request.getSessionItemId())
                .eq(PracticeSessionItem::getUserId, userId));
        touchSession(session.getId(), request.getCurrentIndex(), request.getDurationSeconds(), userId, now);
        refreshAnsweredCount(session.getId(), userId, now);
        return toFlowItem(requirePracticeSessionItem(request.getSessionItemId()), qaItemMapper.selectById(item.getQaItemId()));
    }

    @Override
    @CacheEvict(cacheNames = {RedisConstant.PRACTICE_SESSION_CACHE, RedisConstant.PRACTICE_SESSION_ITEM_CACHE}, allEntries = true)
    public PracticeItemResponse refreshPracticeItemProgress(String sessionId, String sessionItemId, Integer currentIndex, Integer durationSeconds, String userId) {
        PracticeSession session = requirePracticeSession(sessionId, userId);
        PracticeSessionItem item = requireSessionItem(sessionItemId, sessionId, userId);
        LocalDateTime now = LocalDateTime.now();
        touchSession(session.getId(), currentIndex, durationSeconds, userId, now);
        refreshAnsweredCount(session.getId(), userId, now);
        return toFlowItem(requirePracticeSessionItem(sessionItemId), qaItemMapper.selectById(item.getQaItemId()));
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.PRACTICE_SESSION_CACHE, allEntries = true)
    public void abandonActivePractice(String qaSetId, String userId) {
        LocalDateTime now = LocalDateTime.now();
        practiceSessionMapper.update(null, new LambdaUpdateWrapper<PracticeSession>()
                .set(PracticeSession::getStatus, "ABANDONED")
                .set(PracticeSession::getUpdatedAt, now)
                .eq(PracticeSession::getQaSetId, qaSetId)
                .eq(PracticeSession::getUserId, userId)
                .eq(PracticeSession::getStatus, "IN_PROGRESS"));
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.PRACTICE_SESSION_CACHE, allEntries = true)
    public PracticeDetailResponse abandonPractice(String sessionId, Integer durationSeconds, String userId) {
        requirePracticeSession(sessionId, userId);
        LocalDateTime now = LocalDateTime.now();
        LambdaUpdateWrapper<PracticeSession> wrapper = new LambdaUpdateWrapper<PracticeSession>()
                .set(PracticeSession::getStatus, "ABANDONED")
                .set(PracticeSession::getUpdatedAt, now)
                .eq(PracticeSession::getId, sessionId)
                .eq(PracticeSession::getUserId, userId)
                .eq(PracticeSession::getStatus, "IN_PROGRESS");
        if (durationSeconds != null && durationSeconds >= 0) {
            PracticeSession session = requirePracticeSession(sessionId, userId);
            int nextDuration = Math.max(session.getDurationSeconds() == null ? 0 : session.getDurationSeconds(), durationSeconds);
            wrapper.set(PracticeSession::getDurationSeconds, nextDuration);
        }
        practiceSessionMapper.update(null, wrapper);
        return detailPractice(sessionId, userId);
    }

    @Override
    public boolean isPracticeSessionReadyForItemByItemAssess(String sessionId, String userId) {
        PracticeSession session = requirePracticeSession(sessionId, userId);
        if (!"IN_PROGRESS".equals(session.getStatus())) {
            return false;
        }
        Long remaining = practiceSessionItemMapper.selectCount(new LambdaQueryWrapper<PracticeSessionItem>()
                .eq(PracticeSessionItem::getSessionId, sessionId)
                .eq(PracticeSessionItem::getUserId, userId)
                .ne(PracticeSessionItem::getStatus, "SUBMITTED"));
        return remaining != null && remaining == 0;
    }

    @Override
    public boolean isPracticeSessionReadyForAfterAllAssess(String sessionId, String userId) {
        PracticeSession session = requirePracticeSession(sessionId, userId);
        if (!"IN_PROGRESS".equals(session.getStatus())) {
            return false;
        }
        List<PracticeSessionItem> items = practiceSessionItemMapper.selectList(new LambdaQueryWrapper<PracticeSessionItem>()
                .eq(PracticeSessionItem::getSessionId, sessionId)
                .eq(PracticeSessionItem::getUserId, userId));
        return !items.isEmpty() && items.stream()
                .allMatch(item -> Boolean.TRUE.equals(item.getUnknown()) || StringUtils.hasText(item.getUserAnswer()));
    }

    @Override
    public List<PracticeItemResponse> queryPracticeItemsForFeedback(String sessionId, String userId) {
        requirePracticeSession(sessionId, userId);
        List<PracticeSessionItem> items = practiceSessionItemMapper.selectList(new LambdaQueryWrapper<PracticeSessionItem>()
                .eq(PracticeSessionItem::getSessionId, sessionId)
                .eq(PracticeSessionItem::getUserId, userId)
                .orderByAsc(PracticeSessionItem::getSortOrder));
        Map<String, QaItem> qaItemMap = items.isEmpty()
                ? new HashMap<>()
                : qaItemMapper.selectList(new LambdaQueryWrapper<QaItem>()
                        .in(QaItem::getId, items.stream().map(PracticeSessionItem::getQaItemId).toList())
                        .eq(QaItem::getUserId, userId))
                .stream()
                .collect(Collectors.toMap(QaItem::getId, Function.identity()));
        return items.stream()
                .map(item -> toFlowItem(item, qaItemMap.get(item.getQaItemId())))
                .toList();
    }

    @Override
    public List<PracticeSessionResponse> queryPracticeHistory(String qaSetId, String userId) {
        requireQaSet(qaSetId, userId);
        return practiceSessionMapper.selectList(new LambdaQueryWrapper<PracticeSession>()
                        .eq(PracticeSession::getQaSetId, qaSetId)
                        .eq(PracticeSession::getUserId, userId)
                        .eq(PracticeSession::getStatus, "FINISHED")
                        .orderByDesc(PracticeSession::getFinishedAt)
                        .orderByDesc(PracticeSession::getCreatedAt))
                .stream()
                .map(session -> toResponse(session, PracticeSessionResponse.class))
                .toList();
    }

    @Override
    @Cacheable(cacheNames = RedisConstant.PRACTICE_SESSION_CACHE, key = "@redisUtil.query(T(com.dasi.qa.agent.types.constant.RedisConstant).PRACTICE_SESSION_QUERY_KEY, #userId, #request)")
    public List<PracticeSessionResponse> queryPracticeSession(PracticeQueryRequest request, String userId) {
        return query(practiceSessionMapper, PracticeSession.class, PracticeSessionResponse.class, request, userId);
    }

    private PracticeDetailResponse detailPractice(String sessionId, String userId, QaSet knownQaSet) {
        PracticeSession session = requirePracticeSession(sessionId, userId);
        QaSet qaSet = knownQaSet != null ? knownQaSet : requireQaSet(session.getQaSetId(), userId);
        List<PracticeSessionItem> items = practiceSessionItemMapper.selectList(new LambdaQueryWrapper<PracticeSessionItem>()
                .eq(PracticeSessionItem::getSessionId, sessionId)
                .eq(PracticeSessionItem::getUserId, userId)
                .orderByAsc(PracticeSessionItem::getSortOrder));
        Map<String, QaItem> qaItemMap = items.isEmpty()
                ? new HashMap<>()
                : qaItemMapper.selectList(new LambdaQueryWrapper<QaItem>()
                        .in(QaItem::getId, items.stream().map(PracticeSessionItem::getQaItemId).toList())
                        .eq(QaItem::getUserId, userId))
                .stream()
                .collect(Collectors.toMap(QaItem::getId, Function.identity()));
        List<PracticeItemResponse> flowItems = items.stream()
                .map(item -> toFlowItem(item, qaItemMap.get(item.getQaItemId())))
                .toList();
        return PracticeDetailResponse.builder()
                .session(toFlowSession(session, qaSet))
                .items(flowItems)
                .build();
    }

    private List<QaItem> startPracticeItems(PracticeInitRequest request, String userId) {
        return qaItemMapper.selectList(startPracticeItemWrapper(request, userId));
    }

    private LambdaQueryWrapper<QaItem> startPracticeItemWrapper(PracticeInitRequest request, String userId) {
        LambdaQueryWrapper<QaItem> wrapper = new LambdaQueryWrapper<QaItem>()
                .eq(QaItem::getQaSetId, request.getQaSetId())
                .eq(QaItem::getUserId, userId)
                .orderByAsc(QaItem::getSortOrder);
        if (StringUtils.hasText(request.getSelectedModule())) {
            wrapper.eq(QaItem::getModuleTag, request.getSelectedModule());
        }
        return wrapper;
    }

    private PracticeStateResponse toFlowSession(PracticeSession session, QaSet qaSet) {
        AssessDetail detail = null;
        if (StringUtils.hasText(session.getAssessmentDetailJson())) {
            detail = JSON.parseObject(session.getAssessmentDetailJson(), AssessDetail.class);
        }
        return PracticeStateResponse.builder()
                .id(session.getId())
                .qaSetId(session.getQaSetId())
                .qaSetTitle(qaSet != null ? qaSet.getTitle() : "")
                .mode(session.getMode())
                .feedbackMode(session.getFeedbackMode())
                .status(session.getStatus())
                .selectedModuleTag(session.getSelectedModule())
                .currentIndex(session.getCurrentIndex())
                .durationSeconds(session.getDurationSeconds())
                .totalQuestions(session.getTotalQuestions())
                .answeredCount(session.getAnsweredCount())
                .score(session.getScore())
                .accuracy(session.getAccuracy())
                .perfectCount(session.getPerfectCount())
                .correctCount(session.getCorrectCount())
                .deficientCount(session.getDeficientCount())
                .wrongCount(session.getWrongCount())
                .unknownCount(session.getUnknownCount())
                .summary(session.getSummary())
                .assessDetail(detail)
                .startedAt(session.getStartedAt())
                .lastActiveAt(session.getLastActiveAt())
                .finishedAt(session.getFinishedAt())
                .build();
    }

    private PracticeItemResponse toFlowItem(PracticeSessionItem item, QaItem qaItem) {
        JudgeDetail judgeDetail = null;
        HintDetail hintDetail = null;
        if (StringUtils.hasText(item.getFeedbackJudgeDetail())) {
            judgeDetail = JSON.parseObject(item.getFeedbackJudgeDetail(), JudgeDetail.class);
        }
        if (StringUtils.hasText(item.getFeedbackHintDetail())) {
            hintDetail = JSON.parseObject(item.getFeedbackHintDetail(), HintDetail.class);
        }
        return PracticeItemResponse.builder()
                .sessionItemId(item.getId())
                .qaItemId(item.getQaItemId())
                .sortOrder(item.getSortOrder())
                .question(firstText(item.getQuestionSnapshot(), qaItem != null ? qaItem.getQuestion() : null))
                .knowledgeNote(firstText(item.getKnowledgeNoteSnapshot(), qaItem != null ? qaItem.getKnowledgeNote() : null))
                .standardAnswer(firstText(item.getStandardAnswerSnapshot(), qaItem != null ? qaItem.getAnswer() : null))
                .moduleTag(firstText(item.getModuleTagSnapshot(), qaItem != null ? qaItem.getModuleTag() : null))
                .difficulty(firstText(item.getDifficultySnapshot(), qaItem != null ? qaItem.getDifficulty() : null))
                .keywords(firstText(item.getKeywordsSnapshot(), qaItem != null ? qaItem.getKeywords() : null))
                .hint(firstText(item.getHintSnapshot(), qaItem != null ? qaItem.getHint() : null))
                .sourceChunkIdsJson(firstText(item.getSourceChunkIdsSnapshotJson(), qaItem != null ? qaItem.getSourceChunkIdsJson() : null))
                .userAnswer(item.getUserAnswer())
                .status(StringUtils.hasText(item.getStatus()) ? item.getStatus() : "UNANSWERED")
                .unknown(Boolean.TRUE.equals(item.getUnknown()))
                .result(item.getResult())
                .score(item.getScore())
                .feedbackSummary(item.getFeedbackSummary())
                .judgeDetail(judgeDetail)
                .hintDetail(hintDetail)
                .answeredAt(item.getAnsweredAt())
                .submittedAt(item.getSubmittedAt())
                .build();
    }

    private String firstText(String preferred, String fallback) {
        return StringUtils.hasText(preferred) ? preferred : fallback;
    }

    private QaSet requireQaSet(String qaSetId, String userId) {
        QaSet qaSet = qaSetMapper.selectById(qaSetId);
        if (qaSet == null) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        if (!userId.equals(qaSet.getUserId())) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        return qaSet;
    }

    private PracticeSession requirePracticeSession(String sessionId, String userId) {
        PracticeSession session = practiceSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        if (!userId.equals(session.getUserId())) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        return session;
    }

    private PracticeSessionItem requireSessionItem(String sessionItemId, String sessionId, String userId) {
        PracticeSessionItem item = requirePracticeSessionItem(sessionItemId);
        if (!userId.equals(item.getUserId()) || !sessionId.equals(item.getSessionId())) {
            throw new ApiException(ResultCode.FORBIDDEN);
        }
        return item;
    }

    private PracticeSessionItem requirePracticeSessionItem(String sessionItemId) {
        PracticeSessionItem item = practiceSessionItemMapper.selectById(sessionItemId);
        if (item == null) {
            throw new ApiException(ResultCode.NOT_FOUND);
        }
        return item;
    }

    private void touchSession(String sessionId, Integer currentIndex, Integer durationSeconds, String userId, LocalDateTime now) {
        PracticeSession session = requirePracticeSession(sessionId, userId);
        LambdaUpdateWrapper<PracticeSession> wrapper = new LambdaUpdateWrapper<PracticeSession>()
                .set(PracticeSession::getLastActiveAt, now)
                .set(PracticeSession::getUpdatedAt, now)
                .eq(PracticeSession::getId, sessionId)
                .eq(PracticeSession::getUserId, userId)
                .eq(PracticeSession::getStatus, "IN_PROGRESS");
        if (currentIndex != null) {
            wrapper.set(PracticeSession::getCurrentIndex, currentIndex);
        }
        if (durationSeconds != null && durationSeconds >= 0) {
            int nextDuration = Math.max(session.getDurationSeconds() == null ? 0 : session.getDurationSeconds(), durationSeconds);
            wrapper.set(PracticeSession::getDurationSeconds, nextDuration);
        }
        practiceSessionMapper.update(null, wrapper);
    }

    private void refreshAnsweredCount(String sessionId, String userId, LocalDateTime now) {
        Long answered = practiceSessionItemMapper.selectCount(new LambdaQueryWrapper<PracticeSessionItem>()
                .eq(PracticeSessionItem::getSessionId, sessionId)
                .eq(PracticeSessionItem::getUserId, userId)
                .in(PracticeSessionItem::getStatus, List.of("SUBMITTED", "UNKNOWN")));
        practiceSessionMapper.update(null, new LambdaUpdateWrapper<PracticeSession>()
                .set(PracticeSession::getAnsweredCount, answered == null ? 0 : answered.intValue())
                .set(PracticeSession::getUpdatedAt, now)
                .eq(PracticeSession::getId, sessionId)
                .eq(PracticeSession::getUserId, userId));
    }

    private <E, Q, R extends BaseResponse> List<R> query(BaseMapper<E> mapper, Class<E> entityType, Class<R> responseType, Q request, String userId) {
        QueryWrapper<E> queryWrapper = new QueryWrapper<>();
        Map<String, Object> map = BeanUtil.beanToMap(request, new LinkedHashMap<>(), CopyOptions.create().ignoreNullValue());
        Map<String, Object> snakeMap = new LinkedHashMap<>();
        map.forEach((k, v) -> snakeMap.put(StrUtil.toUnderlineCase(k), v));
        queryWrapper.allEq(snakeMap, false);
        if (ReflectUtil.getField(entityType, "userId") != null) {
            queryWrapper.eq(DB_USER_ID, userId);
        }
        return mapper.selectList(queryWrapper).stream().map(entity -> toResponse(entity, responseType)).toList();
    }

    private <E, R extends BaseResponse> R toResponse(E entity, Class<R> responseType) {
        R response = ReflectUtil.newInstance(responseType);
        BeanUtil.copyProperties(entity, response, CopyOptions.create().ignoreNullValue());
        if (response instanceof PracticeSessionResponse sessionResponse
                && entity instanceof PracticeSession sessionEntity
                && sessionEntity.getAssessmentDetailJson() != null
                && StringUtils.hasText(sessionEntity.getAssessmentDetailJson())) {
            AssessDetail detail = JSON.parseObject(sessionEntity.getAssessmentDetailJson(), AssessDetail.class);
            sessionResponse.setAssessDetail(detail);
        }
        if (!StringUtils.hasText(response.getId()) && BeanUtil.getProperty(entity, "userId") != null) {
            response.setId(String.valueOf(BeanUtil.getProperty(entity, "userId")));
        }
        return response;
    }
}
