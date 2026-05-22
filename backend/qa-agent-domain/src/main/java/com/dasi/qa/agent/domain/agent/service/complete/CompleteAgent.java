package com.dasi.qa.agent.domain.agent.service.complete;

import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.complete.model.context.CompleteContext;
import com.dasi.qa.agent.domain.agent.service.complete.model.exception.CompleteException;
import com.dasi.qa.agent.domain.agent.service.complete.model.result.CompleteResult;
import com.dasi.qa.agent.domain.agent.service.complete.subagent.CompleteSubAgent;
import com.dasi.qa.agent.domain.agent.service.shared.RagEvidenceProvider;
import com.dasi.qa.agent.domain.agent.service.shared.UserLlmModelProvider;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import com.dasi.qa.agent.domain.util.IMqUtil;
import com.dasi.qa.agent.types.enumeration.AgentErrorType;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
            throw new CompleteException(AgentErrorType.fromException(exception), "题目创建补全失败: " + exception.getMessage());
        }
    }

    private CompleteResult doComplete(CompleteContext context, String userId) {
        // 1. 拿到用户模型
        ChatModel userModel = userLlmModelProvider.getUserLlmModel(userId);

        // 2. 构造填充智能体
        CompleteSubAgent completeAgent = AiServices.builder(CompleteSubAgent.class)
                .chatModel(userModel)
                .build();

        // 3. 根据问题搜索相关资料
        String evidence = jsonUtil.toJsonString(ragEvidenceProvider.searchByQuestion(userId, context.getDocumentIds(), context.getQuestion()));
        String retryHint = "";
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                // 4. 执行智能体，拿到返回值
                String response = completeAgent.complete(
                        context.getQuestion(),
                        evidence,
                        jsonUtil.toJsonString(context.getUserProfile()),
                        context.getAnswerStyle(),
                        retryHint
                );
                return jsonUtil.parseJsonObject(response, CompleteResult.class);
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                if (attempt == MAX_RETRY) {
                    throw new CompleteException(AgentErrorType.fromException(exception), "CompleteAgent 返回格式异常: " + exception.getMessage());
                }
                log.warn("【题目创建补全】CompleteAgent 调用失败，重试: attempt={}, qaItemId={}", attempt + 1, context.getQaItemId(), exception);
            }
        }
        throw new CompleteException(AgentErrorType.INVALID_RESPONSE, "CompleteAgent 未返回有效结果");
    }

}
