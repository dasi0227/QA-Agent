package com.dasi.qa.agent.types.dto.response.practice;

import com.dasi.qa.agent.types.enumeration.FeedbackDetailType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackDetailPayload {

    private FeedbackDetailType type;
    private JudgeFeedbackDetail judgeDetail;
    private HintFeedbackDetail hintDetail;
}
