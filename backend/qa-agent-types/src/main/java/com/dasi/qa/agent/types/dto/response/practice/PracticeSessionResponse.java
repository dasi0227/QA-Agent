package com.dasi.qa.agent.types.dto.response.practice;

import com.dasi.qa.agent.types.dto.response.BaseResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PracticeSessionResponse extends BaseResponse {

    private String qaSetId;
    private String mode;
    private String feedbackMode;
    private String status;
    private String selectedModule;
    private Integer totalQuestions;
    private Integer answeredCount;
    private Integer score;
    private BigDecimal accuracy;
    private String summary;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
}
