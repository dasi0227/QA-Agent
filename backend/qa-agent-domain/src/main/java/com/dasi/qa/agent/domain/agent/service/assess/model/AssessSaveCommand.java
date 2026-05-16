package com.dasi.qa.agent.domain.agent.service.assess.model;

import com.dasi.qa.agent.domain.agent.service.assess.model.result.RecordResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessMetrics;
import com.dasi.qa.agent.types.dto.response.practice.AssessmentDetail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessSaveCommand {

    private AssessMetrics metrics;
    private AssessmentDetail assessmentDetail;
    private RecordResult recordResult;
}
