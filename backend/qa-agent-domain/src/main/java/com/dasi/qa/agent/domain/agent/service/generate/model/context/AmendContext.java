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
public class AmendContext {

    private List<AmendItem> items;
    private String userPrompt;
    private String jobDescription;
    private String answerStyle;
    private GenerateSupervisor supervisor;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AmendItem {
        private DraftResult draftResult;
        private String reason;
        private String suggestion;
    }
}
