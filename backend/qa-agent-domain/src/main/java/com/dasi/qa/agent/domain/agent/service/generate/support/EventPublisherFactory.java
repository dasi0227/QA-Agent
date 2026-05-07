package com.dasi.qa.agent.domain.agent.service.generate.support;

import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.types.dto.sse.SseEvent;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Component
public class EventPublisherFactory {

    private final IAgentRepository agentRepository;

    public EventPublisherFactory(IAgentRepository agentRepository) {
        this.agentRepository = agentRepository;
    }

    public EventPublisher create(String taskId, Consumer<SseEvent> eventSink, AtomicInteger totalTokens) {
        return new EventPublisher(agentRepository, taskId, eventSink, totalTokens);
    }
}
