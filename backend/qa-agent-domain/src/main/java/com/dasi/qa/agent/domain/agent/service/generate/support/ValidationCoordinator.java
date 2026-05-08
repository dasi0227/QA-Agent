package com.dasi.qa.agent.domain.agent.service.generate.support;

import com.alibaba.fastjson2.JSON;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.DraftItem;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.RevisionItem;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.ValidationResult;
import com.dasi.qa.agent.domain.agent.shared.enumeration.VerdictType;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.AmendAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.EvaluateAgent;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
import com.dasi.qa.agent.types.dto.response.document.SearchResult;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class ValidationCoordinator {

    private final int batchSize;

    public ValidationCoordinator(int batchSize) {
        this.batchSize = batchSize;
    }

    public ValidationOutcome run(String taskId, CreateQaSetRequest request, EvaluateAgent evaluateAgent,
                                 AmendAgent amendAgent, List<DraftItem> drafts, List<SearchResult> evidence) {
        List<DraftItem> passedDrafts = new ArrayList<>();
        int rejectedCount = 0;

        for (List<DraftItem> batch : batches(drafts, batchSize)) {
            List<ValidationResult> initialResults = evaluateOnce(taskId, evaluateAgent, batch, evidence);
            passedDrafts.addAll(itemsByVerdict(batch, initialResults, VerdictType.PASS));
            rejectedCount += countByVerdict(initialResults, VerdictType.REJECT);
            List<RevisionItem> revisionItems = revisionItems(batch, initialResults);
            if (!revisionItems.isEmpty()) {
                ValidationOutcome outcome = runValidationLoop(taskId, request, evaluateAgent, amendAgent,
                        revisionItems, evidence);
                passedDrafts.addAll(outcome.passedDrafts());
                rejectedCount += outcome.rejectedCount();
            }
        }

        return new ValidationOutcome(passedDrafts, rejectedCount);
    }

    private ValidationOutcome runValidationLoop(String taskId, CreateQaSetRequest request, EvaluateAgent evaluateAgent,
                                                AmendAgent amendAgent, List<RevisionItem> revisionItems,
                                                List<SearchResult> evidence) {
        AtomicReference<List<DraftItem>> currentItems = new AtomicReference<>(
                revisionItems.stream().map(RevisionItem::getDraftItem).toList());
        AtomicReference<List<ValidationResult>> currentResults = new AtomicReference<>(List.of());
        AtomicReference<Boolean> amendmentFailed = new AtomicReference<>(false);

        UntypedAgent validationLoop = AgenticServices.loopBuilder()
                .name("VALIDATION_LOOP")
                .description("按 evaluate 结果决定是否执行 amend")
                .maxIterations(2)
                .exitCondition((loopScope, iteration) -> iteration >= 2 || noRevise(currentResults.get()))
                .subAgents(
                        AgenticServices.agentAction(loopScope -> currentResults.set(evaluateOnce(
                                taskId, evaluateAgent, currentItems.get(), evidence))),
                        AgenticServices.agentAction(loopScope -> {
                            if (noRevise(currentResults.get())) {
                                return;
                            }
                            List<RevisionItem> currentRevisionItems = revisionItems(currentItems.get(), currentResults.get());
                            List<DraftItem> amended = amendRevisions(taskId, amendAgent, request,
                                    currentRevisionItems, evidence);
                            if (amended.size() != currentRevisionItems.size()) {
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
            log.warn("Validation loop failed, revised items rejected: count={}, message={}",
                    revisionItems.size(), exception.getMessage());
        }

        if (Boolean.TRUE.equals(amendmentFailed.get())) {
            return new ValidationOutcome(List.of(), revisionItems.size());
        }
        List<DraftItem> passed = itemsByVerdict(currentItems.get(), currentResults.get(), VerdictType.PASS);
        int rejected = Math.max(0, currentItems.get().size() - passed.size());
        return new ValidationOutcome(passed, rejected);
    }

    private List<DraftItem> amendRevisions(String taskId, AmendAgent amendAgent, CreateQaSetRequest request,
                                           List<RevisionItem> revisionItems, List<SearchResult> evidence) {
        if (revisionItems.isEmpty()) {
            return List.of();
        }
        String response = amendAgent.amend(
                taskId,
                JSON.toJSONString(revisionItems),
                JSON.toJSONString(evidence),
                previousQuestions(revisionItems.stream().map(RevisionItem::getDraftItem).toList()),
                generationNote(request)
        );
        List<DraftItem> parsed = JSON.parseArray(extractJsonArray(response), DraftItem.class);
        return parsed == null ? List.of() : parsed;
    }

    private List<ValidationResult> evaluateOnce(String taskId, EvaluateAgent evaluateAgent,
                                                List<DraftItem> drafts, List<SearchResult> evidence) {
        try {
            String response = evaluateAgent.evaluate(taskId,
                    JSON.toJSONString(drafts), JSON.toJSONString(evidence));
            List<ValidationResult> parsed = JSON.parseArray(extractJsonArray(response), ValidationResult.class);
            return parsed == null || parsed.isEmpty() ? passAll(drafts) : parsed;
        } catch (Exception exception) {
            log.warn("EvaluateAgent failed, fallback pass used: message={}", exception.getMessage());
            return passAll(drafts);
        }
    }

    private List<RevisionItem> revisionItems(List<DraftItem> drafts, List<ValidationResult> results) {
        List<RevisionItem> items = new ArrayList<>();
        if (results == null) {
            return items;
        }
        for (ValidationResult result : results) {
            if (result.getItemIndex() >= 0 && result.getItemIndex() < drafts.size()
                    && result.getVerdictType() == VerdictType.REVISE) {
                items.add(new RevisionItem(result.getItemIndex(), drafts.get(result.getItemIndex()),
                        result.getReason(), result.getRevisionSuggestion()));
            }
        }
        return items;
    }

    private List<ValidationResult> passAll(List<DraftItem> drafts) {
        List<ValidationResult> results = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            results.add(new ValidationResult(i, VerdictType.PASS, "fallback pass", ""));
        }
        return results;
    }

    private List<DraftItem> itemsByVerdict(List<DraftItem> drafts, List<ValidationResult> results, VerdictType verdictType) {
        List<DraftItem> items = new ArrayList<>();
        if (results == null) {
            return items;
        }
        for (ValidationResult result : results) {
            if (result.getItemIndex() >= 0 && result.getItemIndex() < drafts.size() && result.getVerdictType() == verdictType) {
                items.add(drafts.get(result.getItemIndex()));
            }
        }
        return items;
    }

    private boolean noRevise(List<ValidationResult> results) {
        return results == null || results.stream().noneMatch(result -> result.getVerdictType() == VerdictType.REVISE);
    }

    private int countByVerdict(List<ValidationResult> results, VerdictType verdictType) {
        if (results == null) {
            return 0;
        }
        return (int) results.stream()
                .filter(result -> result.getVerdictType() == verdictType)
                .count();
    }

    private List<List<DraftItem>> batches(List<DraftItem> drafts, int batchSize) {
        List<List<DraftItem>> batches = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i += batchSize) {
            batches.add(drafts.subList(i, Math.min(i + batchSize, drafts.size())));
        }
        return batches;
    }

    private String previousQuestions(List<DraftItem> previous) {
        return JSON.toJSONString(previous.stream()
                .filter(item -> item != null && item.getQuestion() != null)
                .map(DraftItem::getQuestion)
                .toList());
    }

    private String generationNote(CreateQaSetRequest request) {
        String note = request.getUserPrompt() == null ? "" : request.getUserPrompt();
        if (!Boolean.TRUE.equals(request.getAllowGeneralKnowledge())) {
            note += "\n禁止使用资料外事实；证据不足时写 conflictTip。";
        }
        return note;
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
