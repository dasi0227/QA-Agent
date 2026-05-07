package com.dasi.qa.agent.domain.document.model.enumeration;

import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.result.ResultCode;

public enum SearchStrategy {
    SEMANTIC,
    KEYWORD,
    HYBRID;

    public static SearchStrategy fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
        try {
            return SearchStrategy.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ResultCode.INVALID_PARAM);
        }
    }
}
