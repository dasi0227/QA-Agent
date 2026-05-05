package com.dasi.qa.agent.types.model.request.document;

import com.dasi.qa.agent.types.enums.AgentType;
import com.dasi.qa.agent.types.enums.SearchStrategy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchRequest {

    private String queryText;

    private SearchStrategy strategy;

    private String userId;

    private List<String> filterDocumentIds;

    private List<String> filterModuleTags;

    private String filterTitlePathPrefix;

    @Builder.Default
    private int topK = 10;

    private AgentType agentType;
}
