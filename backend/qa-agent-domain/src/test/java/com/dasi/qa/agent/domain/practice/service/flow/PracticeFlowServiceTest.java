package com.dasi.qa.agent.domain.practice.service.flow;

import com.dasi.qa.agent.domain.agent.service.assess.IAssessAgent;
import com.dasi.qa.agent.domain.agent.service.feedback.IFeedbackAgent;
import com.dasi.qa.agent.domain.practice.model.enumeration.PracticeFeedbackMode;
import com.dasi.qa.agent.domain.practice.model.vo.PracticeStateVO;
import com.dasi.qa.agent.domain.practice.repository.IPracticeRepository;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.domain.util.IIdUtil;
import com.dasi.qa.agent.types.dto.request.practice.*;
import com.dasi.qa.agent.types.dto.response.practice.AssessResponse;
import com.dasi.qa.agent.types.dto.response.practice.FeedbackResponse;
import com.dasi.qa.agent.types.dto.response.practice.PracticeItemResponse;
import com.dasi.qa.agent.types.dto.response.practice.PracticeStateResponse;
import com.dasi.qa.agent.types.dto.response.practice.PracticeDetailResponse;
import com.dasi.qa.agent.types.dto.response.practice.PracticeSessionResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PracticeFlowServiceTest {

    @Test
    void initCreatesSessionAndItemsWithGeneratedIds() {
        FakePracticeRepository repository = new FakePracticeRepository();
        PracticeFlowService service = newService(repository);

        PracticeDetailResponse detail = service.init(PracticeInitRequest.builder()
                .qaSetId("set-1")
                .mode("SEQUENTIAL")
                .feedbackMode(PracticeFeedbackMode.ITEM_BY_ITEM.name())
                .build());

        assertEquals("id-1", detail.getSession().getId());
        assertEquals("set-1", repository.startedRequest.getQaSetId());
        assertEquals(List.of("id-2", "id-3"), repository.createdItemIds);
        assertEquals(2, detail.getItems().size());
    }

    @Test
    void saveOnlyPersistsDraftAndDoesNotCallFeedbackAgent() {
        FakePracticeRepository repository = new FakePracticeRepository();
        FakeFeedbackAgent feedbackAgent = new FakeFeedbackAgent();
        PracticeFlowService service = newService(repository, feedbackAgent, new FakeAssessAgent());

        PracticeItemResponse item = service.save(ItemSaveRequest.builder()
                .sessionId("session-1")
                .sessionItemId("item-1")
                .userAnswer("draft answer")
                .currentIndex(3)
                .build());

        assertEquals("DRAFT", item.getStatus());
        assertEquals("draft answer", repository.savedAnswer);
        assertEquals(3, repository.savedCurrentIndex);
        assertFalse(feedbackAgent.called);
    }

    @Test
    void answerSubmitted() {
        FakePracticeRepository repository = new FakePracticeRepository();
        FakeFeedbackAgent feedbackAgent = new FakeFeedbackAgent(repository);
        PracticeFlowService service = newService(repository, feedbackAgent, new FakeAssessAgent());

        PracticeItemResponse item = service.answer(ItemSubmitRequest.builder()
                .sessionId("session-1")
                .sessionItemId("item-1")
                .userAnswer("answer")
                .currentIndex(1)
                .build());

        assertTrue(feedbackAgent.called);
        assertEquals("item-1", feedbackAgent.request.getSessionItemId());
        assertEquals("answer", feedbackAgent.request.getUserAnswer());
        assertEquals("SUBMITTED", item.getStatus());
        assertEquals("CORRECT", item.getResult());
        assertTrue(repository.progressRefreshed);
    }

    @Test
    void itemByItemUnknownStillSubmitsWithUnknownResult() {
        FakePracticeRepository repository = new FakePracticeRepository();
        FakeFeedbackAgent feedbackAgent = new FakeFeedbackAgent(repository);
        PracticeFlowService service = newService(repository, feedbackAgent, new FakeAssessAgent());

        PracticeItemResponse item = service.unknown(ItemSaveRequest.builder()
                .sessionId("session-1")
                .sessionItemId("item-1")
                .currentIndex(1)
                .build());

        assertTrue(feedbackAgent.called);
        assertTrue(feedbackAgent.request.getUnknown());
        assertEquals("SUBMITTED", item.getStatus());
        assertTrue(item.getUnknown());
        assertEquals("UNKNOWN", item.getResult());
    }

    @Test
    void afterAllUnknownOnlyMarksAndDoesNotCallFeedbackAgent() {
        FakePracticeRepository repository = new FakePracticeRepository();
        repository.feedbackMode = PracticeFeedbackMode.AFTER_ALL;
        FakeFeedbackAgent feedbackAgent = new FakeFeedbackAgent(repository);
        PracticeFlowService service = newService(repository, feedbackAgent, new FakeAssessAgent());

        PracticeItemResponse item = service.unknown(ItemSaveRequest.builder()
                .sessionId("session-1")
                .sessionItemId("item-1")
                .currentIndex(1)
                .build());

        assertFalse(feedbackAgent.called);
        assertEquals("UNKNOWN", item.getStatus());
        assertTrue(item.getUnknown());
    }

    @Test
    void submitSessionCallsAssessAgentAndReturnsFinished() {
        FakePracticeRepository repository = new FakePracticeRepository();
        FakeAssessAgent assessAgent = new FakeAssessAgent(repository);
        PracticeFlowService service = newService(repository, new FakeFeedbackAgent(), assessAgent);

        PracticeDetailResponse detail = service.submit(PracticeSubmitRequest.builder()
                .sessionId("session-1")
                .build());

        assertTrue(assessAgent.called);
        assertEquals("session-1", assessAgent.request.getSessionId());
        assertEquals("FINISHED", detail.getSession().getStatus());
        assertEquals(90, detail.getSession().getScore());
    }

    private PracticeFlowService newService(FakePracticeRepository repository) {
        return newService(repository, new FakeFeedbackAgent(), new FakeAssessAgent());
    }

    private PracticeFlowService newService(
            FakePracticeRepository repository,
            FakeFeedbackAgent feedbackAgent,
            FakeAssessAgent assessAgent
    ) {
        return new PracticeFlowService(
                repository,
                feedbackAgent,
                assessAgent,
                new FakeContextUtil(),
                new FakeIdUtil()
        );
    }

    private static class FakePracticeRepository implements IPracticeRepository {

        private PracticeInitRequest startedRequest;
        private List<String> createdItemIds = new ArrayList<>();
        private String savedAnswer;
        private Integer savedCurrentIndex;
        private boolean progressRefreshed;
        private boolean assessSaved;
        private boolean feedbackUnknown;
        private String feedbackResult;
        private PracticeFeedbackMode feedbackMode = PracticeFeedbackMode.ITEM_BY_ITEM;

        @Override
        public PracticeDetailResponse initPractice(PracticeInitRequest request, String sessionId, List<String> sessionItemIds, String userId) {
            this.startedRequest = request;
            this.createdItemIds = sessionItemIds;
            return detail(sessionId, "IN_PROGRESS", null);
        }

        @Override
        public int countPracticeItems(PracticeInitRequest request, String userId) {
            return 2;
        }

        @Override
        public PracticeStateResponse existPractice(String qaSetId, String userId) {
            return null;
        }

        @Override
        public PracticeDetailResponse detailPractice(String sessionId, String userId) {
            return detail(sessionId, assessSaved ? "FINISHED" : "IN_PROGRESS", assessSaved ? 90 : null);
        }

        @Override
        public PracticeStateVO getPracticeState(String sessionId, String userId) {
            return new PracticeStateVO(sessionId, userId, "IN_PROGRESS", feedbackMode);
        }

        @Override
        public PracticeItemResponse savePracticeAnswer(ItemSaveRequest request, String userId) {
            this.savedAnswer = request.getUserAnswer();
            this.savedCurrentIndex = request.getCurrentIndex();
            return item("DRAFT", null);
        }

        @Override
        public PracticeItemResponse markUnknownOnly(ItemSaveRequest request, String userId) {
            return item("UNKNOWN", null);
        }

        @Override
        public PracticeItemResponse refreshPracticeItemProgress(String sessionId, String sessionItemId, Integer currentIndex, String userId) {
            this.progressRefreshed = true;
            this.savedCurrentIndex = currentIndex;
            return item("SUBMITTED", feedbackResult == null ? "CORRECT" : feedbackResult);
        }

        @Override
        public void abandonActivePractice(String qaSetId, String userId) {
        }

        @Override
        public PracticeDetailResponse abandonPractice(String sessionId, String userId) {
            return detail(sessionId, "ABANDONED", null);
        }

        @Override
        public boolean isPracticeSessionReadyForAssess(String sessionId, String userId) {
            return true;
        }

        @Override
        public List<PracticeSessionResponse> queryPracticeSession(PracticeQueryRequest request, String userId) {
            return List.of();
        }

        private PracticeDetailResponse detail(String sessionId, String status, Integer score) {
            return PracticeDetailResponse.builder()
                    .session(PracticeStateResponse.builder()
                            .id(sessionId)
                            .qaSetId("set-1")
                            .qaSetTitle("题集")
                            .mode("SEQUENTIAL")
                            .feedbackMode(PracticeFeedbackMode.ITEM_BY_ITEM.name())
                            .status(status)
                            .currentIndex(0)
                            .totalQuestions(2)
                            .answeredCount(0)
                            .score(score)
                            .accuracy(BigDecimal.ZERO)
                            .startedAt(LocalDateTime.now())
                            .build())
                    .items(List.of(item("UNANSWERED", null), item("UNANSWERED", null)))
                    .build();
        }

        private PracticeItemResponse item(String status, String result) {
            return PracticeItemResponse.builder()
                    .sessionItemId("item-1")
                    .qaItemId("qa-1")
                    .sortOrder(1)
                    .question("question")
                    .userAnswer(savedAnswer)
                    .status(status)
                    .unknown(feedbackUnknown || "UNKNOWN".equals(status) || "UNKNOWN".equals(result))
                    .result(result)
                    .score(result == null ? null : 90)
                    .build();
        }
    }

    private static class FakeFeedbackAgent implements IFeedbackAgent {

        private final FakePracticeRepository repository;
        private boolean called;
        private FeedbackRequest request;

        private FakeFeedbackAgent() {
            this(null);
        }

        private FakeFeedbackAgent(FakePracticeRepository repository) {
            this.repository = repository;
        }

        @Override
        public FeedbackResponse execute(FeedbackRequest request) {
            this.called = true;
            this.request = request;
            if (repository != null) {
                repository.feedbackUnknown = Boolean.TRUE.equals(request.getUnknown());
                repository.feedbackResult = repository.feedbackUnknown ? "UNKNOWN" : "CORRECT";
            }
            return FeedbackResponse.builder()
                    .sessionItemId(request.getSessionItemId())
                    .qaItemId("qa-1")
                    .result(Boolean.TRUE.equals(request.getUnknown()) ? "UNKNOWN" : "CORRECT")
                    .score(Boolean.TRUE.equals(request.getUnknown()) ? 0 : 90)
                    .feedbackSummary("good")
                    .answeredAt(LocalDateTime.now())
                    .build();
        }
    }

    private static class FakeAssessAgent implements IAssessAgent {

        private final FakePracticeRepository repository;
        private boolean called;
        private AssessRequest request;

        private FakeAssessAgent() {
            this(null);
        }

        private FakeAssessAgent(FakePracticeRepository repository) {
            this.repository = repository;
        }

        @Override
        public AssessResponse execute(AssessRequest request) {
            this.called = true;
            this.request = request;
            if (repository != null) {
                repository.assessSaved = true;
            }
            return AssessResponse.builder()
                    .sessionId(request.getSessionId())
                    .qaSetId("set-1")
                    .score(90)
                    .accuracy(BigDecimal.valueOf(90))
                    .finishedAt(LocalDateTime.now())
                    .build();
        }
    }

    private static class FakeContextUtil implements IContextUtil {

        @Override
        public void setUserId(String userId) {
        }

        @Override
        public String getUserId() {
            return "user-1";
        }

        @Override
        public void clear() {
        }
    }

    private static class FakeIdUtil implements IIdUtil {

        private final Queue<String> ids = new ArrayDeque<>(List.of("id-1", "id-2", "id-3", "id-4"));

        @Override
        public String nextId() {
            return ids.remove();
        }
    }
}
