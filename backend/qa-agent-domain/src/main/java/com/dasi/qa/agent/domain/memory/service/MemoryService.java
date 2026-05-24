package com.dasi.qa.agent.domain.memory.service;

import com.dasi.qa.agent.domain.agent.service.memory.IMemoryAgent;
import com.dasi.qa.agent.domain.agent.service.memory.model.context.MemoryContext;
import com.dasi.qa.agent.domain.agent.service.memory.model.enumeration.MemoryConfidenceHint;
import com.dasi.qa.agent.domain.agent.service.memory.model.result.MemoryCandidateResult;
import com.dasi.qa.agent.domain.memory.model.MemoryIngestContext;
import com.dasi.qa.agent.domain.memory.model.MemoryIngestItem;
import com.dasi.qa.agent.domain.memory.model.UserMemory;
import com.dasi.qa.agent.domain.memory.model.UserMemoryEvidence;
import com.dasi.qa.agent.domain.memory.model.enumeration.MemoryStatus;
import com.dasi.qa.agent.domain.memory.repository.IMemoryRepository;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.domain.util.IIdUtil;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import com.dasi.qa.agent.types.dto.request.memory.MemoryHideRequest;
import com.dasi.qa.agent.types.dto.response.memory.UserMemoryDetailResponse;
import com.dasi.qa.agent.types.dto.response.memory.UserMemoryResponse;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import com.dasi.qa.agent.types.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class MemoryService implements IMemoryService {

    private final IMemoryRepository memoryRepository;
    private final IMemoryAgent memoryAgent;
    private final IContextUtil contextUtil;
    private final IIdUtil idUtil;
    private final IJsonUtil jsonUtil;

    public MemoryService(IMemoryRepository memoryRepository,
                         IMemoryAgent memoryAgent,
                         IContextUtil contextUtil,
                         IIdUtil idUtil,
                         IJsonUtil jsonUtil) {
        this.memoryRepository = memoryRepository;
        this.memoryAgent = memoryAgent;
        this.contextUtil = contextUtil;
        this.idUtil = idUtil;
        this.jsonUtil = jsonUtil;
    }

    @Override
    public List<UserMemoryResponse> list() {
        return memoryRepository.listActiveMemories(contextUtil.getUserId());
    }

    @Override
    public UserMemoryDetailResponse detail(String memoryId) {
        if (!StringUtils.hasText(memoryId)) {
            throw new ApiException(ResultCode.BAD_REQUEST);
        }
        return memoryRepository.detailMemory(memoryId, contextUtil.getUserId());
    }

    @Override
    public void hide(MemoryHideRequest request) {
        memoryRepository.hideMemory(request.getMemoryId(), contextUtil.getUserId());
    }

    @Override
    public void ingestAssessSession(String sessionId, String userId) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(userId)) {
            throw new ApiException(ResultCode.BAD_REQUEST);
        }
        MemoryIngestContext ingestContext = memoryRepository.getIngestContext(sessionId, userId);
        if (ingestContext == null || ingestContext.getItems() == null || ingestContext.getItems().isEmpty()) {
            log.info("【记忆画像】练习上下文为空，跳过沉淀: sessionId={}", sessionId);
            return;
        }
        MemoryContext memoryContext = buildMemoryContext(ingestContext);
        List<MemoryCandidateResult> candidates = memoryAgent.extract(memoryContext, userId);
        if (candidates.isEmpty()) {
            log.info("【记忆画像】无候选画像，跳过沉淀: sessionId={}", sessionId);
            return;
        }
        Map<String, MemoryIngestItem> itemMap = itemMap(ingestContext.getItems());
        for (MemoryCandidateResult candidate : candidates) {
            persistCandidate(candidate, ingestContext, itemMap);
        }
    }

    private MemoryContext buildMemoryContext(MemoryIngestContext context) {
        return MemoryContext.builder()
                .sessionId(context.getSessionId())
                .qaSetTitle(context.getQaSetTitle())
                .statsJson(jsonUtil.toJsonString(Map.of(
                        "totalQuestions", value(context.getTotalQuestions()),
                        "score", value(context.getScore()),
                        "accuracy", context.getAccuracy() == null ? "" : context.getAccuracy(),
                        "perfectCount", value(context.getPerfectCount()),
                        "correctCount", value(context.getCorrectCount()),
                        "deficientCount", value(context.getDeficientCount()),
                        "wrongCount", value(context.getWrongCount()),
                        "unknownCount", value(context.getUnknownCount())
                )))
                .itemsJson(jsonUtil.toJsonString(context.getItems()))
                .memoryCluesJson(StringUtils.hasText(context.getMemoryClueJson()) ? context.getMemoryClueJson() : "[]")
                .existingMemoriesJson(jsonUtil.toJsonString(context.getExistingMemories() == null ? List.of() : context.getExistingMemories()))
                .build();
    }

    private Map<String, MemoryIngestItem> itemMap(List<MemoryIngestItem> items) {
        Map<String, MemoryIngestItem> results = new LinkedHashMap<>();
        for (MemoryIngestItem item : items) {
            if (item != null && StringUtils.hasText(item.getSessionItemId())) {
                results.put(item.getSessionItemId(), item);
            }
        }
        return results;
    }

    private void persistCandidate(MemoryCandidateResult candidate, MemoryIngestContext context, Map<String, MemoryIngestItem> itemMap) {
        List<MemoryIngestItem> evidenceItems = candidate.getEvidenceRefs().stream()
                .map(itemMap::get)
                .filter(item -> item != null)
                .toList();
        if (evidenceItems.isEmpty()) {
            return;
        }
        UserMemory existing = resolveExistingMemory(candidate, context.getUserId());
        if (existing != null && MemoryStatus.HIDDEN.name().equals(existing.getStatus())) {
            return;
        }
        if (existing != null) {
            evidenceItems = evidenceItems.stream()
                    .filter(item -> !memoryRepository.existsEvidence(existing.getId(), item.getSessionItemId()))
                    .toList();
            if (evidenceItems.isEmpty()) {
                return;
            }
        }
        LocalDateTime now = LocalDateTime.now();
        UserMemory memory = existing == null ? newMemory(candidate, context, evidenceItems.size(), now) : updateMemory(existing, candidate, context, evidenceItems.size(), now);
        if (existing == null) {
            memoryRepository.createMemory(memory);
        } else {
            memoryRepository.updateMemory(memory);
        }
        for (MemoryIngestItem item : evidenceItems) {
            if (memoryRepository.existsEvidence(memory.getId(), item.getSessionItemId())) {
                continue;
            }
            memoryRepository.createEvidence(UserMemoryEvidence.builder()
                    .id(idUtil.nextId())
                    .memoryId(memory.getId())
                    .userId(context.getUserId())
                    .sessionId(context.getSessionId())
                    .sessionItemId(item.getSessionItemId())
                    .qaSetId(context.getQaSetId())
                    .qaItemId(item.getQaItemId())
                    .moduleTag(item.getModuleTag())
                    .questionSnapshot(item.getQuestion())
                    .result(item.getResult())
                    .score(item.getScore())
                    .sourceChunkIdsJson(item.getSourceChunkIdsJson())
                    .memoryClueJson(StringUtils.hasText(context.getMemoryClueJson()) ? context.getMemoryClueJson() : "[]")
                    .evidenceSummary(candidate.getSummary())
                    .createdAt(now)
                    .build());
        }
    }

    private UserMemory resolveExistingMemory(MemoryCandidateResult candidate, String userId) {
        if (StringUtils.hasText(candidate.getRelatedMemoryId())) {
            UserMemory related = memoryRepository.findActiveMemoryById(candidate.getRelatedMemoryId(), userId);
            if (related != null
                    && candidate.getMemoryType().equals(related.getMemoryType())
                    && candidate.getTargetType().equals(related.getTargetType())
                    && candidate.getTargetKey().equals(related.getTargetKey())) {
                return related;
            }
        }
        return memoryRepository.findMemoryByKey(userId, candidate.getMemoryType(), candidate.getTargetType(), candidate.getTargetKey());
    }

    private UserMemory newMemory(MemoryCandidateResult candidate, MemoryIngestContext context, int evidenceCount, LocalDateTime now) {
        return UserMemory.builder()
                .id(idUtil.nextId())
                .userId(context.getUserId())
                .memoryType(candidate.getMemoryType())
                .targetType(candidate.getTargetType())
                .targetKey(candidate.getTargetKey())
                .title(candidate.getTitle())
                .summary(candidate.getSummary())
                .detail(candidate.getDetail())
                .confidence(confidence(candidate, evidenceCount, 0))
                .supportCount(evidenceCount)
                .status(MemoryStatus.ACTIVE.name())
                .firstSeenAt(now)
                .lastSeenAt(now)
                .latestSessionId(context.getSessionId())
                .latestQaSetId(context.getQaSetId())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    private UserMemory updateMemory(UserMemory existing, MemoryCandidateResult candidate, MemoryIngestContext context, int evidenceCount, LocalDateTime now) {
        int currentSupport = existing.getSupportCount() == null ? 0 : existing.getSupportCount();
        existing.setTitle(candidate.getTitle());
        existing.setSummary(candidate.getSummary());
        existing.setDetail(candidate.getDetail());
        existing.setSupportCount(currentSupport + evidenceCount);
        existing.setConfidence(confidence(candidate, evidenceCount, currentSupport));
        existing.setLastSeenAt(now);
        existing.setLatestSessionId(context.getSessionId());
        existing.setLatestQaSetId(context.getQaSetId());
        existing.setUpdatedAt(now);
        return existing;
    }

    private int confidence(MemoryCandidateResult candidate, int newEvidenceCount, int currentSupport) {
        int base = MemoryConfidenceHint.fromValue(candidate.getConfidenceHint()).getBaseConfidence();
        int score = base + newEvidenceCount * 5 + Math.min(currentSupport, 4) * 3;
        return Math.min(95, Math.max(0, score));
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }
}
