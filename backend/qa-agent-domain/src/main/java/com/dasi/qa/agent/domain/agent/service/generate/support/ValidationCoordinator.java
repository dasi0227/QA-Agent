package com.dasi.qa.agent.domain.agent.service.generate.support;

import com.alibaba.fastjson2.JSON;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.AmendItem;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.DraftItem;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.EvaluateItem;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.AmendAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.EvaluateAgent;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class ValidationCoordinator {

    private static final String V_PASS = "PASS";
    private static final String V_AMEND = "AMEND";
    private static final String V_REJECT = "REJECT";

    private final int batchSize;

    public ValidationCoordinator(int batchSize) {
        this.batchSize = batchSize;
    }

    public ValidationOutcome run(String taskId, CreateQaSetRequest request, EvaluateAgent evaluateAgent,
                                 AmendAgent amendAgent, List<DraftItem> drafts) {
        List<DraftItem> passedDrafts = new ArrayList<>();
        int rejectedCount = 0;

        for (List<DraftItem> batch : batches(drafts, batchSize)) {
            List<EvaluateItem> initialResults = evaluateOnce(taskId, evaluateAgent, batch);
            passedDrafts.addAll(itemsByVerdict(batch, initialResults, V_PASS));
            rejectedCount += countByVerdict(initialResults, V_REJECT);
            List<AmendItem> amendItems = amendItemsList(batch, initialResults);
            if (!amendItems.isEmpty()) {
                ValidationOutcome outcome = runValidationLoop(taskId, request, evaluateAgent, amendAgent,
                        amendItems);
                passedDrafts.addAll(outcome.passedDrafts());
                rejectedCount += outcome.rejectedCount();
            }
        }

        return new ValidationOutcome(passedDrafts, rejectedCount);
    }

    private ValidationOutcome runValidationLoop(String taskId, CreateQaSetRequest request, EvaluateAgent evaluateAgent,
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
            return new ValidationOutcome(List.of(), amendItems.size());
        }
        List<DraftItem> passed = itemsByVerdict(currentItems.get(), currentResults.get(), V_PASS);
        int rejected = Math.max(0, currentItems.get().size() - passed.size());
        return new ValidationOutcome(passed, rejected);
    }

    private List<DraftItem> amendRevisions(String taskId, AmendAgent amendAgent,
                                           List<AmendItem> amendItems) {
        if (amendItems.isEmpty()) {
            return List.of();
        }
        String response = amendAgent.amend(
                taskId,
                JSON.toJSONString(amendItems),
                ""
        );
        List<DraftItem> parsed = JSON.parseArray(extractJsonArray(response), DraftItem.class);
        return parsed == null ? List.of() : parsed;
    }

    private List<EvaluateItem> evaluateOnce(String taskId, EvaluateAgent evaluateAgent,
                                            List<DraftItem> drafts) {
        try {
            String response = evaluateAgent.evaluate(taskId, JSON.toJSONString(drafts));
            List<EvaluateItem> parsed = JSON.parseArray(extractJsonArray(response), EvaluateItem.class);
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

    private int countByVerdict(List<EvaluateItem> results, String verdict) {
        if (results == null) {
            return 0;
        }
        return (int) results.stream()
                .filter(result -> verdict.equals(result.getVerdict()))
                .count();
    }

    private List<List<DraftItem>> batches(List<DraftItem> drafts, int batchSize) {
        List<List<DraftItem>> batches = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i += batchSize) {
            batches.add(drafts.subList(i, Math.min(i + batchSize, drafts.size())));
        }
        return batches;
    }

    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    public record ValidationOutcome(List<DraftItem> passedDrafts, int rejectedCount) {
    }
}
