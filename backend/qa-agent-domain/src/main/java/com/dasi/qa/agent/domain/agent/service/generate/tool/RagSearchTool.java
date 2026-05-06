package com.dasi.qa.agent.domain.agent.service.generate.tool;

import com.dasi.qa.agent.domain.document.service.rag.search.ISearchService;
import com.dasi.qa.agent.types.dto.request.document.SearchRequest;
import com.dasi.qa.agent.types.dto.response.document.SearchResult;
import com.dasi.qa.agent.types.enumeration.AgentType;
import com.dasi.qa.agent.types.enumeration.SearchStrategy;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;

import java.util.List;

public class RagSearchTool {

    private final ISearchService searchService;
    private final String userId;
    private final List<String> documentIds;

    public RagSearchTool(ISearchService searchService, String userId, List<String> documentIds) {
        this.searchService = searchService;
        this.userId = userId;
        this.documentIds = documentIds;
    }

    @Tool("搜索用户上传资料中的相关知识片段")
    public List<SearchResult> search(@P("查询关键词") String queryText,
                                     @P("限定模块标签") List<String> filterModuleTags) {
        SearchRequest request = SearchRequest.builder()
                .queryText(queryText)
                .strategy(SearchStrategy.HYBRID)
                .userId(userId)
                .filterDocumentIds(documentIds)
                .filterModuleTags(filterModuleTags)
                .topK(5)
                .agentType(AgentType.GENERATION)
                .build();
        return searchService.execute(request);
    }
}
