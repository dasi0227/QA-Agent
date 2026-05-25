package com.dasi.qa.agent.domain.agent.service.memory.model.enumeration;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.util.StringUtils;

@Getter
@AllArgsConstructor
public enum BehaviorKey {
    MISSING_TRADEOFF("缺少取舍边界"),
    DEFINITION_ONLY("只背定义"),
    UNSTRUCTURED_ANSWER("回答结构松散"),
    SCENARIO_WEAK("场景迁移弱"),
    CAUSE_ANALYSIS_WEAK("原因分析不足"),
    TERMINOLOGY_INACCURATE("术语不准确");

    private final String label;

    public static BehaviorKey fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return BehaviorKey.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

}
