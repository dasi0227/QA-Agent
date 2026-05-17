package com.dasi.qa.agent.domain.agent.service.assess.model.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AdviceAgent 输出结果，包含整体点评和复习指导。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdviceResult {

    private String overallComment;
    private String reviewGuidance;
}
