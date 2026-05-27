package com.dasi.qa.agent.domain.chat.service;

import com.dasi.qa.agent.types.dto.request.chat.TempChatRequest;
import com.dasi.qa.agent.types.dto.response.chat.TempChatResponse;

public interface IChatService {

    TempChatResponse tempChat(TempChatRequest request);

}
