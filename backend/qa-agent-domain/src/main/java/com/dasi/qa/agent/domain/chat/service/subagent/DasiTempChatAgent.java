package com.dasi.qa.agent.domain.chat.service.subagent;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;

public interface DasiTempChatAgent {

    @SystemMessage(fromResource = "prompt/chat/temp-chat.txt")
    String chat(@MemoryId String tempChatId, @UserMessage String message);

}
