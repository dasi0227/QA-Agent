package com.dasi.qa.agent.domain.agent.service.assess.model.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AdviseAgent 使用的单题简要摘要，避免重复传入诊断专用字段。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessItemBrief {

    private String question;
    private String standardAnswer;
    private String userAnswer;
    private String result;
    private Integer score;
    private String feedbackSummary;
}
