package com.dasi.qa.agent.domain.agent.model.enumeration;

import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.result.ResultCode;

public enum ErrorType {
    NETWORK_ERROR,
    RATE_LIMITED,
    AUTH_FAILURE,
    INVALID_RESPONSE,
    CONTENT_FILTERED,
    ALL_REJECTED,
    LLM_NOT_CONFIGURED,
    UNKNOWN;

    public static ErrorType fromException(Throwable throwable) {
        String message = throwable == null || throwable.getMessage() == null
                ? ""
                : throwable.getMessage().toLowerCase();
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

    public static ErrorType fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        try {
            return ErrorType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
    }
}
