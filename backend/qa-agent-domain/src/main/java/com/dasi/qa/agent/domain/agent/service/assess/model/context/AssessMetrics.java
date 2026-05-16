package com.dasi.qa.agent.domain.agent.service.assess.model.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessMetrics {

    private Integer totalQuestions;
    private Integer score;
    private BigDecimal accuracy;
    private Integer correctCount;
    private Integer deficientCount;
    private Integer wrongCount;
    private Integer unknownCount;
}
