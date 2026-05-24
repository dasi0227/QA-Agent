package com.dasi.qa.agent.domain.memory.model.enumeration;

import org.springframework.util.StringUtils;

public enum MemoryType {
    EXPRESSION,
    AWFUL,
    UNCLEAR,
    MASTER;

    public static MemoryType fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return MemoryType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
