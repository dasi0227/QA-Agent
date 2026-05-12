package com.dasi.qa.agent.types.dto.response.qa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskListItemResponse {

    private String taskId;

    private String title;

    private String status;

    private String stage;

    private String qaSetId;

    private String createdAt;
}
