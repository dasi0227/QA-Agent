package com.dasi.qa.agent.interfaces.handler;

import com.dasi.qa.agent.domain.agent.service.shared.SseEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.function.Consumer;

public class SseEventHandler implements Consumer<SseEvent> {

    private final SseEmitter emitter;

    public SseEventHandler(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void accept(SseEvent sseEvent) {
        try {
            emitter.send(SseEmitter.event().data(sseEvent));
            if (sseEvent.isCompleted()) {
                emitter.complete();
            }
        } catch (Exception exception) {
            emitter.completeWithError(exception);
        }
    }
}
