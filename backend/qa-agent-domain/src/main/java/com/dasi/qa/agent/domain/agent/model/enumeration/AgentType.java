package com.dasi.qa.agent.domain.agent.model.enumeration;

import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.result.ResultCode;

public enum AgentType {
    GENERATION,
    FEEDBACK,
    SCORING;

    public static AgentType fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        try {
            return AgentType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
    }
}
