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
public class PracticeItemResponse {

    private String sessionItemId;
    private String qaItemId;
    private Integer sortOrder;
    private String question;
    private String knowledgeNote;
    private String standardAnswer;
    private String moduleTag;
    private String difficulty;
    private String keywords;
    private String hint;
    private String sourceChunkIdsJson;
    private String userAnswer;
    private String status;
    private Boolean unknown;
    private String result;
    private Integer score;
    private String feedbackSummary;
    private JudgeDetail judgeDetail;
    private HintDetail hintDetail;
    private List<SourceChunk> sourceChunks;
    private LocalDateTime answeredAt;
    private LocalDateTime submittedAt;
}
