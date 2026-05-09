package com.dasi.qa.agent.domain.agent.service.generate.model.context;

import com.dasi.qa.agent.domain.agent.service.generate.model.result.PlanItem;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftContext {

    private String taskId;
    private CreateQaSetRequest request;
    private PlanItem planItem;
    private String evidence;
    private String userProfileJson;
    private String previousQuestions;
    private int batchCount;
}
