package com.dasi.qa.agent.domain.agent.shared.sse;

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
    private String phase;
    private String status;
    private String message;
    private long timestamp;
    private int currentTokens;
    private int totalTokens;
    private boolean isCompleted;
}
