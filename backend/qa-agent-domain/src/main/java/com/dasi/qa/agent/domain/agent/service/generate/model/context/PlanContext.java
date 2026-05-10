package com.dasi.qa.agent.domain.agent.service.generate.model.context;

import com.dasi.qa.agent.domain.agent.shared.vo.UserProfileAllowVO;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanContext {

    private String taskId;
    private String userId;
    private CreateQaSetRequest request;
    private String userProfileJson;
    private UserProfileAllowVO allow;
}
