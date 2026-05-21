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
public class PracticeStateResponse {

    private String id;
    private String qaSetId;
    private String qaSetTitle;
    private String mode;
    private String feedbackMode;
    private String status;
    private String selectedModuleTag;
    private Integer currentIndex;
    private Integer totalQuestions;
    private Integer answeredCount;
    private Integer score;
    private BigDecimal accuracy;
    private Integer correctCount;
    private Integer deficientCount;
    private Integer wrongCount;
    private Integer unknownCount;
    private String summary;
    private AssessDetail assessDetail;
    private LocalDateTime startedAt;
    private LocalDateTime lastActiveAt;
    private LocalDateTime finishedAt;
}
