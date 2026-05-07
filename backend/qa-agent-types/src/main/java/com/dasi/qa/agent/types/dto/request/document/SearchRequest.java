package com.dasi.qa.agent.types.dto.request.document;
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

    private String strategy;

    private String userId;

    private List<String> filterDocumentIds;

    private List<String> filterModuleTags;

    private String filterTitlePathPrefix;

    @Builder.Default
    private int topK = 10;

    private String agentType;
}
