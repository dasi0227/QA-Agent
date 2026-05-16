package com.dasi.qa.agent.domain.agent.service.assess.support;

import com.dasi.qa.agent.domain.agent.model.enumeration.ErrorType;
import com.dasi.qa.agent.domain.agent.model.vo.UserLlmModelVO;
import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.assess.model.exception.AssessException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;

@Component
public class AssessLlmModelProvider {

    private final IAgentRepository agentRepository;

    public AssessLlmModelProvider(IAgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    public ChatModel getUserLlmModel(String userId) {
        UserLlmModelVO userLlmModelVO = agentRepository.getUserLlmModel(userId);
        if (isNotValid(userLlmModelVO)) {
            throw new AssessException(ErrorType.LLM_NOT_CONFIGURED, "用户未配置 LLM 接入信息，请先在 Profile 中填写 base_url、api_key 和 model_name");
        }
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
