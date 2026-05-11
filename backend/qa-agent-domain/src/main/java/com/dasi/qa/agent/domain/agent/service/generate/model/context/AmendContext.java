package com.dasi.qa.agent.domain.agent.service.generate.model.context;

import com.dasi.qa.agent.domain.agent.service.generate.model.result.AmendItem;
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
public class AmendContext {

    private List<AmendItem> items;
    private String userPrompt;
    private String answerStyle;
    private GenerateSupervisor supervisor;
}
