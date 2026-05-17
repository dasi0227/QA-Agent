package com.dasi.qa.agent.domain.agent.service.assess.model.enumeration;

/**
 * 内部记忆线索的类型枚举。
 */
public enum MemoryClueType {
    CONCEPT_WEAKNESS,
    EXPRESSION_WEAKNESS,
    MISTAKE_PATTERN,
    UNKNOWN_PATTERN,
    STABLE_STRENGTH;

    /**
     * 将字符串解析为线索类型，非法值返回 null 供上层过滤。
     */
    public static MemoryClueType fromValue(String value) {
        // 空值不参与记忆线索落库
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
