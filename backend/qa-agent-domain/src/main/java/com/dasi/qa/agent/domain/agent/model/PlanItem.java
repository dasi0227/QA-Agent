package com.dasi.qa.agent.domain.agent.model;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

public record PlanItem(
        @Description("模块标签，如 Redis、JVM") String moduleTag,
        @Description("该模块题目数") int questionCount,
        @Description("难度分布") DifficultyDistribution difficultyDistribution,
        @Description("重点考察话题") List<String> focusTopics,
        @Description("建议题目类型") List<String> suggestedQuestionTypes
) {
}
