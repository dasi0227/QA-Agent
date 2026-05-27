package com.dasi.qa.agent.domain.agent.service.memory;

import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.memory.model.vo.SessionSource;
import com.dasi.qa.agent.domain.agent.service.memory.model.context.MemoryContext;
import com.dasi.qa.agent.domain.agent.service.memory.model.dto.Memory;
import com.dasi.qa.agent.domain.agent.service.memory.model.dto.MemoryEvidence;
import com.dasi.qa.agent.domain.agent.service.memory.model.enumeration.MemoryPhase;
import com.dasi.qa.agent.domain.agent.service.memory.model.enumeration.MemoryStatus;
import com.dasi.qa.agent.domain.agent.service.memory.model.result.InvestResult;
import com.dasi.qa.agent.domain.agent.service.memory.model.result.MergeResult;
import com.dasi.qa.agent.domain.agent.service.memory.subagent.InvestAgent;
import com.dasi.qa.agent.domain.agent.service.memory.subagent.MergeAgent;
import com.dasi.qa.agent.domain.agent.service.memory.support.MemoryAgentFactory;
import com.dasi.qa.agent.domain.agent.service.memory.support.MemoryResultCleaner;
import com.dasi.qa.agent.domain.agent.service.shared.UserLlmModelProvider;
import com.dasi.qa.agent.domain.util.IIdUtil;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import com.dasi.qa.agent.types.enumeration.AgentErrorType;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.exception.AgentException;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@Slf4j
@SuppressWarnings("unchecked")
public class MemoryAgent implements IMemoryAgent {

    private static final int MAX_RETRY = 2;

    private final IAgentRepository agentRepository;
    private final UserLlmModelProvider userLlmModelProvider;
    private final IIdUtil idUtil;
    private final IJsonUtil jsonUtil;
    private final MemoryAgentFactory memoryAgentFactory;
    private final MemoryResultCleaner memoryResultCleaner;

    public MemoryAgent(IAgentRepository agentRepository,
                       UserLlmModelProvider userLlmModelProvider,
                       IIdUtil idUtil,
                       IJsonUtil jsonUtil,
                       MemoryAgentFactory memoryAgentFactory,
                       MemoryResultCleaner memoryResultCleaner) {
        this.agentRepository = agentRepository;
        this.userLlmModelProvider = userLlmModelProvider;
        this.idUtil = idUtil;
        this.jsonUtil = jsonUtil;
        this.memoryAgentFactory = memoryAgentFactory;
        this.memoryResultCleaner = memoryResultCleaner;
    }

    @Override
    public void execute(String sessionId, String userId) {
        if (!StringUtils.hasText(sessionId) || !StringUtils.hasText(userId)) {
            throw new ApiException(ResultCode.BAD_REQUEST, "记忆沉淀缺少练习或用户信息");
        }
        SessionSource sessionSource = agentRepository.getInvestContext(sessionId, userId);
        if (sessionSource == null || sessionSource.getItems() == null || sessionSource.getItems().isEmpty()) {
            log.info("【记忆画像】练习上下文为空，跳过沉淀: sessionId={}", sessionId);
            return;
        }

        ChatModel userModel = userLlmModelProvider.getUserLlmModel(userId);
        MemoryContext memoryContext = MemoryContext.builder()
                .userModel(userModel)
                .investStep((scope, investAgent) -> doInvest(scope, investAgent, sessionSource))
                .mergeStep((scope, mergeAgent) -> doMerge(scope, mergeAgent, sessionSource))
                .build();
        UntypedAgent memoryAgent = memoryAgentFactory.build(memoryContext);
        memoryAgent.invokeWithAgenticScope(Map.of(
                "sessionId", sessionId,
                "userId", userId
        ));
    }

    /**
     * 阶段一：调用 InvestAgent 从本轮作答中提取候选画像
     */
    private void doInvest(AgenticScope scope, InvestAgent investAgent, SessionSource context) {
        String retryHint = "";
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                String response = investAgent.extract(jsonUtil.toJsonString(context.getItems()), retryHint);
                List<InvestResult> candidates = jsonUtil.parseJsonArray(response, InvestResult.class);
                scope.writeState(MemoryPhase.INVEST.getScopeKey(), memoryResultCleaner.clean(candidates));
                return;
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                if (attempt == MAX_RETRY) {
                    throw new AgentException(AgentErrorType.fromException(exception), "记忆画像生成返回格式异常，请稍后重试");
                }
                log.warn("【记忆画像】MemoryAgent 调用失败，重试: attempt={}, sessionId={}", attempt + 1, context.getSessionId(), exception);
            }
        }
        scope.writeState(MemoryPhase.INVEST.getScopeKey(), List.of());
    }

    /**
     * 阶段二：调用 MergeAgent 将本轮候选画像合并进长期记忆。
     */
    private void doMerge(AgenticScope scope, MergeAgent mergeAgent, SessionSource sessionSource) {
        List<InvestResult> candidates = (List<InvestResult>) scope.readState(MemoryPhase.INVEST.getScopeKey());
        if (candidates == null || candidates.isEmpty()) {
            log.info("【记忆画像】无候选画像，跳过沉淀: sessionId={}", sessionSource.getSessionId());
            return;
        }

        Map<String, SessionSource.SessionSourceItem> itemMap = new LinkedHashMap<>();
        for (SessionSource.SessionSourceItem item : sessionSource.getItems()) {
            itemMap.put(item.getSessionItemId(), item);
        }

        // 对每一个候选画像进行 merge 操作
        for (InvestResult investResult : candidates) {
            // 拿到所有题目证据
            List<SessionSource.SessionSourceItem> evidenceItems = investResult.getEvidenceRefs().stream()
                    .map(itemMap::get)
                    .filter(Objects::nonNull)
                    .toList();
            if (evidenceItems.isEmpty()) {
                continue;
            }

            // 查询是否存在或隐藏
            Memory existing = agentRepository.findMemoryByKey(sessionSource.getUserId(), investResult.getMemoryType(), investResult.getTargetType(), investResult.getTargetKey());
            if (existing != null && MemoryStatus.HIDDEN.name().equals(existing.getStatus())) {
                continue;
            }

            LocalDateTime now = LocalDateTime.now();
            Memory memory;
            // 创建新记忆
            if (existing == null) {
                memory = Memory.builder()
                        .id(idUtil.nextId())
                        .userId(sessionSource.getUserId())
                        .memoryType(investResult.getMemoryType())
                        .targetType(investResult.getTargetType())
                        .targetKey(investResult.getTargetKey())
                        .summary(investResult.getSummary())
                        .content(investResult.getContent())
                        .supportCount(evidenceItems.size())
                        .status(MemoryStatus.ACTIVE.name())
                        .firstSeenAt(now)
                        .lastSeenAt(now)
                        .latestSessionId(sessionSource.getSessionId())
                        .latestQaSetId(sessionSource.getQaSetId())
                        .createdAt(now)
                        .updatedAt(now)
                        .build();
                agentRepository.createMemory(memory);
            }
            // 合并记忆
            else {
                memory = existing;
                MergeResult mergeResult = mergeContent(mergeAgent,
                        existing.getSummary(), existing.getContent(),
                        investResult.getSummary(), investResult.getContent(),
                        sessionSource.getSessionId());
                existing.setSummary(mergeResult.getSummary().trim());
                existing.setContent(mergeResult.getContent().trim());
                existing.setSupportCount(existing.getSupportCount() + evidenceItems.size());
                existing.setLastSeenAt(now);
                existing.setLatestSessionId(sessionSource.getSessionId());
                existing.setLatestQaSetId(sessionSource.getQaSetId());
                existing.setUpdatedAt(now);
                agentRepository.updateMemory(memory);
            }

            // 将证据存入数据库
            for (SessionSource.SessionSourceItem item : evidenceItems) {
                if (agentRepository.existsMemoryEvidence(memory.getId(), item.getSessionItemId())) {
                    continue;
                }
                MemoryEvidence memoryEvidence = MemoryEvidence.builder()
                        .id(idUtil.nextId())
                        .memoryId(memory.getId())
                        .userId(sessionSource.getUserId())
                        .sessionId(sessionSource.getSessionId())
                        .sessionItemId(item.getSessionItemId())
                        .qaSetId(sessionSource.getQaSetId())
                        .qaItemId(item.getQaItemId())
                        .moduleTag(item.getModuleTag())
                        .questionSnapshot(item.getQuestion())
                        .result(item.getResult())
                        .score(item.getScore())
                        .sourceChunkIdsJson(item.getSourceChunkIdsJson())
                        .evidenceSummary(investResult.getContent())
                        .build();
                agentRepository.createMemoryEvidence(memoryEvidence);
            }
        }
    }

    /**
     * 调用 MergeAgent 做语义合并，去重保留更具体的描述，同时合并 summary 和 content
     */
    private MergeResult mergeContent(MergeAgent mergeAgent,
                                     String existingSummary, String existingContent,
                                     String candidateSummary, String candidateContent,
                                     String sessionId) {
        if (!StringUtils.hasText(existingContent)) {
            return MergeResult.builder()
                    .summary(candidateSummary)
                    .content(candidateContent)
                    .build();
        }
        String safeExistingSummary = existingSummary != null ? existingSummary : "";
        String safeCandidateSummary = candidateSummary != null ? candidateSummary : "";
        String retryHint = "";
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                String response = mergeAgent.merge(safeExistingSummary, existingContent, safeCandidateSummary, candidateContent, retryHint);
                return jsonUtil.parseJsonObject(response, MergeResult.class);
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                if (attempt == MAX_RETRY) {
                    throw new AgentException(AgentErrorType.fromException(exception), "记忆画像合并返回格式异常，请稍后重试");
                }
                log.warn("【记忆画像】MergeAgent 调用失败，重试: attempt={}, sessionId={}", attempt + 1, sessionId, exception);
            }
        }
        return MergeResult.builder()
                .summary(existingSummary)
                .content(existingContent)
                .build();
    }

}
