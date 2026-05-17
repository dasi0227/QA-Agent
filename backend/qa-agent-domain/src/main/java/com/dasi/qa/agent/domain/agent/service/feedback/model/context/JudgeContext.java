package com.dasi.qa.agent.domain.agent.service.feedback.model.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JudgeAgent 输入上下文，面向有效用户回答的判定分支。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JudgeContext {

    private String question;
    private String standardAnswer;
    private String knowledgeNote;
    private String tip;
    private String userAnswer;
    private String answerStyle;
    private String feedbackStyle;
}
