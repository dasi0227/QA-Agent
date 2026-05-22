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

    public List<EvidenceItem> searchByPlanItem(String userId, List<String> documentIds, PlanItem planItem) {
        String focusTopics = planItem.getFocusTopics();
        List<String> topics = !StringUtils.hasText(focusTopics)
                ? List.of(planItem.getModule())
                : List.of(focusTopics.split(","));
        return search(userId, documentIds, topics);
    }

    public List<EvidenceItem> searchByQuestion(String userId, List<String> documentIds, String question) {
        if (!StringUtils.hasText(question)) {
            return List.of();
        }
        return search(userId, documentIds, List.of(question));
    }

    private List<EvidenceItem> search(String userId, List<String> documentIds, List<String> topics) {
        List<SearchResult> results = new ArrayList<>();
        for (String topic : topics) {
            String queryText = topic == null ? "" : topic.trim();
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
                .map(EvidenceItem::from)
                .toList();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EvidenceItem {
        private String chunkId;
        private String content;
        private String summary;
        private List<String> moduleTags;

        static EvidenceItem from(SearchResult result) {
            return EvidenceItem.builder()
                    .chunkId(result.getChunkId())
                    .content(result.getContent())
                    .summary(result.getSummary())
                    .moduleTags(result.getModuleTags())
                    .build();
        }
    }
}
