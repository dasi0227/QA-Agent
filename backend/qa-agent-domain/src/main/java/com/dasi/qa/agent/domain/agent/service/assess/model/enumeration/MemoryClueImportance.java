package com.dasi.qa.agent.domain.agent.service.assess.model.enumeration;

public enum MemoryClueImportance {
    HIGH,
    MEDIUM,
    LOW;

    public static MemoryClueImportance normalize(String value) {
        if (value == null || value.isBlank()) {
            return MEDIUM;
        }
        try {
            return MemoryClueImportance.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return MEDIUM;
        }
    }
}
