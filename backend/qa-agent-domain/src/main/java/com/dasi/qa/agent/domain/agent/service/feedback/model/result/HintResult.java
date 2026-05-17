package com.dasi.qa.agent.domain.agent.service.feedback.model.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * HintAgent 输出结果，包含记忆技巧和情绪支持。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HintResult {

    private String memoryTip;
    private String encouragement;
}
