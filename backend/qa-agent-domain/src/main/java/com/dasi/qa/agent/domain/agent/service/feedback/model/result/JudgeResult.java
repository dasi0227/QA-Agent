package com.dasi.qa.agent.domain.agent.service.feedback.model.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeResult {

    private String result;
    private Integer score;
    private String feedbackSummary;
    private List<String> missingPoints;
    private List<String> wrongPoints;
    private String improvementAdvice;
    private String betterAnswer;
}
