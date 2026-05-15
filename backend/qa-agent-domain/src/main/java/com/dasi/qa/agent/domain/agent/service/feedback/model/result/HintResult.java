package com.dasi.qa.agent.domain.agent.service.feedback.model.result;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HintResult {

    private String memoryTip;
    private String encouragement;
}
