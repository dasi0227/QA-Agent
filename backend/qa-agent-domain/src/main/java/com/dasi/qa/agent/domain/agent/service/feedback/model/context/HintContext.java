package com.dasi.qa.agent.domain.agent.service.feedback.model.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HintAgent 输入上下文，面向用户不会作答的提示分支。
 */
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
