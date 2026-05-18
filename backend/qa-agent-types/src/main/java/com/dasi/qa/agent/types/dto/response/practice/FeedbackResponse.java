package com.dasi.qa.agent.types.dto.response.practice;

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
    private String result;
    private Integer score;
    private String feedbackSummary;
    private JudgeDetail judgeDetail;
    private HintDetail hintDetail;
    private List<SourceChunk> sourceChunks;
    private LocalDateTime answeredAt;
}
