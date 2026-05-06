package com.dasi.qa.agent.domain.agent.service.generate.agentic;

import com.dasi.qa.agent.types.dto.request.qa.CreateTaskRequest;
import com.dasi.qa.agent.types.dto.sse.SseEvent;

import java.util.function.Consumer;

public interface IGenerationAgent {

    void execute(String userId, CreateTaskRequest request, Consumer<SseEvent> eventSink);
}
