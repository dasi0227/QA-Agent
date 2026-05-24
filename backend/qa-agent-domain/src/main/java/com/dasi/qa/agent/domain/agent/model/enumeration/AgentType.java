package com.dasi.qa.agent.domain.agent.model.enumeration;

import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.enumeration.ResultCode;
import org.springframework.util.StringUtils;

public enum AgentType {
    GENERATION,
    FEEDBACK,
    SCORING;

    public static AgentType fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            throw new ApiException(ResultCode.INVALID_PARAM, "Agent 类型不能为空");
        }
        try {
            return AgentType.valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ResultCode.INVALID_PARAM, "Agent 类型不支持");
        }
    }
}
