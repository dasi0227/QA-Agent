package com.dasi.qa.agent.domain.agent.service.memory;

import com.dasi.qa.agent.domain.agent.service.memory.model.context.MemoryContext;
import com.dasi.qa.agent.domain.agent.service.memory.model.result.MemoryCandidateResult;
import com.dasi.qa.agent.domain.agent.service.memory.subagent.MemorySubAgent;
import com.dasi.qa.agent.domain.agent.service.memory.support.MemoryResultCleaner;
import com.dasi.qa.agent.domain.agent.service.shared.UserLlmModelProvider;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import com.dasi.qa.agent.types.enumeration.AgentErrorType;
import com.dasi.qa.agent.types.exception.AgentException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class MemoryAgent implements IMemoryAgent {

    private static final int MAX_RETRY = 2;

    private final UserLlmModelProvider userLlmModelProvider;
    private final IJsonUtil jsonUtil;
    private final MemoryResultCleaner memoryResultCleaner;

    public MemoryAgent(UserLlmModelProvider userLlmModelProvider,
                       IJsonUtil jsonUtil,
                       MemoryResultCleaner memoryResultCleaner) {
        this.userLlmModelProvider = userLlmModelProvider;
        this.jsonUtil = jsonUtil;
        this.memoryResultCleaner = memoryResultCleaner;
    }

    @Override
    public List<MemoryCandidateResult> extract(MemoryContext context, String userId) {
        ChatModel userModel = userLlmModelProvider.getUserLlmModel(userId);
        MemorySubAgent memoryAgent = AiServices.builder(MemorySubAgent.class)
                .chatModel(userModel)
                .build();
        String retryHint = "";
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                String response = memoryAgent.extract(
                        context.getQaSetTitle(),
                        context.getStatsJson(),
                        context.getItemsJson(),
                        context.getExistingMemoriesJson(),
                        retryHint
                );
                List<MemoryCandidateResult> candidates = jsonUtil.parseJsonArray(response, MemoryCandidateResult.class);
                return memoryResultCleaner.clean(candidates);
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                if (attempt == MAX_RETRY) {
                    throw new AgentException(AgentErrorType.fromException(exception), "记忆画像生成返回格式异常，请稍后重试");
                }
                log.warn("【记忆画像】MemoryAgent 调用失败，重试: attempt={}, sessionId={}", attempt + 1, context.getSessionId(), exception);
            }
        }
        return List.of();
    }
}
