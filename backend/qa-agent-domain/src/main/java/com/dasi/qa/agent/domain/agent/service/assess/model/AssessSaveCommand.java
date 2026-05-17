package com.dasi.qa.agent.domain.agent.service.assess.model;

import com.dasi.qa.agent.domain.agent.service.assess.model.result.RecordResult;
import com.dasi.qa.agent.domain.agent.service.assess.model.context.AssessMetrics;
import com.dasi.qa.agent.types.dto.response.practice.AssessmentDetail;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 整轮评估保存命令，承载统计指标、评估详情和内部记忆线索。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssessSaveCommand {

    private AssessMetrics metrics;
    private AssessmentDetail assessmentDetail;
    private RecordResult recordResult;
}
