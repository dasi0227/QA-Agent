package com.dasi.qa.agent.domain.agent.service.feedback.model;

import com.dasi.qa.agent.types.dto.response.practice.FeedbackDetailPayload;
import com.dasi.qa.agent.types.enumeration.FeedbackResultType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 单题反馈保存命令，承载 Judge 或 Hint 分支的统一落库数据。
 */
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
