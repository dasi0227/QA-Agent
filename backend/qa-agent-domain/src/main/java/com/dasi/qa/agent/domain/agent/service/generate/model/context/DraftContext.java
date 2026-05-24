package com.dasi.qa.agent.domain.agent.service.generate.model.context;

import com.dasi.qa.agent.domain.agent.service.generate.model.result.PlanResult.PlanItem;
import com.dasi.qa.agent.domain.agent.service.generate.support.GenerateSupervisor;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

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
    private String answerStyle;
    private GenerateSupervisor supervisor;
    private List<String> allowedSourceChunkIds;
    private List<String> fallbackSourceChunkIds;
}
