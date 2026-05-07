package com.dasi.qa.agent.domain.agent.shared;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

public record InterviewSearchResult(
        @Description("按模块分组的搜索结果") List<InterviewInsights> insights
) {
}
