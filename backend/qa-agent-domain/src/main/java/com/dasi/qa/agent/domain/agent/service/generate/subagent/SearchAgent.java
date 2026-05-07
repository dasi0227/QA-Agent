package com.dasi.qa.agent.domain.agent.service.generate.subagent;

import com.dasi.qa.agent.domain.agent.shared.PlanItem;
import com.dasi.qa.agent.domain.agent.shared.enumeration.AgentType;
import com.dasi.qa.agent.domain.document.model.enumeration.SearchStrategy;
import com.dasi.qa.agent.domain.document.service.rag.search.ISearchService;
import com.dasi.qa.agent.types.dto.request.document.SearchRequest;
import com.dasi.qa.agent.types.dto.response.document.SearchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SearchAgent {

    private final ISearchService searchService;

    public SearchAgent(ISearchService searchService) {
        this.searchService = searchService;
    }

    public List<SearchResult> search(String userId, List<String> documentIds, PlanItem planItem) {
        List<SearchResult> results = new ArrayList<>();
        List<String> topics = planItem.focusTopics() == null || planItem.focusTopics().isEmpty()
                ? List.of(planItem.moduleTag()) : planItem.focusTopics();
        for (String topic : topics) {
            SearchRequest request = SearchRequest.builder()
                    .queryText(topic)
                    .strategy(SearchStrategy.HYBRID.name())
                    .userId(userId)
                    .filterDocumentIds(documentIds)
                    .filterModuleTags(List.of(planItem.moduleTag()))
                    .topK(8)
                    .agentType(AgentType.GENERATION.name())
                    .build();
            results.addAll(searchService.execute(request));
        }
        return results.stream()
                .filter(result -> result.getChunkId() != null)
                .collect(java.util.stream.Collectors.toMap(
                        SearchResult::getChunkId,
                        result -> result,
                        (left, right) -> left,
                        java.util.LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();
    }
}
