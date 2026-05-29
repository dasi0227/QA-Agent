package com.dasi.qa.agent.domain.agent.service.complete;

import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.complete.model.context.CompleteContext;
import com.dasi.qa.agent.domain.agent.service.complete.model.exception.CompleteException;
import com.dasi.qa.agent.domain.agent.service.complete.model.result.CompleteResultWithoutAnswer;
import com.dasi.qa.agent.domain.agent.service.complete.model.result.CompleteResult;
import com.dasi.qa.agent.domain.agent.service.complete.subagent.CompleteSubAgentWithAnswer;
import com.dasi.qa.agent.domain.agent.service.complete.subagent.CompleteSubAgentWithoutAnswer;
import com.dasi.qa.agent.domain.agent.service.shared.RagEvidenceProvider;
import com.dasi.qa.agent.domain.agent.service.shared.UserLlmModelProvider;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import com.dasi.qa.agent.domain.util.IMqUtil;
import com.dasi.qa.agent.types.enumeration.AgentErrorType;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class CompleteAgent implements ICompleteAgent {

    private static final int MAX_RETRY = 2;

    private final IAgentRepository agentRepository;
    private final UserLlmModelProvider userLlmModelProvider;
    private final RagEvidenceProvider ragEvidenceProvider;
    private final IJsonUtil jsonUtil;
    private final IMqUtil mqUtil;

    public CompleteAgent(IAgentRepository agentRepository,
                         UserLlmModelProvider userLlmModelProvider,
                         RagEvidenceProvider ragEvidenceProvider,
                         IJsonUtil jsonUtil,
                         IMqUtil mqUtil) {
        this.agentRepository = agentRepository;
        this.userLlmModelProvider = userLlmModelProvider;
        this.ragEvidenceProvider = ragEvidenceProvider;
        this.jsonUtil = jsonUtil;
        this.mqUtil = mqUtil;
    }

    @Override
    public void execute(String qaItemId, String userId) {
        try {
            // 调用智能体获取补全结果
            CompleteContext completeContext = agentRepository.getCompleteContext(qaItemId, userId);
            CompleteResult completeResult = doComplete(completeContext, userId);

            // 保存到数据库中
            agentRepository.saveCompleteResult(qaItemId, userId, completeResult);

            // 发送协助消息
            mqUtil.sendAssistMessage(qaItemId, Map.of("qaItemId", qaItemId, "userId", userId));
            log.info("【题目创建补全】补全成功: qaItemId={}", qaItemId);
        } catch (CompleteException exception) {
            agentRepository.markQaItemCompleteFailed(qaItemId, userId);
            throw exception;
        } catch (Exception exception) {
            agentRepository.markQaItemCompleteFailed(qaItemId, userId);
            log.error("【题目创建补全】补全失败: qaItemId={}", qaItemId, exception);
            throw new CompleteException(AgentErrorType.fromException(exception), "题目创建补全失败，请稍后重试");
        }
    }

    private CompleteResult doComplete(CompleteContext context, String userId) {
        // 1. 拿到用户模型
        ChatModel userModel = userLlmModelProvider.getUserLlmModel4Agent(userId);

        // 2. 根据问题搜索相关资料。空引用题集不扩大到用户全部资料。
        List<RagEvidenceProvider.RagEvidenceItem> ragEvidenceItems = context.getDocumentIds() == null || context.getDocumentIds().isEmpty()
                ? List.of()
                : ragEvidenceProvider.searchByQuestion(userId, context.getDocumentIds(), context.getQuestion());
        String evidence = jsonUtil.toJsonString(ragEvidenceItems);

        if (StringUtils.hasText(context.getAnswer())) {
            CompleteSubAgentWithAnswer completeAgent = AiServices.builder(CompleteSubAgentWithAnswer.class)
                    .chatModel(userModel)
                    .build();
            return doCompleteWithAnswer(completeAgent, context, evidence);
        } else {
            CompleteSubAgentWithoutAnswer completeAgent = AiServices.builder(CompleteSubAgentWithoutAnswer.class)
                    .chatModel(userModel)
                    .build();
            return doCompleteWithoutAnswer(completeAgent, context, evidence);
        }
    }

    private CompleteResult doCompleteWithoutAnswer(CompleteSubAgentWithoutAnswer completeAgent, CompleteContext completeContext, String evidence) {
        String retryHint = "";
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                String response = completeAgent.complete(
                        completeContext.getQuestion(),
                        evidence,
                        jsonUtil.toJsonString(completeContext.getUserProfile()),
                        completeContext.getAnswerStyle(),
                        retryHint
                );
                return jsonUtil.parseJsonObject(response, CompleteResult.class);
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                if (attempt == MAX_RETRY) {
                    throw new CompleteException(AgentErrorType.fromException(exception), "题目创建补全返回格式异常，请重试");
                }
                log.warn("【题目创建补全】CompleteAgent 调用失败，重试: attempt={}, qaItemId={}", attempt + 1, completeContext.getQaItemId(), exception);
            }
        }
        throw new CompleteException(AgentErrorType.INVALID_RESPONSE, "题目创建补全未返回有效结果，请重试");
    }

    private CompleteResult doCompleteWithAnswer(CompleteSubAgentWithAnswer completeAgent, CompleteContext completeContext, String evidence) {
        String retryHint = "";
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                String answer = completeContext.getAnswer().trim();
                String response = completeAgent.complete(
                        completeContext.getQuestion(),
                        answer,
                        evidence,
                        jsonUtil.toJsonString(completeContext.getUserProfile()),
                        completeContext.getAnswerStyle(),
                        retryHint
                );
                CompleteResultWithoutAnswer result = jsonUtil.parseJsonObject(response, CompleteResultWithoutAnswer.class);
                return CompleteResult.builder()
                        .answer(answer)
                        .knowledgeNote(result.getKnowledgeNote())
                        .moduleTag(result.getModuleTag())
                        .difficulty(result.getDifficulty())
                        .sourceReliable(result.getSourceReliable())
                        .sourceChunkIds(result.getSourceChunkIds())
                        .build();
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                if (attempt == MAX_RETRY) {
                    throw new CompleteException(AgentErrorType.fromException(exception), "题目基于标准答案补全返回格式异常，请重试");
                }
                log.warn("【题目创建补全】CompleteSubAgentWithAnswer 调用失败，重试: attempt={}, qaItemId={}", attempt + 1, completeContext.getQaItemId(), exception);
            }
        }
        throw new CompleteException(AgentErrorType.INVALID_RESPONSE, "题目基于标准答案补全未返回有效结果，请重试");
    }

}
