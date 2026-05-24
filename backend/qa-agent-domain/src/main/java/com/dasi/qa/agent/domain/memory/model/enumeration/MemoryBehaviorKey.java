package com.dasi.qa.agent.domain.memory.model.enumeration;

import org.springframework.util.StringUtils;

public enum MemoryBehaviorKey {
    MISSING_TRADEOFF,
    DEFINITION_ONLY,
    UNSTRUCTURED_ANSWER,
    SCENARIO_WEAK,
    CAUSE_ANALYSIS_WEAK,
    TERMINOLOGY_INACCURATE;

    public static MemoryBehaviorKey fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return MemoryBehaviorKey.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public String label() {
        return switch (this) {
            case MISSING_TRADEOFF -> "缺少取舍边界";
            case DEFINITION_ONLY -> "只背定义";
            case UNSTRUCTURED_ANSWER -> "回答结构松散";
            case SCENARIO_WEAK -> "场景迁移弱";
            case CAUSE_ANALYSIS_WEAK -> "原因分析不足";
            case TERMINOLOGY_INACCURATE -> "术语不准确";
        };
    }
}
