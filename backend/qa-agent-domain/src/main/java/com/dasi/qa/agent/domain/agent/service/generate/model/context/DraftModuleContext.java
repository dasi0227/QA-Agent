package com.dasi.qa.agent.domain.agent.service.generate.model.context;

import com.dasi.qa.agent.domain.agent.shared.DraftItem;
import com.dasi.qa.agent.domain.agent.shared.PlanItem;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
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
public class DraftModuleContext {

    private String taskId;
    private CreateQaSetRequest request;
    private PlanItem planItem;
    private List<SearchResult> evidence;
    private List<DraftItem> previousDrafts;
}
