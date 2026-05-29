package com.dasi.qa.agent.domain.agent.service.shared;

import com.dasi.qa.agent.domain.agent.service.generate.model.result.PlanResult.PlanItem;
import com.dasi.qa.agent.domain.document.service.rag.search.IRagSearchService;
import com.dasi.qa.agent.domain.util.IModelUtil;
import com.dasi.qa.agent.domain.util.IPromptUtil;
import com.dasi.qa.agent.types.dto.request.document.RagSearchRequest;
import com.dasi.qa.agent.types.dto.response.document.SearchResult;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

@Component
@Slf4j
public class RagEvidenceProvider {

    private final IRagSearchService searchService;
    private final IModelUtil modelUtil;
    private final IPromptUtil promptUtil;

    public RagEvidenceProvider(IRagSearchService searchService,
                               IModelUtil modelUtil,
                               IPromptUtil promptUtil) {
        this.searchService = searchService;
        this.modelUtil = modelUtil;
        this.promptUtil = promptUtil;
    }

    public List<RagEvidenceItem> searchByPlanItem(String userId, List<String> documentIds,
                                                   PlanItem planItem, String userPrompt, String jobDescription) {
        List<String> queries = planItem.getRetrievalQueries() == null ? List.of() : planItem.getRetrievalQueries().stream()
                .filter(StringUtils::hasText)
                .map(topic -> planItem.getModule() + " " + topic.trim())
                .toList();
        if (queries.isEmpty()) {
            queries = List.of(planItem.getModule());
        }
        List<String> userKeywords = rewriteUserInput(userId, userPrompt, jobDescription);
        List<String> topics = new ArrayList<>(queries);
        topics.addAll(userKeywords);
        return search(userId, documentIds, topics);
    }

    private List<String> rewriteUserInput(String userId, String userPrompt, String jobDescription) {
        if (!StringUtils.hasText(userPrompt) && !StringUtils.hasText(jobDescription)) {
            return List.of();
        }
        try {
            String input = (StringUtils.hasText(userPrompt) ? userPrompt : "")
                    + (StringUtils.hasText(jobDescription) ? " " + jobDescription : "");
            ChatModel userModel = modelUtil.getChatModel(userId);
            String rewritten = userModel.chat(
                    SystemMessage.from(promptUtil.loadRewriterPrompt()),
                    UserMessage.from(input.trim())
            ).aiMessage().text().trim();
            if (!StringUtils.hasText(rewritten)) {
                return List.of();
            }
            return List.of(rewritten.split("\\s+"));
        } catch (Exception exception) {
            log.warn("【RAG证据】用户输入改写失败，仅使用 retrievalQueries", exception);
            return List.of();
        }
    }

    public List<RagEvidenceItem> searchByQuestion(String userId, List<String> documentIds, String question) {
        if (!StringUtils.hasText(question)) {
            return List.of();
        }
        return search(userId, documentIds, List.of(question));
    }

    private List<RagEvidenceItem> search(String userId, List<String> documentIds, List<String> topics) {
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
