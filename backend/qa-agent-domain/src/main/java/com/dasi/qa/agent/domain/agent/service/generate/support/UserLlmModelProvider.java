package com.dasi.qa.agent.domain.agent.service.generate.support;

import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.generate.model.exception.GenerateException;
import com.dasi.qa.agent.domain.agent.shared.enumeration.ErrorType;
import com.dasi.qa.agent.domain.agent.shared.vo.UserLlmModelVO;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * 用户 LLM 模型提供器，读取用户配置的 base_url/api_key/model_name，构建 ChatModel 实例。
 */
@Component
public class UserLlmModelProvider {

    private final IAgentRepository agentRepository;

    public UserLlmModelProvider(IAgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    public ChatModel getUserLlmModel(String userId, ChatModelListener tokenListener) {
        UserLlmModelVO userLlmModelVO = agentRepository.getUserLlmModel(userId);
        if (isNotValid(userLlmModelVO)) {
            throw new GenerateException(ErrorType.LLM_NOT_CONFIGURED, "用户未配置 LLM 接入信息，请先在 Profile 中填写 base_url、api_key 和 model_name");
        }

        return OpenAiChatModel.builder()
                .baseUrl(userLlmModelVO.getBaseUrl())
                .apiKey(userLlmModelVO.getApiKey())
                .modelName(userLlmModelVO.getModelName())
                .timeout(Duration.ofSeconds(60))
                .maxRetries(1)
                .listeners(List.of(tokenListener))
                .build();
    }

    private boolean isNotValid(UserLlmModelVO userLlmModelVO) {
        return userLlmModelVO == null
                || !StringUtils.hasText(userLlmModelVO.getBaseUrl())
                || !StringUtils.hasText(userLlmModelVO.getApiKey())
                || !StringUtils.hasText(userLlmModelVO.getModelName());
    }

}
