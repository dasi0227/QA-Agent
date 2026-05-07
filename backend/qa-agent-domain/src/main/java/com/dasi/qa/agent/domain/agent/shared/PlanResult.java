package com.dasi.qa.agent.domain.agent.shared;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

public record PlanResult(
        @Description("问答集标题") String title,
        @Description("问答集概述") String description,
        @Description("模块规划列表") List<PlanItem> planItems
) {
}
