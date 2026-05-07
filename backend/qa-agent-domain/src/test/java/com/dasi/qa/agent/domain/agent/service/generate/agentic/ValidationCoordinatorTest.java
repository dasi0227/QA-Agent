package com.dasi.qa.agent.domain.agent.service.generate.agentic;

import com.dasi.qa.agent.domain.agent.model.Difficulty;
import com.dasi.qa.agent.domain.agent.model.DraftItem;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.AmenderAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.EvaluatorAgent;
import com.dasi.qa.agent.types.dto.request.qa.CreateTaskRequest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ValidationCoordinatorTest {

    @Test
    void shouldSkipAmenderWhenEvaluatorPassesAll() {
        AtomicInteger amendCalls = new AtomicInteger(0);
        EvaluatorAgent evaluatorAgent = (taskId, draftItemsJson, evidenceChunks) -> """
                [{"itemIndex":0,"verdict":"PASS","reason":"ok","revisionSuggestion":""}]
                """;
        AmenderAgent amenderAgent = (taskId, revisionItemsJson, evidenceChunks, previousQuestions, note) -> {
            amendCalls.incrementAndGet();
            return "[]";
        };

        ValidationCoordinator.ValidationOutcome outcome = new ValidationCoordinator(10)
                .run("task-1", request(), evaluatorAgent, amenderAgent, List.of(draft("Q1")), List.of());

        assertEquals(1, outcome.passedDrafts().size());
        assertEquals(0, outcome.rejectedCount());
        assertEquals(0, amendCalls.get());
    }

    @Test
    void shouldAmendRevisedItemAndAcceptSecondEvaluation() {
        AtomicInteger evaluateCalls = new AtomicInteger(0);
        AtomicInteger amendCalls = new AtomicInteger(0);
        EvaluatorAgent evaluatorAgent = (taskId, draftItemsJson, evidenceChunks) -> {
            if (evaluateCalls.incrementAndGet() == 1) {
                return """
                        [{"itemIndex":0,"verdict":"REVISE","reason":"too broad","revisionSuggestion":"收窄问题"}]
                        """;
            }
            return """
                    [{"itemIndex":0,"verdict":"PASS","reason":"ok","revisionSuggestion":""}]
                    """;
        };
        AmenderAgent amenderAgent = (taskId, revisionItemsJson, evidenceChunks, previousQuestions, note) -> {
            amendCalls.incrementAndGet();
            return """
                    [{
                      "question":"Q1 fixed",
                      "knowledgeNote":"note",
                      "answer":"answer",
                      "moduleTag":"Redis",
                      "difficulty":"MEDIUM",
                      "conflictTip":"",
                      "sourceChunkIds":[]
                    }]
                    """;
        };

        ValidationCoordinator.ValidationOutcome outcome = new ValidationCoordinator(10)
                .run("task-1", request(), evaluatorAgent, amenderAgent, List.of(draft("Q1")), List.of());

        assertEquals(1, outcome.passedDrafts().size());
        assertEquals("Q1 fixed", outcome.passedDrafts().get(0).question());
        assertEquals(0, outcome.rejectedCount());
        assertEquals(2, evaluateCalls.get());
        assertEquals(1, amendCalls.get());
    }

    @Test
    void shouldRejectRevisionWhenAmenderOutputSizeMismatch() {
        EvaluatorAgent evaluatorAgent = (taskId, draftItemsJson, evidenceChunks) -> """
                [{"itemIndex":0,"verdict":"REVISE","reason":"too broad","revisionSuggestion":"收窄问题"}]
                """;
        AmenderAgent amenderAgent = (taskId, revisionItemsJson, evidenceChunks, previousQuestions, note) -> "[]";

        ValidationCoordinator.ValidationOutcome outcome = new ValidationCoordinator(10)
                .run("task-1", request(), evaluatorAgent, amenderAgent, List.of(draft("Q1")), List.of());

        assertEquals(0, outcome.passedDrafts().size());
        assertEquals(1, outcome.rejectedCount());
    }

    @Test
    void shouldFallbackPassWhenEvaluatorFails() {
        EvaluatorAgent evaluatorAgent = (taskId, draftItemsJson, evidenceChunks) -> {
            throw new IllegalStateException("model error");
        };
        AmenderAgent amenderAgent = (taskId, revisionItemsJson, evidenceChunks, previousQuestions, note) -> "[]";

        ValidationCoordinator.ValidationOutcome outcome = new ValidationCoordinator(10)
                .run("task-1", request(), evaluatorAgent, amenderAgent, List.of(draft("Q1")), List.of());

        assertEquals(1, outcome.passedDrafts().size());
        assertEquals(0, outcome.rejectedCount());
    }

    private CreateTaskRequest request() {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setAllowGeneralKnowledge(false);
        request.setNote("只基于资料");
        return request;
    }

    private DraftItem draft(String question) {
        return new DraftItem(question, "note", "answer", "Redis",
                Difficulty.MEDIUM, "", List.of());
    }

}
