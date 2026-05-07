package com.dasi.qa.agent.domain.agent.service.generate.agentic;

import com.alibaba.fastjson2.JSON;
import com.dasi.qa.agent.domain.agent.model.Difficulty;
import com.dasi.qa.agent.domain.agent.model.DifficultyDistribution;
import com.dasi.qa.agent.domain.agent.model.DraftItem;
import com.dasi.qa.agent.domain.agent.model.PlanItem;
import com.dasi.qa.agent.domain.agent.model.PlanResult;
import com.dasi.qa.agent.domain.agent.model.UserLlmConfig;
import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.AmenderAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.DrafterAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.EvaluatorAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.PlannerAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.SearcherAgent;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.SummarizerAgent;
import com.dasi.qa.agent.domain.agent.service.generate.tool.InterviewExperienceSearchTool;
import com.dasi.qa.agent.domain.agent.service.generate.tool.RagSearchTool;
import com.dasi.qa.agent.domain.document.service.rag.search.ISearchService;
import com.dasi.qa.agent.types.dto.request.qa.CreateTaskRequest;
import com.dasi.qa.agent.types.dto.response.document.SearchResult;
import com.dasi.qa.agent.types.dto.sse.SseEvent;
import com.dasi.qa.agent.types.enumeration.ErrorType;
import com.dasi.qa.agent.types.enumeration.GenerationStage;
import com.dasi.qa.agent.types.enumeration.GenerationStatus;
import com.dasi.qa.agent.types.exception.ApiException;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.observability.AgentInvocationError;
import dev.langchain4j.agentic.observability.AgentListener;
import dev.langchain4j.agentic.observability.AgentResponse;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@Service
@Slf4j
public class GenerationAgent implements IGenerationAgent {

    private static final int DEFAULT_QUESTION_COUNT = 10;
    private static final int MAX_MODULE_QUESTIONS_PER_BATCH = 10;

    private final IAgentRepository agentRepository;
    private final IUserLlmProvider userLlmProvider;
    private final IQaGenerationDagFactory dagFactory;
    private final ISearchService searchService;
    private final SearcherAgent searcherAgent;
    private final SummarizerAgent summarizerAgent;
    private final ChatModel supervisorChatModel;
    private final ChatModel webSearchChatModel;
    private final ChatMemoryProvider chatMemoryProvider;
    private final ThreadPoolTaskExecutor applicationTaskExecutor;

    public GenerationAgent(IAgentRepository agentRepository,
                           IUserLlmProvider userLlmProvider,
                           IQaGenerationDagFactory dagFactory,
                           ISearchService searchService,
                           SearcherAgent searcherAgent,
                           SummarizerAgent summarizerAgent,
                           @Qualifier("supervisorChatModel") ChatModel supervisorChatModel,
                           @Qualifier("webSearchChatModel") ChatModel webSearchChatModel,
                           ChatMemoryProvider chatMemoryProvider,
                           @Qualifier("applicationTaskExecutor") ThreadPoolTaskExecutor applicationTaskExecutor) {
        this.agentRepository = agentRepository;
        this.userLlmProvider = userLlmProvider;
        this.dagFactory = dagFactory;
        this.searchService = searchService;
        this.searcherAgent = searcherAgent;
        this.summarizerAgent = summarizerAgent;
        this.supervisorChatModel = supervisorChatModel;
        this.webSearchChatModel = webSearchChatModel;
        this.chatMemoryProvider = chatMemoryProvider;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    @Override
    public void execute(String userId, CreateTaskRequest request, Consumer<SseEvent> eventSink) {
        String taskId = UUID.randomUUID().toString();
        AtomicInteger totalTokens = new AtomicInteger(0);
        TaskEventPublisher publisher = new TaskEventPublisher(taskId, eventSink, totalTokens);
        agentRepository.createGenerationTask(taskId, userId, request);
        publisher.publish(GenerationStage.PENDING, GenerationStatus.PROCESSING, "生成任务已创建", 0);

        try {
            UserLlmConfig llmConfig = userLlmProvider.getConfig(userId);
            ChatModel userModel = OpenAiChatModel.builder()
                    .baseUrl(llmConfig.baseUrl())
                    .apiKey(llmConfig.apiKey())
                    .modelName(llmConfig.modelName())
                    .timeout(Duration.ofSeconds(60))
                    .maxRetries(1)
                    .build();

            runGuardrails(request, publisher, totalTokens);
            String documentsSummary = agentRepository.getDocumentsSummary(request.getDocumentIds(), userId);
            QaGenerationAgentListener listener = new QaGenerationAgentListener(taskId, publisher, totalTokens);
            List<Object> creatorTools = creatorTools(userId, request);
            List<Object> amendmentTools = amendmentTools(userId, request);

            UntypedAgent dag = dagFactory.build(new QaGenerationDagContext(
                    taskId,
                    userModel,
                    chatMemoryProvider,
                    listener,
                    creatorTools,
                    amendmentTools,
                    applicationTaskExecutor,
                    (scope, plannerAgent) -> runPlanner(scope, plannerAgent, taskId, request, documentsSummary),
                    (scope, drafterAgent, executor) -> runCreator(scope, drafterAgent, executor, taskId, userId, request),
                    (scope, evaluatorAgent, amenderAgent) -> runValidator(scope, evaluatorAgent, amenderAgent, taskId, request),
                    scope -> runSummarizer(scope, taskId, userId, request, publisher)
            ));

            dag.invoke(Map.of("taskId", taskId));
        } catch (ApiException exception) {
            ErrorType errorType = exception.getCode() == 40902 ? ErrorType.LLM_NOT_CONFIGURED : ErrorType.UNKNOWN;
            fail(taskId, publisher, errorType, exception.getMessage());
        } catch (GenerationException exception) {
            fail(taskId, publisher, exception.errorType, exception.getMessage());
        } catch (Exception exception) {
            fail(taskId, publisher, classifyError(exception), exception.getMessage());
        }
    }

    private List<Object> creatorTools(String userId, CreateTaskRequest request) {
        List<Object> tools = new ArrayList<>();
        tools.add(new RagSearchTool(searchService, userId, request.getDocumentIds()));
        if (Boolean.TRUE.equals(request.getAllowWebSearch())) {
            tools.add(new InterviewExperienceSearchTool(webSearchChatModel));
        }
        return tools;
    }

    private List<Object> amendmentTools(String userId, CreateTaskRequest request) {
        return List.of(new RagSearchTool(searchService, userId, request.getDocumentIds()));
    }

    private void runGuardrails(CreateTaskRequest request, TaskEventPublisher publisher, AtomicInteger totalTokens) {
        String prompt = loadPrompt("prompt/supervisor-classify.txt") + "\n\n用户要求："
                + safe(request.getNote()) + "\n请返回 JSON。";
        ChatResponse response = chat(supervisorChatModel, "你是 QA_Agent 的输入安全分类器。", prompt, totalTokens);
        Map<String, Object> result = parseObject(response.aiMessage().text());
        Object valid = result.get("valid");
        if (Boolean.FALSE.equals(valid) || "false".equalsIgnoreCase(String.valueOf(valid))) {
            String reason = String.valueOf(result.getOrDefault("reason", "用户要求与生成问答集无关"));
            publisher.publish(GenerationStage.FAILED, GenerationStatus.FAILED, reason, 0);
            throw new GenerationException(ErrorType.CONTENT_FILTERED, reason);
        }
    }

    private void runPlanner(AgenticScope scope, PlannerAgent plannerAgent, String taskId,
                            CreateTaskRequest request, String documentsSummary) {
        agentRepository.updateTaskStage(taskId, GenerationStatus.PROCESSING, GenerationStage.PLANNER);
        PlanResult planResult;
        try {
            planResult = plannerAgent.plan(taskId, documentsSummary, "", "", "",
                    safe(request.getNote()), questionCount(request));
        } catch (Exception exception) {
            log.warn("PlannerAgent failed, fallback plan used: taskId={}, message={}", taskId, exception.getMessage());
            planResult = fallbackPlan(request);
        }
        scope.writeState("planResult", normalizePlan(planResult, request));
    }

    private void runCreator(AgenticScope scope, DrafterAgent drafterAgent, Executor executor,
                            String taskId, String userId, CreateTaskRequest request) {
        agentRepository.updateTaskStage(taskId, GenerationStatus.PROCESSING, GenerationStage.CREATOR);
        PlanResult planResult = readPlan(scope, request);
        List<DraftItem> allDrafts = Collections.synchronizedList(new ArrayList<>());
        List<SearchResult> allEvidence = Collections.synchronizedList(new ArrayList<>());
        List<String> failedModules = Collections.synchronizedList(new ArrayList<>());
        List<Object> moduleAgents = new ArrayList<>();

        for (PlanItem planItem : safePlanItems(planResult, request)) {
            moduleAgents.add(AgenticServices.agentAction(moduleScope -> {
                try {
                    List<SearchResult> evidence = searcherAgent.search(userId, request.getDocumentIds(), planItem);
                    allEvidence.addAll(evidence);
                    allDrafts.addAll(draftModule(taskId, drafterAgent, request, planItem, evidence, allDrafts));
                } catch (Exception exception) {
                    failedModules.add(planItem.moduleTag() + ": " + safe(exception.getMessage()));
                    log.warn("Creator module failed: taskId={}, module={}, message={}",
                            taskId, planItem.moduleTag(), exception.getMessage());
                }
            }));
        }

        UntypedAgent creator = AgenticServices.parallelBuilder()
                .name("CREATOR")
                .description("按模块并发执行 SearcherAgent 到 DrafterAgent")
                .executor(executor)
                .subAgents(moduleAgents)
                .output(moduleScope -> allDrafts)
                .build();
        creator.invoke(Map.of("taskId", taskId));

        List<DraftItem> deduplicated = deduplicate(allDrafts);
        scope.writeState("allDrafts", deduplicated);
        scope.writeState("allEvidence", deduplicateEvidence(allEvidence));
        if (!failedModules.isEmpty()) {
            scope.writeState("creatorFailedModules", List.copyOf(failedModules));
        }
    }

    private List<DraftItem> draftModule(String taskId, DrafterAgent drafterAgent, CreateTaskRequest request,
                                        PlanItem planItem, List<SearchResult> evidence, List<DraftItem> previous) {
        int remaining = Math.max(0, planItem.questionCount());
        List<DraftItem> moduleDrafts = new ArrayList<>();
        while (remaining > 0) {
            int batchCount = Math.min(MAX_MODULE_QUESTIONS_PER_BATCH, remaining);
            List<DraftItem> batch = draftBatch(taskId, drafterAgent, request, planItem, evidence,
                    previousQuestions(previous) + previousQuestions(moduleDrafts), batchCount, "");
            moduleDrafts.addAll(batch);
            remaining -= batchCount;
        }
        return moduleDrafts;
    }

    private List<DraftItem> draftBatch(String taskId, DrafterAgent drafterAgent, CreateTaskRequest request,
                                       PlanItem planItem, List<SearchResult> evidence,
                                       String previousQuestions, int batchCount, String extraNote) {
        try {
            String response = drafterAgent.draft(
                    taskId,
                    planItem.moduleTag(),
                    JSON.toJSONString(evidence),
                    "",
                    "",
                    answerStyle(request),
                    JSON.toJSONString(planItem.difficultyDistribution()),
                    batchCount,
                    previousQuestions,
                    generationNote(request, extraNote)
            );
            List<DraftItem> parsed = JSON.parseArray(extractJsonArray(response), DraftItem.class);
            return parsed == null ? List.of() : parsed;
        } catch (Exception exception) {
            log.warn("DrafterAgent failed, fallback drafts used: module={}, message={}",
                    planItem.moduleTag(), exception.getMessage());
            return fallbackDrafts(new PlanItem(planItem.moduleTag(), batchCount,
                    planItem.difficultyDistribution(), planItem.focusTopics(), planItem.suggestedQuestionTypes()), evidence);
        }
    }

    private void runValidator(AgenticScope scope, EvaluatorAgent evaluatorAgent, AmenderAgent amenderAgent,
                              String taskId, CreateTaskRequest request) {
        agentRepository.updateTaskStage(taskId, GenerationStatus.PROCESSING, GenerationStage.VALIDATOR);
        List<DraftItem> drafts = readDrafts(scope);
        List<SearchResult> evidence = readEvidence(scope);
        ValidationCoordinator.ValidationOutcome outcome = new ValidationCoordinator(MAX_MODULE_QUESTIONS_PER_BATCH)
                .run(taskId, request, evaluatorAgent, amenderAgent, drafts, evidence);

        List<DraftItem> finalDrafts = cleanDrafts(filterSourceChunkIds(deduplicate(outcome.passedDrafts()), evidence),
                request, evidence);
        if (finalDrafts.isEmpty()) {
            throw new GenerationException(ErrorType.ALL_REJECTED, "Evaluator 未通过任何题目");
        }
        scope.writeState("passedDrafts", finalDrafts);
        scope.writeState("rejectedCount", outcome.rejectedCount());
    }

    private void runSummarizer(AgenticScope scope, String taskId, String userId,
                               CreateTaskRequest request, TaskEventPublisher publisher) {
        agentRepository.updateTaskStage(taskId, GenerationStatus.PROCESSING, GenerationStage.SUMMARIZER);
        PlanResult planResult = readPlan(scope, request);
        List<DraftItem> passedDrafts = readPassedDrafts(scope);
        SummarizerAgent.SummaryResult summary = summarizerAgent.summarize(taskId, userId, request, planResult,
                passedDrafts, readInt(scope, "rejectedCount"), readStrings(scope, "creatorFailedModules"),
                publisher.totalTokens.get());
        agentRepository.markTaskCompleted(taskId, summary.qaSetId());
        scope.writeState("qaSetId", summary.qaSetId());
        publisher.publish(GenerationStage.COMPLETED, GenerationStatus.COMPLETED, summary.message(), 0);
    }

    private ChatResponse chat(ChatModel model, String systemPrompt, String userPrompt, AtomicInteger totalTokens) {
        ChatResponse response = model.chat(SystemMessage.from(systemPrompt), UserMessage.from(userPrompt));
        totalTokens.addAndGet(tokens(response));
        return response;
    }

    private int tokens(ChatResponse response) {
        if (response == null) {
            return 0;
        }
        TokenUsage usage = response.tokenUsage();
        return usage == null || usage.totalTokenCount() == null ? 0 : usage.totalTokenCount();
    }

    private void fail(String taskId, TaskEventPublisher publisher, ErrorType errorType, String message) {
        String errorMessage = message == null || message.isBlank() ? errorType.name() : message;
        log.error("QA generation task failed: taskId={}, errorType={}, message={}", taskId, errorType, errorMessage);
        agentRepository.markTaskFailed(taskId, errorType, errorMessage);
        publisher.publish(GenerationStage.FAILED, GenerationStatus.FAILED, errorMessage, 0);
    }

    private ErrorType classifyError(Exception exception) {
        String message = exception.getMessage() == null ? "" : exception.getMessage().toLowerCase();
        if (message.contains("401") || message.contains("unauthorized") || message.contains("api key")) {
            return ErrorType.AUTH_FAILURE;
        }
        if (message.contains("rate limit") || message.contains("429")) {
            return ErrorType.RATE_LIMITED;
        }
        if (message.contains("parse") || message.contains("json")) {
            return ErrorType.INVALID_RESPONSE;
        }
        if (message.contains("timeout") || message.contains("connect")) {
            return ErrorType.NETWORK_ERROR;
        }
        return ErrorType.UNKNOWN;
    }

    private PlanResult normalizePlan(PlanResult planResult, CreateTaskRequest request) {
        List<PlanItem> items = safePlanItems(planResult, request);
        int total = items.stream().mapToInt(item -> Math.max(0, item.questionCount())).sum();
        int target = questionCount(request);
        if (total != target && !items.isEmpty()) {
            PlanItem first = items.get(0);
            int fixedCount = Math.max(1, first.questionCount() + target - total);
            List<PlanItem> fixedItems = new ArrayList<>(items);
            fixedItems.set(0, new PlanItem(first.moduleTag(), fixedCount,
                    normalizeDistribution(first.difficultyDistribution(), fixedCount),
                    first.focusTopics(), first.suggestedQuestionTypes()));
            items = fixedItems;
        }
        return new PlanResult(
                planResult == null || planResult.title() == null || planResult.title().isBlank()
                        ? title(request) : planResult.title(),
                planResult == null || planResult.description() == null ? "" : planResult.description(),
                items.stream()
                        .map(item -> new PlanItem(item.moduleTag(), item.questionCount(),
                                normalizeDistribution(item.difficultyDistribution(), item.questionCount()),
                                item.focusTopics() == null ? List.of() : item.focusTopics(),
                                item.suggestedQuestionTypes() == null ? List.of() : item.suggestedQuestionTypes()))
                        .toList()
        );
    }

    private DifficultyDistribution normalizeDistribution(DifficultyDistribution distribution, int questionCount) {
        if (distribution == null) {
            return new DifficultyDistribution(0, questionCount, 0);
        }
        int total = distribution.easy() + distribution.medium() + distribution.hard();
        if (total == questionCount) {
            return distribution;
        }
        return new DifficultyDistribution(distribution.easy(), Math.max(0, questionCount - distribution.easy() - distribution.hard()), distribution.hard());
    }

    private List<DraftItem> deduplicate(List<DraftItem> items) {
        Map<String, DraftItem> map = new LinkedHashMap<>();
        for (DraftItem item : items) {
            if (item != null && item.question() != null && !item.question().isBlank()) {
                map.putIfAbsent(item.question(), item);
            }
        }
        return new ArrayList<>(map.values());
    }

    private List<SearchResult> deduplicateEvidence(List<SearchResult> evidence) {
        Map<String, SearchResult> map = new LinkedHashMap<>();
        for (SearchResult result : evidence) {
            if (result.getChunkId() != null) {
                map.putIfAbsent(result.getChunkId(), result);
            }
        }
        return new ArrayList<>(map.values());
    }

    private List<DraftItem> filterSourceChunkIds(List<DraftItem> drafts, List<SearchResult> evidence) {
        Set<String> validChunkIds = new LinkedHashSet<>();
        for (SearchResult result : evidence) {
            if (result.getChunkId() != null) {
                validChunkIds.add(result.getChunkId());
            }
        }
        return drafts.stream()
                .map(item -> new DraftItem(item.question(), item.knowledgeNote(), item.answer(), item.moduleTag(),
                        item.difficulty(), item.conflictTip(), filterSourceChunkIds(item.sourceChunkIds(), validChunkIds)))
                .toList();
    }

    private List<String> filterSourceChunkIds(List<String> sourceChunkIds, Set<String> validChunkIds) {
        if (sourceChunkIds == null) {
            return List.of();
        }
        return sourceChunkIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .filter(id -> validChunkIds.isEmpty() || validChunkIds.contains(id))
                .distinct()
                .toList();
    }

    private List<DraftItem> cleanDrafts(List<DraftItem> drafts, CreateTaskRequest request, List<SearchResult> evidence) {
        boolean strictEvidence = !Boolean.TRUE.equals(request.getAllowGeneralKnowledge());
        boolean hasEvidence = evidence != null && !evidence.isEmpty();
        return drafts.stream()
                .filter(item -> item != null)
                .filter(item -> hasText(item.question()) && hasText(item.answer()))
                .map(item -> sanitizeDraftItem(item, strictEvidence, hasEvidence))
                .toList();
    }

    private DraftItem sanitizeDraftItem(DraftItem item, boolean strictEvidence, boolean hasEvidence) {
        List<String> sourceChunkIds = item.sourceChunkIds() == null ? List.of() : item.sourceChunkIds();
        String conflictTip = safe(item.conflictTip());
        if (strictEvidence && sourceChunkIds.isEmpty()) {
            conflictTip = conflictTip.isBlank() ? "资料证据不足，答案仅保留为低置信度基础题" : conflictTip;
        }
        if (!hasEvidence && conflictTip.isBlank()) {
            conflictTip = "未检索到可用资料证据";
        }
        return new DraftItem(
                item.question().trim(),
                safe(item.knowledgeNote()).trim(),
                item.answer().trim(),
                hasText(item.moduleTag()) ? item.moduleTag().trim() : "General",
                item.difficulty() == null ? Difficulty.MEDIUM : item.difficulty(),
                conflictTip,
                sourceChunkIds
        );
    }

    private List<DraftItem> fallbackDrafts(PlanItem planItem, List<SearchResult> evidence) {
        List<String> chunkIds = evidence.stream().map(SearchResult::getChunkId).filter(id -> id != null).limit(3).toList();
        String evidenceText = evidence.isEmpty() ? "" : evidence.get(0).getContent();
        int count = Math.max(1, planItem.questionCount());
        List<DraftItem> drafts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            drafts.add(new DraftItem(
                    planItem.moduleTag() + " 的核心问题 " + (i + 1),
                    evidenceText,
                    evidenceText,
                    planItem.moduleTag(),
                    Difficulty.MEDIUM,
                    evidence.isEmpty() ? "资料证据不足" : "",
                    chunkIds
            ));
        }
        return drafts;
    }

    private PlanResult fallbackPlan(CreateTaskRequest request) {
        int count = questionCount(request);
        return new PlanResult(
                title(request),
                "根据用户资料生成的技术面试问答集",
                List.of(new PlanItem("General", count, new DifficultyDistribution(0, count, 0),
                        List.of("核心知识点"), List.of("概念题", "场景题")))
        );
    }

    private List<PlanItem> safePlanItems(PlanResult planResult, CreateTaskRequest request) {
        if (planResult == null || planResult.planItems() == null || planResult.planItems().isEmpty()) {
            return fallbackPlan(request).planItems();
        }
        return planResult.planItems().stream()
                .filter(item -> item.moduleTag() != null && !item.moduleTag().isBlank())
                .filter(item -> item.questionCount() > 0)
                .toList();
    }

    @SuppressWarnings("unchecked")
    private List<DraftItem> readDrafts(AgenticScope scope) {
        Object value = scope.readState("allDrafts");
        return value instanceof List<?> list ? (List<DraftItem>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    private List<DraftItem> readPassedDrafts(AgenticScope scope) {
        Object value = scope.readState("passedDrafts");
        return value instanceof List<?> list ? (List<DraftItem>) list : List.of();
    }

    @SuppressWarnings("unchecked")
    private List<SearchResult> readEvidence(AgenticScope scope) {
        Object value = scope.readState("allEvidence");
        return value instanceof List<?> list ? (List<SearchResult>) list : List.of();
    }

    private PlanResult readPlan(AgenticScope scope, CreateTaskRequest request) {
        Object value = scope.readState("planResult");
        return value instanceof PlanResult planResult ? planResult : fallbackPlan(request);
    }

    private int questionCount(CreateTaskRequest request) {
        return request.getRequestedQuestionCount() == null || request.getRequestedQuestionCount() <= 0
                ? DEFAULT_QUESTION_COUNT : request.getRequestedQuestionCount();
    }

    private String previousQuestions(List<DraftItem> previous) {
        return JSON.toJSONString(previous.stream()
                .filter(item -> item != null && item.question() != null)
                .map(DraftItem::question)
                .toList());
    }

    private String generationNote(CreateTaskRequest request, String extraNote) {
        String note = safe(request.getNote());
        if (!Boolean.TRUE.equals(request.getAllowGeneralKnowledge())) {
            note += "\n禁止使用资料外事实；证据不足时写 conflictTip。";
        }
        if (extraNote != null && !extraNote.isBlank()) {
            note += "\n" + extraNote;
        }
        return note;
    }

    private String answerStyle(CreateTaskRequest request) {
        return Boolean.TRUE.equals(request.getAllowGeneralKnowledge())
                ? "口头面试回答，可补充通用技术常识但必须标明资料证据边界"
                : "口头面试回答，严格基于资料证据";
    }

    private Map<String, Object> parseObject(String text) {
        try {
            return JSON.parseObject(extractJsonObject(text));
        } catch (Exception ignored) {
            return Map.of("valid", true, "reason", "parse fallback");
        }
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String loadPrompt(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private String title(CreateTaskRequest request) {
        return request.getTitle() == null || request.getTitle().isBlank() ? "生成问答集" : request.getTitle();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @SuppressWarnings("unchecked")
    private List<String> readStrings(AgenticScope scope, String key) {
        Object value = scope.readState(key);
        return value instanceof List<?> list ? (List<String>) list : List.of();
    }

    private int readInt(AgenticScope scope, String key) {
        Object value = scope.readState(key);
        return value instanceof Integer integer ? integer : 0;
    }

    private record ValidationLoopOutcome(List<DraftItem> passedDrafts, int rejectedCount) {
    }

    private class QaGenerationAgentListener implements AgentListener {

        private final String taskId;
        private final TaskEventPublisher publisher;
        private final AtomicInteger totalTokens;
        private final AtomicInteger lastPublishedTokens = new AtomicInteger(0);

        private QaGenerationAgentListener(String taskId, TaskEventPublisher publisher, AtomicInteger totalTokens) {
            this.taskId = taskId;
            this.publisher = publisher;
            this.totalTokens = totalTokens;
        }

        @Override
        public void afterAgentInvocation(AgentResponse response) {
            totalTokens.addAndGet(tokens(response.chatResponse()));
            GenerationStage stage = stageFromAgentName(response.agentName());
            String summary = summarizeStage(stage, response.output());
            int total = totalTokens.get();
            int current = total - lastPublishedTokens.getAndSet(total);
            publisher.publish(stage, GenerationStatus.PROCESSING, summary, current);
        }

        @Override
        public void onAgentInvocationError(AgentInvocationError error) {
            GenerationStage stage = stageFromAgentName(error.agentName());
            String message = error.error() == null ? "Agent 调用失败" : error.error().getMessage();
            log.warn("Agent invocation failed: taskId={}, agent={}, message={}", taskId, error.agentName(), message);
            publisher.publish(stage, GenerationStatus.PROCESSING, stage.name() + " 阶段出现可恢复错误：" + safe(message), 0);
        }

        @Override
        public boolean inheritedBySubagents() {
            return true;
        }

        private String summarizeStage(GenerationStage stage, Object output) {
            try {
                ChatResponse response = chat(supervisorChatModel,
                        loadPrompt("prompt/supervisor-summary.txt"),
                        "阶段：" + stage.name() + "\n产出：" + JSON.toJSONString(output),
                        totalTokens);
                return response.aiMessage().text();
            } catch (Exception e) {
                return stage.name() + " 阶段完成";
            }
        }

        private GenerationStage stageFromAgentName(String agentName) {
            if ("PLANNER".equals(agentName)) {
                return GenerationStage.PLANNER;
            }
            if ("DRAFTER".equals(agentName)) {
                return GenerationStage.CREATOR;
            }
            if ("EVALUATOR".equals(agentName) || "AMENDER".equals(agentName) || "VALIDATOR".equals(agentName)) {
                return GenerationStage.VALIDATOR;
            }
            if ("SUMMARIZER".equals(agentName)) {
                return GenerationStage.SUMMARIZER;
            }
            return GenerationStage.CREATOR;
        }
    }

    private class TaskEventPublisher {

        private final String taskId;
        private final Consumer<SseEvent> eventSink;
        private final AtomicInteger totalTokens;

        private TaskEventPublisher(String taskId, Consumer<SseEvent> eventSink, AtomicInteger totalTokens) {
            this.taskId = taskId;
            this.eventSink = eventSink;
            this.totalTokens = totalTokens;
        }

        private void publish(GenerationStage stage, GenerationStatus status, String message, int currentTokens) {
            SseEvent event = SseEvent.of(taskId, stage.name(), status.name(), message,
                    System.currentTimeMillis(), currentTokens, totalTokens.get());
            log.info("[task={}] [stage={}] {}", taskId, stage, message);
            agentRepository.appendTaskMessage(taskId, stage, message);
            eventSink.accept(event);
        }
    }

    private static class GenerationException extends RuntimeException {

        private final ErrorType errorType;

        private GenerationException(ErrorType errorType, String message) {
            super(message);
            this.errorType = errorType;
        }
    }
}
