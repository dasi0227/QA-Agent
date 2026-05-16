package com.dasi.qa.agent.types.dto.response.practice;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessResponse {

    private String sessionId;
    private String qaSetId;
    private Integer score;
    private BigDecimal accuracy;
    private Integer correctCount;
    private Integer deficientCount;
    private Integer wrongCount;
    private Integer unknownCount;
    private String summary;
    private AssessmentDetail assessmentDetail;
    private LocalDateTime finishedAt;
}
