package com.dasi.qa.agent.types.dto.request.qa;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateTaskRequest {

    private String title;

    private String note;

    private List<String> documentIds;

    private Integer requestedQuestionCount;

    private Boolean allowGeneralKnowledge;

    private Boolean allowWebSearch;
}
