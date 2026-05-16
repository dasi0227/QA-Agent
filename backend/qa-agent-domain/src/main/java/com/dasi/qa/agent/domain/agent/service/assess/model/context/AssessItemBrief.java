package com.dasi.qa.agent.domain.agent.service.assess.model.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

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
