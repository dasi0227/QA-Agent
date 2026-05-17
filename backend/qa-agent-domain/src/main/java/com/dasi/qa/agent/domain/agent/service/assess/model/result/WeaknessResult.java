package com.dasi.qa.agent.domain.agent.service.assess.model.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DiagnosisAgent 输出的单条薄弱点分析。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WeaknessResult {

    private String title;
    private String analysis;
}
