package com.dasi.qa.agent.domain.agent.service.shared;

import com.dasi.qa.agent.domain.agent.model.vo.UserLlmModelVO;
import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import com.dasi.qa.agent.types.exception.LlmConfigException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

/**
 * 统一读取用户 LLM 配置并构建 ChatModel。
 */
@Component
public class UserLlmModelProvider {

    private final IAgentRepository agentRepository;

    public UserLlmModelProvider(IAgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    public ChatModel getUserLlmModel(String userId) {
        return buildUserLlmModel(userId, null);
    }

    public ChatModel getUserLlmModel(String userId, ChatModelListener tokenListener) {
        return buildUserLlmModel(userId, tokenListener);
    }

    private ChatModel buildUserLlmModel(String userId, ChatModelListener tokenListener) {
        UserLlmModelVO userLlmModelVO = agentRepository.getUserLlmModel(userId);
        if (isNotValid(userLlmModelVO)) {
            throw new LlmConfigException(ResultCode.LLM_NOT_CONFIGURED, "用户未配置 LLM 接入信息，请先在 Profile 中填写 base_url、api_key 和 model_name");
        }

        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(userLlmModelVO.getBaseUrl())
                .apiKey(userLlmModelVO.getApiKey())
                .modelName(userLlmModelVO.getModelName())
                .timeout(Duration.ofSeconds(60))
                .maxRetries(1);
        if (tokenListener != null) {
            builder.listeners(List.of(tokenListener));
        }
        return builder.build();
    }

    private boolean isNotValid(UserLlmModelVO userLlmModelVO) {
        return userLlmModelVO == null
                || !StringUtils.hasText(userLlmModelVO.getBaseUrl())
                || !StringUtils.hasText(userLlmModelVO.getApiKey())
                || !StringUtils.hasText(userLlmModelVO.getModelName());
    }
}
