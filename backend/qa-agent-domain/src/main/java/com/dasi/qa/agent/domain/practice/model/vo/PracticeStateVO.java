package com.dasi.qa.agent.domain.practice.model.vo;

import com.dasi.qa.agent.domain.practice.model.enumeration.PracticeFeedbackMode;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PracticeStateVO {

    private final String sessionId;
    private final String userId;
    private final String status;
    private final PracticeFeedbackMode feedbackMode;
}
