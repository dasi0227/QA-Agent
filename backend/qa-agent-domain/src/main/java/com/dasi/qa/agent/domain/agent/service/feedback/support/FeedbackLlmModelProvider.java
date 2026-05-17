package com.dasi.qa.agent.domain.agent.service.feedback.support;

import com.dasi.qa.agent.domain.agent.model.enumeration.ErrorType;
import com.dasi.qa.agent.domain.agent.model.vo.UserLlmModelVO;
import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.feedback.model.exception.FeedbackException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * FeedbackLlmModelProvider 负责用用户 Profile 中的配置创建反馈链路模型。
 */
@Component
public class FeedbackLlmModelProvider {

    private final IAgentRepository agentRepository;

    public FeedbackLlmModelProvider(IAgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    /**
     * 获取用户专属 LLM 模型，配置缺失时抛出业务异常。
     */
    public ChatModel getUserLlmModel(String userId) {
        // 1. 读取用户模型配置
        UserLlmModelVO userLlmModelVO = agentRepository.getUserLlmModel(userId);
        // 2. 校验 baseUrl、apiKey 和 modelName
        if (isNotValid(userLlmModelVO)) {
            throw new FeedbackException(ErrorType.LLM_NOT_CONFIGURED, "用户未配置 LLM 接入信息，请先在 Profile 中填写 base_url、api_key 和 model_name");
        }
        // 3. 构建 OpenAI 兼容 ChatModel
        return OpenAiChatModel.builder()
                .baseUrl(userLlmModelVO.getBaseUrl())
                .apiKey(userLlmModelVO.getApiKey())
                .modelName(userLlmModelVO.getModelName())
                .timeout(Duration.ofSeconds(60))
                .maxRetries(1)
                .build();
    }

    private boolean isNotValid(UserLlmModelVO userLlmModelVO) {
        return userLlmModelVO == null
                || !StringUtils.hasText(userLlmModelVO.getBaseUrl())
                || !StringUtils.hasText(userLlmModelVO.getApiKey())
                || !StringUtils.hasText(userLlmModelVO.getModelName());
    }
}
