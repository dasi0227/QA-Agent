package com.dasi.qa.agent.domain.agent.service.assess.model.enumeration;

import org.springframework.util.StringUtils;

/**
 * 内部记忆线索的重要程度枚举。
 */
public enum MemoryClueImportance {
    HIGH,
    MEDIUM,
    LOW;

    /**
     * 非法重要程度统一归一为 MEDIUM。
     */
    public static MemoryClueImportance normalize(String value) {
        // 空值和非法值使用中等重要程度兜底
        if (!StringUtils.hasText(value)) {
            return MEDIUM;
        }
        try {
            return MemoryClueImportance.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return MEDIUM;
        }
    }
}
