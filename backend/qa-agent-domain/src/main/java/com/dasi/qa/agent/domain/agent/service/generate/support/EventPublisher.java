package com.dasi.qa.agent.domain.agent.service.generate.support;

import com.dasi.qa.agent.domain.agent.model.enumeration.ErrorType;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GenerationStage;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GenerationStatus;
import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.model.sse.SseEvent;
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

    public void publishEvent(GenerationStage stage, GenerationStatus status, String message, int currentTokens) {
        SseEvent event = SseEvent.builder()
                .taskId(taskId)
                .stage(stage.name())
                .status(status.name())
                .message(message)
                .timestamp(System.currentTimeMillis())
                .currentTokens(currentTokens)
                .totalTokens(totalTokens.get())
                .build();
        log.info("[task={}] [stage={}] {}", taskId, stage, message);
        agentRepository.appendTaskMessage(taskId, stage, message);
        eventSink.accept(event);
    }

    public void publishFailure(ErrorType errorType, String message) {
        String errorMessage = message == null || message.isBlank() ? errorType.name() : message;
        agentRepository.markTaskFailed(taskId, errorType, errorMessage);
        publishEvent(GenerationStage.FAILED, GenerationStatus.FAILED, errorMessage, 0);
    }

    public void updateTaskStage(GenerationStatus status, GenerationStage stage) {
        agentRepository.updateTaskStage(taskId, status, stage);
    }

    public int totalTokens() {
        return totalTokens.get();
    }

}
