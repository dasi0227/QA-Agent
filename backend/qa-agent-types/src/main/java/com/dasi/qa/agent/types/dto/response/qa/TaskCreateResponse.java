package com.dasi.qa.agent.types.dto.response.qa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskCreateResponse {

    private String taskId;
}
