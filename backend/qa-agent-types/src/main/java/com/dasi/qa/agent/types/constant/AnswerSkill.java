package com.dasi.qa.agent.types.constant;

import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Set;

@NoArgsConstructor
public class AnswerSkill {

    private static final List<String> VALUES = List.of(
            "内容/结构回答的完整性",
            "原因/场景分析的逻辑性",
            "概念/术语表达的精确度"
    );

    private static final Set<String> VALUE_SET = Set.copyOf(VALUES);

    public static boolean contains(String value) {
        return value != null && !value.isBlank() && VALUE_SET.contains(value.trim());
    }

    public static List<String> values() {
        return VALUES;
    }
}
