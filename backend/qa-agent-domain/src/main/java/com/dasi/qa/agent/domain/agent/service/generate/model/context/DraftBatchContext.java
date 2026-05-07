package com.dasi.qa.agent.domain.agent.service.generate.model.context;

import com.dasi.qa.agent.domain.agent.model.PlanItem;
import com.dasi.qa.agent.types.dto.request.qa.CreateTaskRequest;
import com.dasi.qa.agent.types.dto.response.document.SearchResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DraftBatchContext {

    private String taskId;
    private CreateTaskRequest request;
    private PlanItem planItem;
    private List<SearchResult> evidence;
    private String previousQuestions;
    private Integer batchCount;
    private String extraNote;
}
