package com.dasi.qa.agent.domain.agent.shared.sse;

import com.dasi.qa.agent.domain.agent.shared.enumeration.ErrorType;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GeneratePhase;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GenerateStatus;
import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Slf4j
public class EventPublisher {

    private final IAgentRepository agentRepository;
    private final String taskId;
    private final Consumer<SseEvent> eventSink;
    private final AtomicInteger totalTokens;

    public EventPublisher(IAgentRepository agentRepository,
                          String taskId,
                          Consumer<SseEvent> eventSink,
                          AtomicInteger totalTokens) {
        this.agentRepository = agentRepository;
        this.taskId = taskId;
        this.eventSink = eventSink;
        this.totalTokens = totalTokens;
    }

    public void publishEvent(GeneratePhase phase, GenerateStatus status, String message, int currentTokens) {
        SseEvent sseEvent = SseEvent.builder()
                .taskId(taskId)
                .phase(phase)
                .status(status)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .currentTokens(currentTokens)
                .totalTokens(totalTokens.get())
                .build();
        log.info("[task={}] [phase={}] {}", taskId, phase, message);
        agentRepository.appendTaskMessage(taskId, phase, message);
        eventSink.accept(sseEvent);
    }

    public void publishFailure(ErrorType errorType, String errorMessage) {
        agentRepository.markTaskFailed(taskId, errorType, errorMessage);
        publishEvent(GeneratePhase.FAIL, GenerateStatus.UNSOLVED, errorMessage, 0);
    }

    public void publishCanceled(ErrorType errorType, String errorMessage) {
        agentRepository.markTaskFailed(taskId, errorType, errorMessage);
        publishEvent(GeneratePhase.FAIL, GenerateStatus.CANCELED, errorMessage, 0);
    }

}
