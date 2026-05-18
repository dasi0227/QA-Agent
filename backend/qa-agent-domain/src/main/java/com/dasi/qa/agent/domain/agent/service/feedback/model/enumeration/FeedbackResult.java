package com.dasi.qa.agent.domain.agent.service.feedback.model.enumeration;

import org.springframework.util.StringUtils;

public enum FeedbackResult {
    CORRECT,
    DEFICIENT,
    WRONG,
    UNKNOWN,
    PERFECT;

    public static FeedbackResult fromValue(String value) {
        if (!StringUtils.hasText(value)) return DEFICIENT;
        try {
            FeedbackResult result = valueOf(value.trim().toUpperCase());
            return result == UNKNOWN ? DEFICIENT : result;
        } catch (IllegalArgumentException e) {
            return DEFICIENT;
        }
    }
}

