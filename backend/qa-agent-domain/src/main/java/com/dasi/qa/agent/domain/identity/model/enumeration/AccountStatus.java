package com.dasi.qa.agent.domain.identity.model.enumeration;

import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.result.ResultCode;

public enum AccountStatus {
    ACTIVE,
    DISABLED;

    public static AccountStatus fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        try {
            return AccountStatus.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
    }
}
