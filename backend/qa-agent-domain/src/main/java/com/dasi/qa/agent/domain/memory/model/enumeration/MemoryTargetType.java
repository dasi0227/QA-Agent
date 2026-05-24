package com.dasi.qa.agent.domain.memory.model.enumeration;

import org.springframework.util.StringUtils;

public enum MemoryTargetType {
    MODULE,
    BEHAVIOR,
    GENERAL;

    public static MemoryTargetType fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return MemoryTargetType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
