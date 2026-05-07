package com.dasi.qa.agent.domain.agent.service.generate.model.enumeration;

import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.result.ResultCode;

public enum GenerationStage {
    PENDING,
    DECIDING,
    PLANNING,
    CREATING,
    VALIDATING,
    SUMMARIZING,
    COMPLETED,
    FAILED;

    public static GenerationStage fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        try {
            return GenerationStage.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
    }
}
