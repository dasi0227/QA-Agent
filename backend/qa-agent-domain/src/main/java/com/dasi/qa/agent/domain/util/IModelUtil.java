package com.dasi.qa.agent.domain.util;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;

public interface IModelUtil {

    ChatModel getAgentModel(String userId);

    ChatModel getAgentModel(String userId, ChatModelListener tokenListener);

    ChatModel getChatModel(String userId);
}
