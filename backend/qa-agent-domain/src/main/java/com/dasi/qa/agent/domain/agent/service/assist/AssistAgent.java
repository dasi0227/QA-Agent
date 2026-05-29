package com.dasi.qa.agent.domain.agent.service.assist;

import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.assist.model.context.AssistContext;
import com.dasi.qa.agent.domain.agent.service.assist.model.exception.AssistException;
import com.dasi.qa.agent.domain.agent.service.assist.model.result.AssistResult;
import com.dasi.qa.agent.domain.agent.service.assist.subagent.AssistSubAgent;
import com.dasi.qa.agent.domain.agent.service.shared.UserLlmModelProvider;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import com.dasi.qa.agent.types.enumeration.AgentErrorType;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class AssistAgent implements IAssistAgent {

    private static final int MAX_RETRY = 2;

    private final IAgentRepository agentRepository;
    private final UserLlmModelProvider userLlmModelProvider;
    private final IJsonUtil jsonUtil;

    public AssistAgent(IAgentRepository agentRepository,
                       UserLlmModelProvider userLlmModelProvider,
                       IJsonUtil jsonUtil) {
        this.agentRepository = agentRepository;
        this.userLlmModelProvider = userLlmModelProvider;
        this.jsonUtil = jsonUtil;
    }

    @Override
    public void execute(String qaItemId, String userId) {
        try {
            AssistContext assistContext = agentRepository.getAssistContext(qaItemId, userId);
            AssistResult assistResult = doAssist(assistContext, userId);
            agentRepository.saveAssistResult(qaItemId, userId, assistResult);
            log.info("【题目辅助补全】补全成功: qaItemId={}", qaItemId);
        } catch (AssistException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("【题目辅助补全】补全失败: qaItemId={}", qaItemId, exception);
            throw new AssistException(AgentErrorType.fromException(exception), "题目辅助补全失败，请稍后重试");
        }
    }

    private AssistResult doAssist(AssistContext context, String userId) {
        ChatModel userModel = userLlmModelProvider.getUserLlmModel4Agent(userId);
        AssistSubAgent assistAgent = AiServices.builder(AssistSubAgent.class)
                .chatModel(userModel)
                .build();
        String retryHint = "";
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                String response = assistAgent.assist(
                        context.getQuestion(),
                        context.getStandardAnswer(),
                        context.getKnowledgeNote(),
                        context.getModuleTag(),
                        retryHint
                );
                return jsonUtil.parseJsonObject(response, AssistResult.class);
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                if (attempt == MAX_RETRY) {
                    throw new AssistException(AgentErrorType.fromException(exception), "题目辅助补全返回格式异常，请重试");
                }
                log.warn("【题目辅助补全】AssistAgent 调用失败，重试: attempt={}, qaItemId={}", attempt + 1, context.getQaItemId(), exception);
            }
        }
        throw new AssistException(AgentErrorType.INVALID_RESPONSE, "题目辅助补全未返回有效结果，请重试");
    }

}
