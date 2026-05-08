package com.dasi.qa.agent.domain.agent.service.generate.model.context;

import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DecideContext {

    private String taskId;
    private CreateQaSetRequest request;
}
