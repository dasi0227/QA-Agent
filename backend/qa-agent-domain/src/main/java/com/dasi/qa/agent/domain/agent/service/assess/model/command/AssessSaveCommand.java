package com.dasi.qa.agent.domain.agent.service.assess.model.command;

import com.dasi.qa.agent.types.dto.response.practice.AssessDetail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 整轮评估保存命令，承载可直接落库的统计指标和评估详情。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessSaveCommand {

    private Integer score;
    private BigDecimal accuracy;
    private Integer perfectCount;
    private Integer correctCount;
    private Integer deficientCount;
    private Integer wrongCount;
    private Integer unknownCount;
    private AssessDetail assessDetail;
}
