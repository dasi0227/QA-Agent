package com.dasi.qa.agent.domain.agent.service.generate;

import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
import com.dasi.qa.agent.domain.agent.shared.sse.SseEvent;

import java.util.function.Consumer;

public interface IGenerateAgent {

    void execute(String userId, CreateQaSetRequest request, Consumer<SseEvent> sseEventHandler);

}
