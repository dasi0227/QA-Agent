package com.dasi.qa.agent.domain.agent.shared.sse;

import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GenerationStage;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GenerationStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SseEvent {

    private String taskId;
    private GenerationStage stage;
    private GenerationStatus status;
    private String message;
    private long timestamp;
    private int currentTokens;
    private int totalTokens;
}
