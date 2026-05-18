package com.dasi.qa.agent.types.enumeration;

public enum AgentErrorType {
    NETWORK_ERROR,
    RATE_LIMITED,
    AUTH_FAILURE,
    INVALID_RESPONSE,
    CONTENT_FILTERED,
    LLM_NOT_CONFIGURED,
    PRACTICE_SESSION_NOT_COMPLETED,
    UNKNOWN;

    public static AgentErrorType fromException(Throwable throwable) {
        String message = throwable == null || throwable.getMessage() == null ? "" : throwable.getMessage().toLowerCase();
        if (message.contains("401") || message.contains("unauthorized") || message.contains("api key")) {
            return AUTH_FAILURE;
        }
        if (message.contains("rate limit") || message.contains("429")) {
            return RATE_LIMITED;
        }
        if (message.contains("parse") || message.contains("json")) {
            return INVALID_RESPONSE;
        }
        if (message.contains("timeout") || message.contains("connect")) {
            return NETWORK_ERROR;
        }
        return UNKNOWN;
    }

}
