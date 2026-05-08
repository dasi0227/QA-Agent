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
}
