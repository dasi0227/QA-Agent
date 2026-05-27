package com.dasi.qa.agent.domain.chat.service;

import com.dasi.qa.agent.domain.agent.service.shared.UserLlmModelProvider;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.types.dto.request.chat.TempChatRequest;
import com.dasi.qa.agent.types.dto.response.chat.TempChatResponse;
import com.dasi.qa.agent.types.enumeration.AgentErrorType;
import com.dasi.qa.agent.types.exception.AgentException;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ChatService implements IChatService {

    private final IContextUtil contextUtil;
    private final UserLlmModelProvider userLlmModelProvider;
    private final ChatMemoryProvider chatMemoryProvider;

    public ChatService(IContextUtil contextUtil,
                       UserLlmModelProvider userLlmModelProvider,
                       ChatMemoryProvider chatMemoryProvider) {
        this.contextUtil = contextUtil;
        this.userLlmModelProvider = userLlmModelProvider;
        this.chatMemoryProvider = chatMemoryProvider;
    }

    private interface TempChatBot {
        @SystemMessage(fromResource = "prompt/chat/temp-chat.txt")
        String chat(@MemoryId String tempChatId, @UserMessage String message);
    }

    @Override
    public TempChatResponse tempChat(TempChatRequest request) {
        String userId = contextUtil.getUserId();
        try {
            ChatModel userModel = userLlmModelProvider.getUserLlmModel(userId);
            TempChatBot agent = AiServices.builder(TempChatBot.class)
                    .chatModel(userModel)
                    .chatMemoryProvider(chatMemoryProvider)
                    .build();
            String content = agent.chat(request.getTempChatId().trim(), request.getMessage().trim());
            return TempChatResponse.builder()
                    .role("assistant")
                    .content(content)
                    .build();
        } catch (AgentException exception) {
            throw exception;
        } catch (Exception exception) {
            log.error("【临时对话】Dasi 回复失败: userId={}, tempChatId={}", userId, request.getTempChatId(), exception);
            throw new AgentException(AgentErrorType.fromException(exception), "Dasi 暂时没有回复，请稍后再试");
        }
    }

}
