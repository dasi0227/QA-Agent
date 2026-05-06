package com.dasi.qa.agent.types.dto.sse;

public record SseEvent(
        String taskId,
        String stage,
        String status,
        String message,
        long timestamp,
        SseTokens tokens
) {

    public static SseEvent of(String taskId, String stage, String status,
                              String message, long timestamp, int currentTokens, int totalTokens) {
        return new SseEvent(taskId, stage, status, message,
                timestamp, new SseTokens(currentTokens, totalTokens));
    }
}
