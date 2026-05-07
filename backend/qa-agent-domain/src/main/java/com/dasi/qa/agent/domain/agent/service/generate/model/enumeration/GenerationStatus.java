package com.dasi.qa.agent.domain.agent.service.generate.model.enumeration;

import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.result.ResultCode;

public enum GenerationStatus {
    PENDING,
    PROCESSING,
    COMPLETED,
    FAILED;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }

    public static GenerationStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        try {
            return GenerationStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
    }
}
