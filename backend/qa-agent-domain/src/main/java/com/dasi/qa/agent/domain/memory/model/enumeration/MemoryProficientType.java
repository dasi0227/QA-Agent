package com.dasi.qa.agent.domain.memory.model.enumeration;

import org.springframework.util.StringUtils;

public enum MemoryProficientType {
    AWFUL,
    UNCLEAR,
    MASTER;

    public static MemoryProficientType fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return MemoryProficientType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
