package com.dasi.qa.agent.domain.agent.service.generate.model.context;

import com.dasi.qa.agent.types.dto.request.qa.CreateTaskRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlannerStageContext {

    private String taskId;
    private CreateTaskRequest request;
    private String documentsSummary;
}
