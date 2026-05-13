package com.dasi.qa.agent.domain.agent.service.generate.model.context;

import com.dasi.qa.agent.domain.agent.service.generate.model.result.DraftResult;
import com.dasi.qa.agent.domain.agent.service.generate.support.GenerateSupervisor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidateLoopContext {

    private List<DraftResult> batch;
    private String userPrompt;
    private String jobDescription;
    private String answerStyle;
    private GenerateSupervisor supervisor;
}
