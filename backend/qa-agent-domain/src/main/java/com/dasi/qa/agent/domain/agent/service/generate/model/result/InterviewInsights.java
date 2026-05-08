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
public class InterviewInsights {

    @Description("公司名")
    private String company;

    @Description("岗位")
    private String role;

    @Description("技术模块")
    private String module;

    @Description("高频考点")
    private List<String> highFrequencyTopics;

    @Description("典型面试题示例")
    private List<String> typicalQuestions;

    @Description("面试官侧重点")
    private String interviewerFocus;

    @Description("来源说明")
    private String sourceHint;
}
