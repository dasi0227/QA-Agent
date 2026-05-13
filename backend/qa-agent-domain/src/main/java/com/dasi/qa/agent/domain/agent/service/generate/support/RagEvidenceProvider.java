package com.dasi.qa.agent.domain.agent.service.generate.support;

import com.dasi.qa.agent.domain.agent.service.generate.model.result.PlanResult.PlanItem;
import com.dasi.qa.agent.domain.document.service.rag.search.IRagSearchService;
import com.dasi.qa.agent.types.dto.request.document.RagSearchRequest;
import com.dasi.qa.agent.types.dto.response.document.SearchResult;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * RAG 证据检索器，按 PlanItem 的 focusTopics 逐主题搜索资料库，去重后返回证据列表。
 */
@Component
public class RagEvidenceProvider {

    private final IRagSearchService searchService;

    public RagEvidenceProvider(IRagSearchService searchService) {
        this.searchService = searchService;
    }

    public List<SearchResult> search(String userId, List<String> documentIds, PlanItem planItem) {
        List<SearchResult> results = new ArrayList<>();
        String focusTopics = planItem.getFocusTopics();
        List<String> topics = (focusTopics == null || focusTopics.isBlank())
                ? List.of(planItem.getModule())
                : List.of(focusTopics.split(","));
        for (String topic : topics) {
            topic = topic.trim();
            if (topic.isEmpty()) {
                continue;
            }
            RagSearchRequest request = RagSearchRequest.builder()
                    .queryText(topic)
                    .userId(userId)
                    .filterDocumentIds(documentIds)
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
