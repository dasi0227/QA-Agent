package com.dasi.qa.agent.domain.agent.shared.sse;

import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GeneratePhase;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GenerateStatus;
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
    private GeneratePhase phase;
    private GenerateStatus status;
    private String message;
    private long timestamp;
    private int currentTokens;
    private int totalTokens;
}
