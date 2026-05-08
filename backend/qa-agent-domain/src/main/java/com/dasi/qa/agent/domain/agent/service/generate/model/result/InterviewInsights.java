package com.dasi.qa.agent.domain.agent.service.generate.model.result;

import dev.langchain4j.model.output.structured.Description;

import java.util.List;

public record InterviewInsights(
        @Description("公司名") String company,
        @Description("岗位") String role,
        @Description("技术模块") String module,
        @Description("高频考点") List<String> highFrequencyTopics,
        @Description("典型面试题示例") List<String> typicalQuestions,
        @Description("面试官侧重点") String interviewerFocus,
        @Description("来源说明") String sourceHint
) {
}
