package com.dasi.qa.agent.domain.agent.service.assess.model.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DiagnosisAgent 输出结果，包含优势和薄弱点列表。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagnosisResult {

    private List<StrengthResult> strengths;
    private List<WeaknessResult> weaknesses;
}
