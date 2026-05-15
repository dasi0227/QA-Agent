package com.dasi.qa.agent.domain.agent.service.feedback.model.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HintContext {

    private String question;
    private String standardAnswer;
    private String knowledgeNote;
    private String tip;
    private String answerStyle;
    private String feedbackStyle;
}
