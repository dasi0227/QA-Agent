package com.dasi.qa.agent.domain.agent.service.assess.model.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessContext {

    private String sessionId;
    private String qaSetId;
    private String qaSetTitle;
    private Integer totalQuestions;
    private List<AssessItem> items;
    private AssessMetrics metrics;
}
