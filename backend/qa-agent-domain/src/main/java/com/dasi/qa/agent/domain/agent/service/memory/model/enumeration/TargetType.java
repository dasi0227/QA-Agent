package com.dasi.qa.agent.domain.agent.service.memory.model.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.util.StringUtils;

@Getter
@AllArgsConstructor
public enum TargetType {
    MODULE_TAG("知识模块"),
    ANSWER_SKILL("回答能力");

    private final String label;

    public static TargetType fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return TargetType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

}
