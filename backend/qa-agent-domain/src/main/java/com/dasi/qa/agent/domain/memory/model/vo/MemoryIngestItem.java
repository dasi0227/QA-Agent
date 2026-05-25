package com.dasi.qa.agent.domain.memory.model.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemoryIngestItem {

    private String sessionItemId;
    private String qaItemId;
    private String question;
    private String moduleTag;
    private String difficulty;
    private String standardAnswer;
    private String userAnswer;
    private String result;
    private Integer score;
    private String feedbackSummary;
    private String missingPointsJson;
    private String wrongPointsJson;
    private String sourceChunkIdsJson;
}
