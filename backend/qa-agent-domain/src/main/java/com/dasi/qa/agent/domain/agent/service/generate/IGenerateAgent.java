package com.dasi.qa.agent.domain.agent.service.generate;

import com.dasi.qa.agent.types.dto.request.qa.CreateTaskRequest;
import com.dasi.qa.agent.types.dto.sse.SseEvent;

import java.util.function.Consumer;

public interface IGenerateAgent {

    void execute(String userId, CreateTaskRequest request, Consumer<SseEvent> eventSink);

}
