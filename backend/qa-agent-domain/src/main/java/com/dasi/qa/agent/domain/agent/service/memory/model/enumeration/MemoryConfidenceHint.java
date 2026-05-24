package com.dasi.qa.agent.domain.agent.service.memory.model.enumeration;

import org.springframework.util.StringUtils;

public enum MemoryConfidenceHint {
    LOW(45),
    MEDIUM(60),
    HIGH(75);

    private final int baseConfidence;

    MemoryConfidenceHint(int baseConfidence) {
        this.baseConfidence = baseConfidence;
    }

    public int getBaseConfidence() {
        return baseConfidence;
    }

    public static MemoryConfidenceHint fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            return MEDIUM;
        }
        try {
            return MemoryConfidenceHint.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return MEDIUM;
        }
    }
}
