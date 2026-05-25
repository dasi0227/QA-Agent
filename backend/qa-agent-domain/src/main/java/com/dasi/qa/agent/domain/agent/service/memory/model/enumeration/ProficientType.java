package com.dasi.qa.agent.domain.agent.service.memory.model.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.util.StringUtils;

@Getter
@AllArgsConstructor
public enum ProficientType {
    AWFUL("严重薄弱"),
    UNCLEAR("理解不稳"),
    MASTER("稳定掌握");

    private final String label;

    public static ProficientType fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return ProficientType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

}
