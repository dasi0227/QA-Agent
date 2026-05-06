package com.dasi.qa.agent.application.configuration;

import com.dasi.qa.agent.domain.agent.model.Difficulty;
import com.dasi.qa.agent.domain.agent.model.DifficultyDistribution;
import com.dasi.qa.agent.domain.agent.model.DraftItem;
import com.dasi.qa.agent.domain.agent.model.PlanItem;
import com.dasi.qa.agent.domain.agent.model.PlanResult;
import com.dasi.qa.agent.domain.agent.model.UserLlmConfig;
import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.generate.agentic.UserLlmProvider;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.SummarizerAgent;
import com.dasi.qa.agent.types.dto.request.qa.CreateTaskRequest;
import com.dasi.qa.agent.types.dto.response.qa.TaskMessageResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskStatusResponse;
import com.dasi.qa.agent.types.enumeration.ErrorType;
import com.dasi.qa.agent.types.enumeration.GenerationStage;
import com.dasi.qa.agent.types.enumeration.GenerationStatus;
import com.dasi.qa.agent.types.exception.ApiException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QaGenerationSupportTest {

    @Test
    void shouldFailWhenUserLlmConfigMissing() {
        UserLlmProvider provider = new UserLlmProvider(new FakeAgentRepository(null));

        ApiException exception = assertThrows(ApiException.class, () -> provider.getConfig("user-1"));

        assertEquals(40902, exception.getCode());
    }

    @Test
    void shouldBuildDetailedSummaryMessage() {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setTitle("Redis 题集");
        request.setRequestedQuestionCount(3);
        PlanResult planResult = new PlanResult("Redis 题集", "desc", List.of(
                new PlanItem("Redis", 3, new DifficultyDistribution(1, 1, 1), List.of(), List.of())
        ));
        List<DraftItem> draftItems = List.of(
                new DraftItem("Q1", "N1", "A1", "Redis", Difficulty.EASY, "", List.of("c1")),
                new DraftItem("Q2", "N2", "A2", "Redis", Difficulty.MEDIUM, "", List.of("c2"))
        );
        SummarizerAgent summarizerAgent = new SummarizerAgent(new FakeAgentRepository(
                new UserLlmConfig("https://example.com/v1", "key", "model")));

        SummarizerAgent.SummaryResult summary = summarizerAgent.summarize("task-1", "user-1",
                request, planResult, draftItems, 1, List.of("JVM: no evidence"), 1234);

        assertEquals("qa-set-1", summary.qaSetId());
        assertTrue(summary.message().contains("计划 3 题"));
        assertTrue(summary.message().contains("未通过或丢弃 1 题"));
        assertTrue(summary.message().contains("Creator 失败模块 1 个"));
        assertTrue(summary.message().contains("1234 tokens"));
    }

    private static class FakeAgentRepository implements IAgentRepository {

        private final UserLlmConfig config;

        private FakeAgentRepository(UserLlmConfig config) {
            this.config = config;
        }

        @Override
        public void createGenerationTask(String taskId, String userId, CreateTaskRequest request) {
        }

        @Override
        public void updateTaskStage(String taskId, GenerationStatus status, GenerationStage stage) {
        }

        @Override
        public void markTaskCompleted(String taskId, String qaSetId) {
        }

        @Override
        public void markTaskFailed(String taskId, ErrorType errorType, String errorMessage) {
        }

        @Override
        public void appendTaskMessage(String taskId, GenerationStage stage, String message) {
        }

        @Override
        public TaskStatusResponse getTaskStatus(String taskId, String userId) {
            return null;
        }

        @Override
        public List<TaskMessageResponse> getTaskMessages(String taskId, String userId) {
            return List.of();
        }

        @Override
        public UserLlmConfig getUserLlmConfig(String userId) {
            return config;
        }

        @Override
        public String getDocumentsSummary(List<String> documentIds, String userId) {
            return "";
        }

        @Override
        public String saveGeneratedQaSet(String taskId, String userId, CreateTaskRequest request,
                                         PlanResult planResult, List<DraftItem> draftItems) {
            return "qa-set-1";
        }
    }
}
