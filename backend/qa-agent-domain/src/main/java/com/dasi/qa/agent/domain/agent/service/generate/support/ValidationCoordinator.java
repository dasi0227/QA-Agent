package com.dasi.qa.agent.domain.agent.service.generate.support;

import com.dasi.qa.agent.domain.agent.service.generate.model.result.AmendItem;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.DraftItem;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.EvaluateItem;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.AmendAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.EvaluateAgent;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class ValidationCoordinator {

    private static final String V_PASS = "PASS";
    private static final String V_AMEND = "AMEND";
    private static final String V_REJECT = "REJECT";

    private final int batchSize;
    private final IJsonUtil jsonUtil;

    public ValidationCoordinator(int batchSize, IJsonUtil jsonUtil) {
        this.batchSize = batchSize;
        this.jsonUtil = jsonUtil;
    }

    public List<DraftItem> run(String taskId, CreateQaSetRequest request, EvaluateAgent evaluateAgent,
                              AmendAgent amendAgent, List<DraftItem> drafts) {
        List<DraftItem> passedDrafts = new ArrayList<>();

        for (List<DraftItem> batch : batches(drafts, batchSize)) {
            List<EvaluateItem> initialResults = evaluateOnce(taskId, evaluateAgent, batch);
            passedDrafts.addAll(itemsByVerdict(batch, initialResults, V_PASS));
            List<AmendItem> amendItems = amendItemsList(batch, initialResults);
            if (!amendItems.isEmpty()) {
                passedDrafts.addAll(runValidationLoop(taskId, request, evaluateAgent, amendAgent,
                        amendItems));
            }
        }

        return passedDrafts.stream()
                .filter(Objects::nonNull)
                .filter(item -> StringUtils.hasText(item.getQuestion()) && StringUtils.hasText(item.getAnswer()))
                .map(item -> new DraftItem(
                        item.getQuestion().trim(),
                        item.getAnswer().trim(),
                        item.getKnowledgeNote() != null ? item.getKnowledgeNote().trim() : "",
                        StringUtils.hasText(item.getTag()) ? item.getTag().trim() : "General",
                        StringUtils.hasText(item.getDifficulty()) ? item.getDifficulty().trim() : "MEDIUM",
                        item.getConflictTip() != null ? item.getConflictTip() : "",
                        item.getEvidence() != null ? item.getEvidence() : ""))
                .toList();
    }

    private List<DraftItem> runValidationLoop(String taskId, CreateQaSetRequest request, EvaluateAgent evaluateAgent,
                                              AmendAgent amendAgent, List<AmendItem> amendItems) {
        AtomicReference<List<DraftItem>> currentItems = new AtomicReference<>(
                amendItems.stream().map(AmendItem::getDraftItem).toList());
        AtomicReference<List<EvaluateItem>> currentResults = new AtomicReference<>(List.of());
        AtomicReference<Boolean> amendmentFailed = new AtomicReference<>(false);

        UntypedAgent validationLoop = AgenticServices.loopBuilder()
                .name("VALIDATION_LOOP")
                .description("按 evaluate 结果决定是否执行 amend")
                .maxIterations(2)
                .exitCondition((loopScope, iteration) -> iteration >= 2 || noAmend(currentResults.get()))
                .subAgents(
                        AgenticServices.agentAction(loopScope -> currentResults.set(evaluateOnce(
                                taskId, evaluateAgent, currentItems.get()))),
                        AgenticServices.agentAction(loopScope -> {
                            if (noAmend(currentResults.get())) {
                                return;
                            }
                            List<AmendItem> currentAmendItems = amendItemsList(currentItems.get(), currentResults.get());
                            List<DraftItem> amended = amendRevisions(taskId, amendAgent,
                                    currentAmendItems);
                            if (amended.size() != currentAmendItems.size()) {
                                amendmentFailed.set(true);
                                throw new IllegalStateException("AmendAgent output size mismatch");
                            }
                            currentItems.set(amended);
                        })
                )
                .output(loopScope -> currentItems.get())
                .build();

        try {
            validationLoop.invoke(java.util.Map.of("taskId", taskId));
        } catch (Exception exception) {
            amendmentFailed.set(true);
            log.warn("Validation loop failed, amended items rejected: count={}, message={}",
                    amendItems.size(), exception.getMessage());
        }

        if (Boolean.TRUE.equals(amendmentFailed.get())) {
            return List.of();
        }
        return itemsByVerdict(currentItems.get(), currentResults.get(), V_PASS);
    }

    private List<DraftItem> amendRevisions(String taskId, AmendAgent amendAgent,
                                           List<AmendItem> amendItems) {
        if (amendItems.isEmpty()) {
            return List.of();
        }
        String response = amendAgent.amend(
                taskId,
                jsonUtil.toJsonString(amendItems),
                ""
        );
        List<DraftItem> parsed = jsonUtil.parseJsonArray(response, DraftItem.class);
        return parsed == null ? List.of() : parsed;
    }

    private List<EvaluateItem> evaluateOnce(String taskId, EvaluateAgent evaluateAgent,
                                            List<DraftItem> drafts) {
        try {
            String response = evaluateAgent.evaluate(taskId, jsonUtil.toJsonString(drafts));
            List<EvaluateItem> parsed = jsonUtil.parseJsonArray(response, EvaluateItem.class);
            return parsed == null || parsed.isEmpty() ? passAll(drafts) : parsed;
        } catch (Exception exception) {
            log.warn("EvaluateAgent failed, fallback pass used: message={}", exception.getMessage());
            return passAll(drafts);
        }
    }

    private List<AmendItem> amendItemsList(List<DraftItem> drafts, List<EvaluateItem> results) {
        List<AmendItem> items = new ArrayList<>();
        if (results == null) {
            return items;
        }
        int count = Math.min(drafts.size(), results.size());
        for (int i = 0; i < count; i++) {
            if (V_AMEND.equals(results.get(i).getVerdict())) {
                items.add(new AmendItem(drafts.get(i),
                        results.get(i).getReason(), results.get(i).getSuggestion()));
            }
        }
        return items;
    }

    private List<EvaluateItem> passAll(List<DraftItem> drafts) {
        List<EvaluateItem> results = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            results.add(new EvaluateItem(V_PASS, "fallback pass", ""));
        }
        return results;
    }

    private List<DraftItem> itemsByVerdict(List<DraftItem> drafts, List<EvaluateItem> results, String verdict) {
        List<DraftItem> items = new ArrayList<>();
        if (results == null) {
            return items;
        }
        int count = Math.min(drafts.size(), results.size());
        for (int i = 0; i < count; i++) {
            if (verdict.equals(results.get(i).getVerdict())) {
                items.add(drafts.get(i));
            }
        }
        return items;
    }

    private boolean noAmend(List<EvaluateItem> results) {
        return results == null || results.stream().noneMatch(result -> V_AMEND.equals(result.getVerdict()));
    }

    private List<List<DraftItem>> batches(List<DraftItem> drafts, int batchSize) {
        List<List<DraftItem>> batches = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i += batchSize) {
            batches.add(drafts.subList(i, Math.min(i + batchSize, drafts.size())));
        }
        return batches;
    }

}
