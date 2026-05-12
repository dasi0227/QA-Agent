package com.dasi.qa.agent.types.dto.response.qa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskStatusResponse {

    private String taskId;

    private String userId;

    private String title;

    private String userPrompt;

    private String documentIdsJson;

    private String documentNamesJson;

    private String qaSetId;

    private String status;

    private String stage;

    private String errorCode;

    private String errorMessage;

    private Integer requestedQuestionCount;

    private String createdAt;

    private String startedAt;

    private String completedAt;
}
