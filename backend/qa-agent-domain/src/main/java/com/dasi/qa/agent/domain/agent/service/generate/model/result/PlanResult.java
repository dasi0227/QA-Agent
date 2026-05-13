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
public class PlanResult {

    @Description("问答集标题")
    private String title;

    @Description("问答集概述")
    private String description;

    @Description("模块规划列表")
    private List<PlanItem> planItems;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanItem {

        @Description("技术模块标签")
        private String module;

        @Description("该模块题目数")
        private int questionCount;

        @Description("重点考察话题，逗号分隔，用于 RAG 检索")
        private String focusTopics;

        @Description("本模块必须覆盖的核心考点与生成边界")
        private String keyConcepts;
    }
}
