package com.dasi.qa.agent.domain.agent.service.generate.model.context;

import com.dasi.qa.agent.types.dto.request.qa.CreateTaskRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.concurrent.Executor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreatorStageContext {

    private String taskId;
    private String userId;
    private CreateTaskRequest request;
    private Executor executor;
}
