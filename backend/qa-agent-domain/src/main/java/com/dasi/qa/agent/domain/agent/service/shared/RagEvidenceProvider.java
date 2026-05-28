package com.dasi.qa.agent.domain.agent.service.shared;

import com.dasi.qa.agent.domain.agent.service.generate.model.result.PlanResult.PlanItem;
import com.dasi.qa.agent.domain.document.service.rag.search.IRagSearchService;
import com.dasi.qa.agent.types.dto.request.document.RagSearchRequest;
import com.dasi.qa.agent.types.dto.response.document.SearchResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class RagEvidenceProvider {

    private final IRagSearchService searchService;

    public RagEvidenceProvider(IRagSearchService searchService) {
        this.searchService = searchService;
    }

    public List<RagEvidenceItem> searchByPlanItem(String userId, List<String> documentIds, PlanItem planItem) {
        List<String> queires = planItem.getRetrievalQueries() == null ? List.of() : planItem.getRetrievalQueries().stream()
                .filter(StringUtils::hasText)
                .map(topic -> planItem.getModule() + " " + topic.trim())
                .toList();
        if (queires.isEmpty()) {
            queires = List.of(planItem.getModule());
        }
        return search(userId, documentIds, queires);
    }

    public List<RagEvidenceItem> searchByQuestion(String userId, List<String> documentIds, String question) {
        if (!StringUtils.hasText(question)) {
            return List.of();
        }
        return search(userId, documentIds, List.of(question));
    }

    private List<RagEvidenceItem> search(String userId, List<String> documentIds, List<String> queries) {
        List<SearchResult> results = new ArrayList<>();
        for (String query : queries) {
            String queryText = query == null ? "" : query.trim();
            if (!StringUtils.hasText(queryText)) {
                continue;
            }
            RagSearchRequest request = RagSearchRequest.builder()
                    .queryText(queryText)
                    .userId(userId)
                    .filterDocumentIds(documentIds)
                    .build();
            results.addAll(searchService.execute(request));
        }
        return results.stream()
                .filter(result -> result.getChunkId() != null)
                .collect(Collectors.toMap(
                        SearchResult::getChunkId,
                        result -> result,
                        (left, right) -> left,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .map(RagEvidenceItem::from)
                .toList();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RagEvidenceItem {
        private String chunkId;
        private String content;
        private String summary;
        private String headingPath;

        static RagEvidenceItem from(SearchResult result) {
            return RagEvidenceItem.builder()
                    .chunkId(result.getChunkId())
                    .content(result.getContent())
                    .summary(result.getSummary())
                    .headingPath(result.getHeadingPath())
                    .build();
        }
    }
}
