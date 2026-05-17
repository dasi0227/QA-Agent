package com.dasi.qa.agent.domain.agent.model.sse;

import com.dasi.qa.agent.types.enumeration.AgentErrorType;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GeneratePhase;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GenerateStatus;
import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Slf4j
public class EventPublisher {

    private final IAgentRepository agentRepository;
    private final String taskId;
    private final String userId;
    private final Consumer<SseEvent> eventSink;
    private final AtomicInteger totalTokens;
    private final IJsonUtil jsonUtil;

    public EventPublisher(IAgentRepository agentRepository,
                          String taskId,
                          String userId,
                          Consumer<SseEvent> eventSink,
                          AtomicInteger totalTokens,
                          IJsonUtil jsonUtil) {
        this.agentRepository = agentRepository;
        this.taskId = taskId;
        this.userId = userId;
        this.eventSink = eventSink;
        this.totalTokens = totalTokens;
        this.jsonUtil = jsonUtil;
    }

    public void publishEvent(GeneratePhase phase, GenerateStatus status, String message, int currentTokens) {
        SseEvent sseEvent = SseEvent.builder()
                .taskId(taskId)
                .stage(phase.getGenerateStage())
                .status(status.name())
                .message(message)
                .timestamp(System.currentTimeMillis())
                .currentTokens(currentTokens)
                .totalTokens(totalTokens.get())
                .isCompleted(status.isTerminated())
                .build();
        log.info("【SSE事件】事件已发送: stage={}, message={}", phase.getGenerateStage(), message);
        agentRepository.appendTaskMessage(taskId, userId, phase.getGenerateStage(), message, jsonUtil.toJsonString(sseEvent));
        eventSink.accept(sseEvent);
    }

    public void publishFailure(AgentErrorType agentErrorType, String errorMessage) {
        agentRepository.markTaskFailed(taskId, agentErrorType, errorMessage);
        publishEvent(GeneratePhase.FAIL, GenerateStatus.UNSOLVED, errorMessage, 0);
    }

    public void publishCanceled(AgentErrorType agentErrorType, String errorMessage) {
        agentRepository.markTaskFailed(taskId, agentErrorType, errorMessage);
        publishEvent(GeneratePhase.FAIL, GenerateStatus.CANCELED, errorMessage, 0);
    }

    public void publishProgress(String stage, String message) {
        SseEvent sseEvent = SseEvent.builder()
                .taskId(taskId)
                .stage(stage)
                .status(GenerateStatus.PROCESSING.name())
                .message(message)
                .timestamp(System.currentTimeMillis())
                .currentTokens(0)
                .totalTokens(totalTokens.get())
                .isCompleted(false)
                .build();
        log.info("【SSE事件】事件已发送: stage={}, message={}", stage, message);
        agentRepository.appendTaskMessage(taskId, userId, stage, message, jsonUtil.toJsonString(sseEvent));
        eventSink.accept(sseEvent);
    }

}
