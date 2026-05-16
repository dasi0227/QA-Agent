package com.dasi.qa.agent.domain.agent.service.assess.model.enumeration;

public enum MemoryClueType {
    CONCEPT_WEAKNESS,
    EXPRESSION_WEAKNESS,
    MISTAKE_PATTERN,
    UNKNOWN_PATTERN,
    STABLE_STRENGTH;

    public static MemoryClueType fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return MemoryClueType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
