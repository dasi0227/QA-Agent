package com.dasi.qa.agent.domain.agent.service.feedback.model;

import com.dasi.qa.agent.types.dto.response.practice.FeedbackDetailPayload;
import com.dasi.qa.agent.types.enumeration.FeedbackResultType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackSaveCommand {

    private String userAnswer;
    private FeedbackResultType result;
    private Integer score;
    private String feedbackSummary;
    private FeedbackDetailPayload detailPayload;
}
