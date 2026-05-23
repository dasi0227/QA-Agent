package com.dasi.qa.agent.domain.agent.service.assess.model.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Java 计算出的整轮稳定指标，LLM 不参与这些字段的判定。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessStats {

    private Integer totalQuestions;
    private Integer score;
    private BigDecimal accuracy;
    private Integer perfectCount;
    private Integer correctCount;
    private Integer deficientCount;
    private Integer wrongCount;
    private Integer unknownCount;
}
