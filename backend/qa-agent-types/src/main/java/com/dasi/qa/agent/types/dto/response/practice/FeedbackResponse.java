package com.dasi.qa.agent.types.dto.response.practice;

import com.dasi.qa.agent.types.enumeration.FeedbackResultType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FeedbackResponse {

    private String sessionItemId;
    private String qaItemId;
    private FeedbackResultType result;
    private Integer score;
    private String feedbackSummary;
    private JudgeFeedbackDetail judgeDetail;
    private HintFeedbackDetail hintDetail;
    private List<FeedbackSourceChunk> sourceChunks;
    private LocalDateTime answeredAt;
}
