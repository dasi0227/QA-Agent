package com.dasi.qa.agent.interfaces.handler;

import com.dasi.qa.agent.domain.agent.shared.sse.SseEvent;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.function.Consumer;

public class SseEventHandler implements Consumer<SseEvent> {

    private final SseEmitter emitter;

    public SseEventHandler(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void accept(SseEvent sseEvent) {
        try {
            emitter.send(SseEmitter.event()
                    .name(sseEvent.getPhase().getGenerateStage())
                    .data(sseEvent));
            if (sseEvent.getStatus().isTerminated()) {
                emitter.complete();
            }
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
    }
}
