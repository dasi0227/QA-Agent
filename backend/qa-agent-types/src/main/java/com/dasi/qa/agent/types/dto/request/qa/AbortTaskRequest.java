package com.dasi.qa.agent.types.dto.request.qa;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AbortTaskRequest {

    @NotBlank(message = "任务 ID 不能为空")
    private String taskId;
}
