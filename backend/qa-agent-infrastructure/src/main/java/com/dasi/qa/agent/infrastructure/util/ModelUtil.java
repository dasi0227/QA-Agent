package com.dasi.qa.agent.infrastructure.util;

import com.dasi.qa.agent.domain.util.IModelUtil;
import com.dasi.qa.agent.infrastructure.persistent.entity.UserProfile;
import com.dasi.qa.agent.infrastructure.persistent.mapper.mysql.UserProfileMapper;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import com.dasi.qa.agent.types.exception.LlmConfigException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;

@Service
public class ModelUtil implements IModelUtil {

    private final UserProfileMapper userProfileMapper;

    public ModelUtil(UserProfileMapper userProfileMapper) {
        this.userProfileMapper = userProfileMapper;
    }

    @Override
    public ChatModel getAgentModel(String userId) {
        return build(userId, null, true);
    }

    @Override
    public ChatModel getAgentModel(String userId, ChatModelListener tokenListener) {
        return build(userId, tokenListener, true);
    }

    @Override
    public ChatModel getChatModel(String userId) {
        return build(userId, null, false);
    }

    private ChatModel build(String userId, ChatModelListener tokenListener, boolean jsonMode) {
        UserProfile profile = userProfileMapper.selectById(userId);
        if (isNotValid(profile)) {
            throw new LlmConfigException(ResultCode.LLM_NOT_CONFIGURED, "用户未配置 LLM 接入信息，请先在 Profile 中填写 base_url、api_key 和 model_name");
        }

        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(profile.getLlmBaseUrl())
                .apiKey(profile.getLlmApiKey())
                .modelName(profile.getLlmModelName())
                .timeout(Duration.ofSeconds(60))
                .maxRetries(1)
                .responseFormat(jsonMode ? "json_object" : "text");
        if (tokenListener != null) {
            builder.listeners(List.of(tokenListener));
        }
        return builder.build();
    }

    private boolean isNotValid(UserProfile profile) {
        return profile == null
                || !StringUtils.hasText(profile.getLlmBaseUrl())
                || !StringUtils.hasText(profile.getLlmApiKey())
                || !StringUtils.hasText(profile.getLlmModelName());
    }
}
