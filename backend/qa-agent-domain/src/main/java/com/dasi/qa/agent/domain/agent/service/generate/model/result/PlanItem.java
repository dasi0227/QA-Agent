package com.dasi.qa.agent.domain.agent.service.generate.model.result;

import dev.langchain4j.model.output.structured.Description;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanItem {

    @Description("模块标签，如 Redis、JVM")
    private String moduleTag;

    @Description("该模块题目数")
    private int questionCount;

    @Description("难度分布")
    private DifficultyDistribution difficultyDistribution;

    @Description("重点考察话题")
    private List<String> focusTopics;

    @Description("建议题目类型")
    private List<String> suggestedQuestionTypes;
}
