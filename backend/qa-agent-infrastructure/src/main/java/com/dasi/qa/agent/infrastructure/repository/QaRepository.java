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
import com.dasi.qa.agent.domain.qa.model.enumeration.CompleteStatus;
import com.dasi.qa.agent.domain.qa.repository.IQaRepository;
import com.dasi.qa.agent.domain.util.IIdUtil;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.*;
import com.dasi.qa.agent.infrastructure.persistent.entity.*;
import com.dasi.qa.agent.types.dto.request.qa.CreateEmptyQaSetRequest;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaItemBatchRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaItemDraft;
import com.dasi.qa.agent.types.dto.request.qa.QaItemRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaSetRequest;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaItemSingleRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaItemCompleteRequest;
import com.dasi.qa.agent.types.dto.response.BaseResponse;
import com.dasi.qa.agent.types.dto.response.qa.QaItemResponse;
import com.dasi.qa.agent.domain.qa.service.convert.QaSetExportFile;
import com.dasi.qa.agent.types.dto.response.qa.QaSetResponse;
import com.dasi.qa.agent.types.constant.RedisConstant;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.dasi.qa.agent.types.constant.StringConstant.DB_USER_ID;

@Repository
public class QaRepository implements IQaRepository {

    private final QaSetMapper qaSetMapper;
    private final QaItemMapper qaItemMapper;
    private final PracticeSessionMapper practiceSessionMapper;
    private final PracticeSessionItemMapper practiceSessionItemMapper;
    private final QaSetDocumentRefMapper qaSetDocumentRefMapper;
    private final SourceDocumentMapper sourceDocumentMapper;
    private final UserMemoryEvidenceMapper userMemoryEvidenceMapper;
    private final MessageJobMapper messageJobMapper;
    private final IIdUtil idUtil;

    public QaRepository(QaSetMapper qaSetMapper, QaItemMapper qaItemMapper,
                        PracticeSessionMapper practiceSessionMapper,
                        PracticeSessionItemMapper practiceSessionItemMapper,
                        QaSetDocumentRefMapper qaSetDocumentRefMapper,
                        SourceDocumentMapper sourceDocumentMapper,
                        UserMemoryEvidenceMapper userMemoryEvidenceMapper,
                        MessageJobMapper messageJobMapper,
                        IIdUtil idUtil) {
        this.qaSetMapper = qaSetMapper;
        this.qaItemMapper = qaItemMapper;
        this.practiceSessionMapper = practiceSessionMapper;
        this.practiceSessionItemMapper = practiceSessionItemMapper;
        this.qaSetDocumentRefMapper = qaSetDocumentRefMapper;
        this.sourceDocumentMapper = sourceDocumentMapper;
        this.userMemoryEvidenceMapper = userMemoryEvidenceMapper;
        this.messageJobMapper = messageJobMapper;
        this.idUtil = idUtil;
    }

    @Override
    @Cacheable(cacheNames = RedisConstant.QA_SET_CACHE,
            key = "@redisUtil.detail(T(com.dasi.qa.agent.types.constant.RedisConstant).QA_SET_DETAIL_KEY, #userId, #id)")
    public QaSetResponse detailQaSet(String id, String userId) {
        QaSetResponse response = detail(qaSetMapper, QaSet.class, QaSetResponse.class, id, userId);
        response.setDocumentCount(countDocumentRefs(id));
        return response;
    }

    @Override
    @Cacheable(cacheNames = RedisConstant.QA_SET_CACHE,
            key = "@redisUtil.query(T(com.dasi.qa.agent.types.constant.RedisConstant).QA_SET_QUERY_KEY, #userId, #request)")
    public List<QaSetResponse> queryQaSet(QaSetRequest request, String userId) {
        List<QaSetResponse> responses = query(qaSetMapper, QaSet.class, QaSetResponse.class, request, userId);
        List<String> qaSetIds = responses.stream().map(QaSetResponse::getId).toList();
        if (qaSetIds.isEmpty()) {
            return responses;
        }
        Map<String, Long> countMap = qaSetDocumentRefMapper.selectList(
                new LambdaQueryWrapper<QaSetDocumentRef>().in(QaSetDocumentRef::getQaSetId, qaSetIds))
                .stream().collect(Collectors.groupingBy(QaSetDocumentRef::getQaSetId, Collectors.counting()));
        for (QaSetResponse response : responses) {
            response.setDocumentCount(countMap.getOrDefault(response.getId(), 0L).intValue());
        }
        return responses;
    }

    private Integer countDocumentRefs(String qaSetId) {
        return Math.toIntExact(qaSetDocumentRefMapper.selectCount(
                new LambdaQueryWrapper<QaSetDocumentRef>().eq(QaSetDocumentRef::getQaSetId, qaSetId)));
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    @CacheEvict(cacheNames = RedisConstant.QA_SET_CACHE, allEntries = true)
    public QaSetResponse createEmptyQaSet(String id, CreateEmptyQaSetRequest request, String userId) {
        LocalDateTime now = LocalDateTime.now();
        QaSet qaSet = QaSet.builder()
                .id(id)
                .userId(userId)
                .taskId(null)
                .title(request.getTitle().trim())
                .description(StringUtils.hasText(request.getDescription()) ? request.getDescription().trim() : "")
                .moduleTagsJson(JSON.toJSONString(List.of()))
                .questionCount(0)
                .practiceCount(0)
                .averageScore(0)
                .bestScore(0)
                .averageAccuracy(BigDecimal.ZERO)
                .bestAccuracy(BigDecimal.ZERO)
                .createdAt(now)
                .updatedAt(now)
                .build();
        qaSetMapper.insert(qaSet);
        return detailQaSet(id, userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.QA_SET_CACHE, allEntries = true)
    public QaSetResponse updateQaSet(QaSetRequest request, String userId) {
        requireQaSet(request.getId(), userId);
        QaSet entity = toEntity(request, QaSet.class);
        entity.setUserId(userId);
        entity.setUpdatedAt(LocalDateTime.now());
        qaSetMapper.update(entity, new LambdaUpdateWrapper<QaSet>()
                .eq(QaSet::getId, request.getId())
                .eq(QaSet::getUserId, userId));
        return detailQaSet(request.getId(), userId);
    }

    @Override
    @CacheEvict(cacheNames = {RedisConstant.QA_SET_CACHE, RedisConstant.QA_ITEM_CACHE}, allEntries = true)
    public void deleteQaSet(String id, String userId) {
        requireQaSet(id, userId);
        List<PracticeSession> sessions = practiceSessionMapper.selectList(
            new LambdaQueryWrapper<PracticeSession>()
                    .eq(PracticeSession::getQaSetId, id)
                    .eq(PracticeSession::getUserId, userId));
        for (PracticeSession session : sessions) {
            practiceSessionItemMapper.delete(
                new LambdaQueryWrapper<PracticeSessionItem>()
                        .eq(PracticeSessionItem::getSessionId, session.getId())
                        .eq(PracticeSessionItem::getUserId, userId));
        }
        practiceSessionMapper.delete(
            new LambdaQueryWrapper<PracticeSession>()
                    .eq(PracticeSession::getQaSetId, id)
                    .eq(PracticeSession::getUserId, userId));
        qaItemMapper.delete(
            new LambdaQueryWrapper<QaItem>()
                    .eq(QaItem::getQaSetId, id)
                    .eq(QaItem::getUserId, userId));
        List<QaSetDocumentRef> refs = qaSetDocumentRefMapper.selectList(
            new LambdaQueryWrapper<QaSetDocumentRef>().eq(QaSetDocumentRef::getQaSetId, id));
        for (QaSetDocumentRef ref : refs) {
            sourceDocumentMapper.update(null,
                    new LambdaUpdateWrapper<SourceDocument>()
                            .setSql("reference_count = CASE WHEN reference_count > 0 THEN reference_count - 1 ELSE 0 END")
                            .eq(SourceDocument::getId, ref.getDocumentId()));
        }
        qaSetDocumentRefMapper.delete(
            new LambdaQueryWrapper<QaSetDocumentRef>().eq(QaSetDocumentRef::getQaSetId, id));
        qaSetMapper.delete(new LambdaQueryWrapper<QaSet>()
                .eq(QaSet::getId, id)
                .eq(QaSet::getUserId, userId));
    }

    @Override
    public List<QaItemResponse> queryQaItemsBySetId(String qaSetId, String userId) {
        requireQaSet(qaSetId, userId);
        return qaItemMapper.selectList(new LambdaQueryWrapper<QaItem>()
                        .eq(QaItem::getQaSetId, qaSetId)
                        .eq(QaItem::getUserId, userId)
                        .orderByAsc(QaItem::getSortOrder)
                        .orderByAsc(QaItem::getCreatedAt))
                .stream()
                .map(item -> toResponse(item, QaItemResponse.class))
                .toList();
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    @CacheEvict(cacheNames = {RedisConstant.QA_SET_CACHE, RedisConstant.QA_ITEM_CACHE}, allEntries = true)
    public QaSetResponse importQaSet(QaSetExportFile portableFile, String userId) {
        String qaSetId = idUtil.nextId();
        LocalDateTime now = LocalDateTime.now();
        QaSet qaSet = QaSet.builder()
                .id(qaSetId)
                .userId(userId)
                .title(portableFile.getQaQaSetMetaInfo().getTitle().trim())
                .description(portableFile.getQaQaSetMetaInfo().getDescription())
                .moduleTagsJson(JSON.toJSONString(portableFile.getQaQaSetMetaInfo().getModuleTags() != null ? portableFile.getQaQaSetMetaInfo().getModuleTags() : List.of()))
                .questionCount(portableFile.getQaSetEntries().size())
                .practiceCount(0)
                .averageScore(0)
                .bestScore(0)
                .averageAccuracy(BigDecimal.ZERO)
                .bestAccuracy(BigDecimal.ZERO)
                .createdAt(now)
                .updatedAt(now)
                .build();
        qaSetMapper.insert(qaSet);

        for (int i = 0; i < portableFile.getQaSetEntries().size(); i++) {
            QaSetExportFile.QaSetEntry qaSetEntry = portableFile.getQaSetEntries().get(i);
            QaItem qaItem = QaItem.builder()
                    .id(idUtil.nextId())
                    .userId(userId)
                    .qaSetId(qaSetId)
                    .question(qaSetEntry.getQuestion().trim())
                    .knowledgeNote(qaSetEntry.getKnowledgeNote())
                    .answer(qaSetEntry.getAnswer())
                    .moduleTag(StringUtils.hasText(qaSetEntry.getModuleTag()) ? qaSetEntry.getModuleTag() : "")
                    .difficulty(StringUtils.hasText(qaSetEntry.getDifficulty()) ? qaSetEntry.getDifficulty() : "")
                    .keywords(qaSetEntry.getKeywords())
                    .hint(qaSetEntry.getHint())
                    .sourceReliable(qaSetEntry.getSourceReliable() != null ? qaSetEntry.getSourceReliable() : Boolean.FALSE)
                    .isImported(true)
                    .sourceChunkIdsJson("[]")
                    .completeStatus(CompleteStatus.SOLVED.name())
                    .sortOrder(qaSetEntry.getSortOrder() != null ? qaSetEntry.getSortOrder() : i + 1)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            qaItemMapper.insert(qaItem);
        }
        return detailQaSet(qaSetId, userId);
    }

    @Override
    @Cacheable(cacheNames = RedisConstant.QA_ITEM_CACHE,
            key = "@redisUtil.detail(T(com.dasi.qa.agent.types.constant.RedisConstant).QA_ITEM_DETAIL_KEY, #userId, #id)")
    public QaItemResponse detailQaItem(String id, String userId) {
        return detail(qaItemMapper, QaItem.class, QaItemResponse.class, id, userId);
    }

    @Override
    public void fillQaItemPracticeStats(QaItemResponse response, String userId) {
        LambdaQueryWrapper<PracticeSessionItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PracticeSessionItem::getQaItemId, response.getId())
                .eq(PracticeSessionItem::getUserId, userId)
                .eq(PracticeSessionItem::getStatus, "SUBMITTED")
                .isNotNull(PracticeSessionItem::getScore);
        List<PracticeSessionItem> items = practiceSessionItemMapper.selectList(wrapper);
        if (items.isEmpty()) {
            return;
        }
        int total = items.size();
        double avgScore = items.stream().mapToInt(PracticeSessionItem::getScore).average().orElse(0);
        long correctCount = items.stream()
                .filter(item -> "CORRECT".equals(item.getResult()) || "PERFECT".equals(item.getResult()))
                .count();
        response.setPracticeTotalCount(total);
        response.setPracticeAverageScore(BigDecimal.valueOf(Math.round(avgScore * 10) / 10.0));
        response.setPracticeCorrectRate(BigDecimal.valueOf(Math.round((double) correctCount / total * 1000) / 10.0));
    }

    @Override
    @Cacheable(cacheNames = RedisConstant.QA_ITEM_CACHE,
            key = "@redisUtil.query(T(com.dasi.qa.agent.types.constant.RedisConstant).QA_ITEM_QUERY_KEY, #userId, #request)")
    public List<QaItemResponse> queryQaItem(QaItemRequest request, String userId) {
        return query(qaItemMapper, QaItem.class, QaItemResponse.class, request, userId);
    }

    @Override
    public boolean existsQaItemByQuestion(String qaSetId, String question, String userId) {
        LambdaQueryWrapper<QaItem> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(QaItem::getQaSetId, qaSetId)
                .eq(QaItem::getUserId, userId)
                .eq(QaItem::getQuestion, question);
        return qaItemMapper.selectCount(wrapper) > 0;
    }

    @Override
    public QaItemResponse createQaItem(QaItemRequest request, String userId) {
        return create(qaItemMapper, QaItem.class, QaItemResponse.class, request, userId);
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    @CacheEvict(cacheNames = {RedisConstant.QA_ITEM_CACHE, RedisConstant.QA_SET_CACHE}, allEntries = true)
    public QaItemResponse createQaItem(String id, CreateQaItemSingleRequest request, String userId) {
        QaSet qaSet = qaSetMapper.selectById(request.getQaSetId());
        if (qaSet == null) {
            throw new ApiException(ResultCode.NOT_FOUND, "题集不存在");
        }
        if (!userId.equals(qaSet.getUserId())) {
            throw new ApiException(ResultCode.NOT_FOUND, "题集不存在");
        }
        Integer maxSortOrder = qaItemMapper.selectList(new LambdaQueryWrapper<QaItem>()
                        .eq(QaItem::getQaSetId, request.getQaSetId())
                        .eq(QaItem::getUserId, userId))
                .stream()
                .map(QaItem::getSortOrder)
                .filter(sortOrder -> sortOrder != null)
                .max(Integer::compareTo)
                .orElse(0);
        QaItem item = QaItem.builder()
                .id(id)
                .userId(userId)
                .qaSetId(request.getQaSetId())
                .question(request.getQuestion())
                .knowledgeNote("")
                .answer(request.getAnswer() != null ? request.getAnswer() : "")
                .moduleTag("")
                .difficulty("")
                .keywords("")
                .hint("")
                .sourceReliable(Boolean.TRUE)
                .sourceChunkIdsJson("[]")
                .completeStatus(CompleteStatus.PROCESSING.name())
                .sortOrder(maxSortOrder + 1)
                .build();
        qaItemMapper.insert(item);
        qaSetMapper.update(null,
                new LambdaUpdateWrapper<QaSet>()
                        .setSql("question_count = question_count + 1")
                        .set(QaSet::getUpdatedAt, LocalDateTime.now())
                        .eq(QaSet::getId, request.getQaSetId())
                        .eq(QaSet::getUserId, userId));
        return toResponse(item, QaItemResponse.class);
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    @CacheEvict(cacheNames = {RedisConstant.QA_ITEM_CACHE, RedisConstant.QA_SET_CACHE}, allEntries = true)
    public List<QaItemResponse> createQaItems(List<String> ids, CreateQaItemBatchRequest request, String userId) {
        QaSet qaSet = qaSetMapper.selectById(request.getQaSetId());
        if (qaSet == null) {
            throw new ApiException(ResultCode.NOT_FOUND, "题集不存在");
        }
        if (!userId.equals(qaSet.getUserId())) {
            throw new ApiException(ResultCode.NOT_FOUND, "题集不存在");
        }
        List<QaItemDraft> items = request.getItems();
        if (ids == null || ids.size() != items.size()) {
            throw new ApiException(ResultCode.BAD_REQUEST, "批量创建题目的 ID 数量与题目数量不一致");
        }
        Integer maxSortOrder = qaItemMapper.selectList(new LambdaQueryWrapper<QaItem>()
                        .eq(QaItem::getQaSetId, request.getQaSetId())
                        .eq(QaItem::getUserId, userId))
                .stream()
                .map(QaItem::getSortOrder)
                .filter(sortOrder -> sortOrder != null)
                .max(Integer::compareTo)
                .orElse(0);
        List<QaItemResponse> responses = new java.util.ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            QaItemDraft draft = items.get(i);
            QaItem item = QaItem.builder()
                    .id(ids.get(i))
                    .userId(userId)
                    .qaSetId(request.getQaSetId())
                    .question(draft.getQuestion())
                    .knowledgeNote("")
                    .answer(draft.getAnswer() != null ? draft.getAnswer() : "")
                    .moduleTag("")
                    .difficulty("")
                    .keywords("")
                    .hint("")
                    .sourceReliable(Boolean.TRUE)
                    .isImported(false)
                    .sourceChunkIdsJson("[]")
                    .completeStatus(CompleteStatus.PROCESSING.name())
                    .sortOrder(maxSortOrder + i + 1)
                    .build();
            qaItemMapper.insert(item);
            responses.add(toResponse(item, QaItemResponse.class));
        }
        qaSetMapper.update(null,
                new LambdaUpdateWrapper<QaSet>()
                        .setSql("question_count = question_count + " + items.size())
                        .set(QaSet::getUpdatedAt, LocalDateTime.now())
                        .eq(QaSet::getId, request.getQaSetId())
                        .eq(QaSet::getUserId, userId));
        return responses;
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.QA_ITEM_CACHE, allEntries = true)
    public QaItemResponse updateQaItem(QaItemRequest request, String userId) {
        QaItem existing = requireQaItem(request.getId(), userId);
        QaItem entity = toEntity(request, QaItem.class);
        entity.setUserId(userId);
        entity.setQaSetId(existing.getQaSetId());
        entity.setUpdatedAt(LocalDateTime.now());
        qaItemMapper.update(entity, new LambdaUpdateWrapper<QaItem>()
                .eq(QaItem::getId, request.getId())
                .eq(QaItem::getUserId, userId));
        return detailQaItem(request.getId(), userId);
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.QA_ITEM_CACHE, allEntries = true)
    public QaItemResponse markQaItemCompleteProcessing(QaItemCompleteRequest request, String userId) {
        QaItem item = qaItemMapper.selectById(request.getId());
        if (item == null) {
            throw new ApiException(ResultCode.NOT_FOUND, "题目不存在");
        }
        if (!userId.equals(item.getUserId())) {
            throw new ApiException(ResultCode.NOT_FOUND, "题目不存在");
        }
        if (CompleteStatus.PROCESSING.name().equals(item.getCompleteStatus())) {
            throw new ApiException(ResultCode.CONFLICT, "题目正在补全中，请稍后再试");
        }
        String question = request.getQuestion().trim();
        String answer = request.getAnswer() == null ? "" : request.getAnswer().trim();
        LocalDateTime now = LocalDateTime.now();
        int updated = qaItemMapper.update(null,
                new LambdaUpdateWrapper<QaItem>()
                        .set(QaItem::getQuestion, question)
                        .set(QaItem::getAnswer, answer)
                        .set(QaItem::getCompleteStatus, CompleteStatus.PROCESSING.name())
                        .set(QaItem::getUpdatedAt, now)
                        .eq(QaItem::getId, request.getId())
                        .eq(QaItem::getUserId, userId)
                        .ne(QaItem::getCompleteStatus, CompleteStatus.PROCESSING.name()));
        if (updated == 0) {
            throw new ApiException(ResultCode.CONFLICT, "题目正在补全中，请稍后再试");
        }
        item.setQuestion(question);
        item.setAnswer(answer);
        item.setKnowledgeNote("");
        item.setDifficulty("");
        item.setKeywords("");
        item.setHint("");
        item.setModuleTag("");
        item.setSourceChunkIdsJson("[]");
        item.setSourceReliable(Boolean.TRUE);
        item.setCompleteStatus(CompleteStatus.PROCESSING.name());
        item.setUpdatedAt(now);
        return toResponse(item, QaItemResponse.class);
    }

    @Override
    public List<String> getDocumentIdsByQaSetId(String qaSetId) {
        return qaSetDocumentRefMapper.selectList(
                new LambdaQueryWrapper<QaSetDocumentRef>().eq(QaSetDocumentRef::getQaSetId, qaSetId))
                .stream().map(QaSetDocumentRef::getDocumentId).toList();
    }

    @Override
    public List<QaItemResponse> getSolvableQaItemsBySetId(String qaSetId, String userId) {
        List<QaItem> items = qaItemMapper.selectList(new LambdaQueryWrapper<QaItem>()
                .eq(QaItem::getQaSetId, qaSetId)
                .eq(QaItem::getUserId, userId)
                .eq(QaItem::getCompleteStatus, CompleteStatus.SOLVED.name()));
        return items.stream().map(item -> toResponse(item, QaItemResponse.class)).toList();
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    public void syncQaSetDocumentRefs(String qaSetId, List<String> documentIds) {
        qaSetDocumentRefMapper.delete(new LambdaQueryWrapper<QaSetDocumentRef>()
                .eq(QaSetDocumentRef::getQaSetId, qaSetId));
        if (documentIds != null) {
            for (String docId : documentIds) {
                QaSetDocumentRef ref = new QaSetDocumentRef();
                ref.setQaSetId(qaSetId);
                ref.setDocumentId(docId);
                qaSetDocumentRefMapper.insert(ref);
            }
        }
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.QA_ITEM_CACHE, allEntries = true)
    public void updateQaItemEvidenceFields(String itemId, String userId, List<String> chunkIds, boolean hasEvidence) {
        qaItemMapper.update(null, new LambdaUpdateWrapper<QaItem>()
                .set(QaItem::getSourceChunkIdsJson, JSON.toJSONString(chunkIds))
                .set(QaItem::getSourceReliable, hasEvidence)
                .set(QaItem::getIsImported, false)
                .set(QaItem::getUpdatedAt, LocalDateTime.now())
                .eq(QaItem::getId, itemId)
                .eq(QaItem::getUserId, userId));
    }

    @Override
    @CacheEvict(cacheNames = RedisConstant.QA_ITEM_CACHE, allEntries = true)
    public void removeOrphanChunkRefs(List<String> deadChunkIds, String userId) {
        if (deadChunkIds == null || deadChunkIds.isEmpty()) {
            return;
        }
        List<QaItem> items = qaItemMapper.selectList(new LambdaQueryWrapper<QaItem>()
                .eq(QaItem::getUserId, userId)
                .isNotNull(QaItem::getSourceChunkIdsJson)
                .ne(QaItem::getSourceChunkIdsJson, "[]"));
        for (QaItem item : items) {
            List<String> current = JSON.parseArray(item.getSourceChunkIdsJson(), String.class);
            if (current == null || current.isEmpty()) {
                continue;
            }
            List<String> cleaned = current.stream()
                    .filter(id -> !deadChunkIds.contains(id))
                    .toList();
            if (cleaned.size() != current.size()) {
                item.setSourceChunkIdsJson(JSON.toJSONString(cleaned));
                if (cleaned.isEmpty()) {
                    item.setSourceReliable(false);
                }
                item.setUpdatedAt(LocalDateTime.now());
                qaItemMapper.updateById(item);
            }
        }
    }

    @Override
    public void deleteDocumentRefsByDocumentId(String documentId) {
        qaSetDocumentRefMapper.delete(new LambdaQueryWrapper<QaSetDocumentRef>()
                .eq(QaSetDocumentRef::getDocumentId, documentId));
    }

    @Override
    @Transactional(transactionManager = "mysqlTransactionManager")
    @CacheEvict(cacheNames = {RedisConstant.QA_ITEM_CACHE, RedisConstant.QA_SET_CACHE}, allEntries = true)
    public void deleteQaItem(String id, String userId) {
        QaItem item = requireQaItem(id, userId);
        practiceSessionItemMapper.delete(new LambdaQueryWrapper<PracticeSessionItem>()
                .eq(PracticeSessionItem::getQaItemId, id)
                .eq(PracticeSessionItem::getUserId, userId));
        userMemoryEvidenceMapper.delete(new LambdaQueryWrapper<UserMemoryEvidence>()
                .eq(UserMemoryEvidence::getQaItemId, id)
                .eq(UserMemoryEvidence::getUserId, userId));
        messageJobMapper.delete(new LambdaQueryWrapper<MessageJob>()
                .eq(MessageJob::getJobId, "assist_" + id));
        qaItemMapper.delete(new LambdaQueryWrapper<QaItem>()
                .eq(QaItem::getId, id)
                .eq(QaItem::getUserId, userId));
        qaSetMapper.update(null,
                new LambdaUpdateWrapper<QaSet>()
                        .setSql("question_count = CASE WHEN question_count > 0 THEN question_count - 1 ELSE 0 END")
                        .set(QaSet::getUpdatedAt, LocalDateTime.now())
                        .eq(QaSet::getId, item.getQaSetId())
                        .eq(QaSet::getUserId, userId));
    }

    private QaSet requireQaSet(String id, String userId) {
        QaSet entity = qaSetMapper.selectById(id);
        if (entity == null) {
            throw new ApiException(ResultCode.NOT_FOUND, "题集不存在");
        }
        if (!userId.equals(entity.getUserId())) {
            throw new ApiException(ResultCode.NOT_FOUND, "题集不存在");
        }
        return entity;
    }

    private QaItem requireQaItem(String id, String userId) {
        QaItem entity = qaItemMapper.selectById(id);
        if (entity == null) {
            throw new ApiException(ResultCode.NOT_FOUND, "题目不存在");
        }
        if (!userId.equals(entity.getUserId())) {
            throw new ApiException(ResultCode.NOT_FOUND, "题目不存在");
        }
        return entity;
    }

    private <E, R extends BaseResponse> R detail(BaseMapper<E> mapper, Class<E> entityType, Class<R> responseType, String id, String userId) {
        E entity = mapper.selectById(id);
        if (entity == null) {
            throw new ApiException(ResultCode.NOT_FOUND, "问答资源不存在");
        }
        if (ReflectUtil.getField(entityType, "userId") != null) {
            Object entityUserId = BeanUtil.getProperty(entity, "userId");
            if (entityUserId != null && !userId.equals(String.valueOf(entityUserId))) {
                throw new ApiException(ResultCode.NOT_FOUND, "问答资源不存在");
            }
        }
        return toResponse(entity, responseType);
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
        queryWrapper.orderByDesc("created_at");
        return mapper.selectList(queryWrapper).stream().map(entity -> toResponse(entity, responseType)).toList();
    }

    private <E, Q, R extends BaseResponse> R create(BaseMapper<E> mapper, Class<E> entityType, Class<R> responseType, Q request, String userId) {
        E entity = toEntity(request, entityType);
        BeanUtil.setProperty(entity, "userId", userId);
        mapper.insert(entity);
        return toResponse(entity, responseType);
    }

    private <E, Q, R extends BaseResponse> R update(BaseMapper<E> mapper, Class<E> entityType, Class<R> responseType, Q request, String userId) {
        E entity = toEntity(request, entityType);
        BeanUtil.setProperty(entity, "userId", userId);
        mapper.updateById(entity);
        return toResponse(entity, responseType);
    }

    private <E, Q> E toEntity(Q request, Class<E> entityType) {
        E entity = ReflectUtil.newInstance(entityType);
        BeanUtil.copyProperties(request, entity, CopyOptions.create().ignoreNullValue());
        return entity;
    }

    private <E, R extends BaseResponse> R toResponse(E entity, Class<R> responseType) {
        R response = ReflectUtil.newInstance(responseType);
        BeanUtil.copyProperties(entity, response, CopyOptions.create().ignoreNullValue());
        if (!StringUtils.hasText(response.getId()) && BeanUtil.getProperty(entity, "userId") != null) {
            response.setId(String.valueOf(BeanUtil.getProperty(entity, "userId")));
        }
        return response;
    }
}
