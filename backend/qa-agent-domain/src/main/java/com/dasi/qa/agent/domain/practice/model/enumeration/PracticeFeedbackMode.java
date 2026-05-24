package com.dasi.qa.agent.domain.practice.model.enumeration;

import com.dasi.qa.agent.types.enumeration.ResultCode;
import com.dasi.qa.agent.types.exception.ApiException;
import org.springframework.util.StringUtils;

public enum PracticeFeedbackMode {
    ITEM_BY_ITEM,
    AFTER_ALL;

    public static PracticeFeedbackMode defaultMode() {
        return ITEM_BY_ITEM;
    }

    public static PracticeFeedbackMode fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            return defaultMode();
        }
        try {
            return PracticeFeedbackMode.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ApiException(ResultCode.INVALID_PARAM, "反馈模式不支持，请选择 ITEM_BY_ITEM 或 AFTER_ALL");
        }
    }

    public boolean isItemByItem() {
        return this == ITEM_BY_ITEM;
    }
}
