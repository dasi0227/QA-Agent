package com.dasi.qa.agent.domain.agent.service.generate;

import com.alibaba.fastjson2.JSON;
import com.dasi.qa.agent.domain.agent.model.*;
import com.dasi.qa.agent.domain.agent.model.enumuration.Difficulty;
import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.generate.model.context.*;
import com.dasi.qa.agent.domain.agent.service.generate.model.exception.GenerateAbortedException;
import com.dasi.qa.agent.domain.agent.service.generate.model.exception.GenerateException;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.*;
import com.dasi.qa.agent.domain.agent.service.generate.support.*;
import com.dasi.qa.agent.domain.agent.service.generate.tool.RagSearchTool;
import com.dasi.qa.agent.domain.agent.service.generate.tool.WebSearchTool;
import com.dasi.qa.agent.domain.document.service.rag.search.ISearchService;
import com.dasi.qa.agent.types.dto.request.qa.CreateTaskRequest;
import com.dasi.qa.agent.types.dto.response.document.SearchResult;
import com.dasi.qa.agent.types.dto.sse.SseEvent;
import com.dasi.qa.agent.types.enumeration.ErrorType;
import com.dasi.qa.agent.types.enumeration.GenerationStage;
import com.dasi.qa.agent.types.enumeration.GenerationStatus;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
@Service
@Slf4j
public class GenerateAgent implements IGenerateAgent {

    private static final int DEFAULT_QUESTION_COUNT = 10;
    private static final int MAX_MODULE_QUESTIONS_PER_BATCH = 10;

    private final IAgentRepository agentRepository;
    private final GenerateAgentFactory generateAgentFactory;
    private final ISearchService searchService;
    private final UserLlmModelFactory userLlmModelFactory;
    private final ChatMemoryProvider chatMemoryProvider;
    private final EventPublisherFactory eventPublisherFactory;
    private final ChatModel webSearchModel;
    private final ChatModel supervisorChatModel;
    private final ThreadPoolTaskExecutor applicationTaskExecutor;

    public GenerateAgent(IAgentRepository agentRepository,
                         UserLlmModelFactory userLlmModelFactory,
                         GenerateAgentFactory generateAgentFactory,
                         ISearchService searchService,
                         ChatMemoryProvider chatMemoryProvider,
                         EventPublisherFactory eventPublisherFactory,
                         @Qualifier("webSearchModel") ChatModel webSearchModel,
                         @Qualifier("supervisorModel") ChatModel supervisorModel,
                         @Qualifier("applicationTaskExecutor") ThreadPoolTaskExecutor applicationTaskExecutor) {
        this.agentRepository = agentRepository;
        this.userLlmModelFactory = userLlmModelFactory;
        this.generateAgentFactory = generateAgentFactory;
        this.searchService = searchService;
        this.chatMemoryProvider = chatMemoryProvider;
        this.eventPublisherFactory = eventPublisherFactory;
        this.webSearchModel = webSearchModel;
        this.supervisorChatModel = supervisorModel;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    /**
     * 执行一次生成问答集主流程：
     * 1. 创建任务并推送 PENDING 事件
     * 2. 读取用户 LLM 配置
     * 3. 构建并运行 Generate DAG
     * 4. 成功则标记成功并推送 COMPLETED 事件，失败则捕获异常并推送 FAILED 事件
     *
     * @param userId    用户 id
     * @param request   创建问答集请求
     * @param eventSink 发送 SSE 事件函数
     */
    @Override
    public void execute(String userId, CreateTaskRequest request, Consumer<SseEvent> eventSink) {
        // 随机生成任务ID
        String taskId = UUID.randomUUID().toString();

        // 生成可追踪的创建任务
        agentRepository.createGenerationTask(taskId, userId, request);

        // 由于存在并发处理，所以需要用 Atomic 工具类确保线程安全
        AtomicInteger totalTokens = new AtomicInteger(0);

        // 创建当前任务的事件发送器并发送第一条 PENDING 消息
        EventPublisher eventPublisher = eventPublisherFactory.create(taskId, eventSink, totalTokens);
        eventPublisher.publishEvent(GenerationStage.PENDING, GenerationStatus.PROCESSING, "生成任务已创建", 0);

        try {
            // 拿到用户模型配置
            ChatModel userModel = userLlmModelFactory.getUserLlmModel(userId);

            // 获取智能体会用到的工具
            List<Object> creatorTools = getCreatorTools(userId, request);
            List<Object> amendmentTools = getValidatorTools(userId, request);

            // 获取链路的监听器，负责处理每个智能体的输出
            GenerateAgentListener listener = new GenerateAgentListener(taskId, eventPublisher, totalTokens, supervisorChatModel);

            //
            String documentsSummary = agentRepository.getDocumentsSummary(request.getDocumentIds(), userId);

            // 创建好智能体执行的上下文对象
            PlannerStageContext plannerStageContext = PlannerStageContext.builder()
                    .taskId(taskId)
                    .request(request)
                    .documentsSummary(documentsSummary)
                    .build();

            ValidatorStageContext validatorStageContext = ValidatorStageContext.builder()
                    .taskId(taskId)
                    .request(request)
                    .build();

            SummarizerStageContext summarizerStageContext = SummarizerStageContext.builder()
                    .taskId(taskId)
                    .userId(userId)
                    .request(request)
                    .publisher(eventPublisher)
                    .build();

            CreatorStageContext creatorStageContext = CreatorStageContext.builder()
                    .taskId(taskId)
                    .userId(userId)
                    .request(request)
                    .executor(applicationTaskExecutor)
                    .build();

            // 汇总上下文，构造得到任务的上下文
            GenerateContext generateContext = GenerateContext.builder()
                    .taskId(taskId)
                    .userModel(userModel)
                    .chatMemoryProvider(chatMemoryProvider)
                    .listener(listener)
                    .eventPublisher(eventPublisher)
                    .creatorTools(creatorTools)
                    .amendmentTools(amendmentTools)
                    .decideStep((scope, decideAgent) -> runDecide(scope, decideAgent, taskId))
                    .abortStep((scope, abortAgent) -> runAbort(scope, abortAgent, eventPublisher))
                    .planStep((scope, planAgent) -> runPlanner(scope, planAgent, plannerStageContext))
                    .createStep((scope, draftAgent, searchAgent) -> runCreator(scope, draftAgent, searchAgent, creatorStageContext))
                    .validateStep((scope, evaluateAgent, amendAgent) -> runValidator(scope, evaluateAgent, amendAgent, validatorStageContext))
                    .summarizeStep((scope, summarizeAgent) -> runSummarizer(scope, summarizeAgent, summarizerStageContext))
                    .build();

            // 创建 Generate Agent 的完整 DAG 结构
            UntypedAgent generateAgent = generateAgentFactory.build(generateContext);

            // 创建 Scope 的初始值
            Map<String, Object> initialData = Map.of(
                    "taskId", taskId,
                    "userPrompt", safe(request.getUserPrompt())
            );

            // 开始启动 DAG 执行智能体
            generateAgent.invoke(initialData);
        } catch (GenerateAbortedException exception) {
            return;
        } catch (GenerateException exception) {
            fail(taskId, eventPublisher, exception.getErrorType(), exception.getMessage());
        } catch (Exception exception) {
            if (isAborted(exception)) {
                return;
            }
            fail(taskId, eventPublisher, classifyError(exception), exception.getMessage());
        }
    }

    /**
     * 构建 Creator 阶段的工具列表
     * - 根据用户指定的笔记资料进行 RagSearch
     * - 当用户允许联网搜索时进行 WebSearch
     */
    private List<Object> getCreatorTools(String userId, CreateTaskRequest request) {
        List<Object> tools = new ArrayList<>();
        tools.add(new RagSearchTool(searchService, userId, request.getDocumentIds()));
        if (Boolean.TRUE.equals(request.getAllowWebSearch())) {
            tools.add(new WebSearchTool(webSearchModel));
        }
        return tools;
    }


    /**
     * 构建 Validator 阶段的工具列表
     * - 根据用户指定的笔记资料进行 RagSearch
     */
    private List<Object> getValidatorTools(String userId, CreateTaskRequest request) {
        return List.of(new RagSearchTool(searchService, userId, request.getDocumentIds()));
    }

    private void runDecide(AgenticScope scope, DecideAgent decideAgent, String taskId) {
        agentRepository.updateTaskStage(taskId, GenerationStatus.PROCESSING, GenerationStage.DECIDE);
        Object userPrompt = scope.readState("userPrompt");
        DecideResult decideResult = decideAgent.decide(taskId, userPrompt == null ? "" : String.valueOf(userPrompt));
        scope.writeState("decideResult", decideResult);
    }

    private void runAbort(AgenticScope scope, AbortAgent abortAgent, EventPublisher eventPublisher) {
        abortAgent.abort(scope, eventPublisher);
    }

    private void runPlanner(AgenticScope scope, PlanAgent planAgent, PlannerStageContext context) {
        agentRepository.updateTaskStage(context.getTaskId(), GenerationStatus.PROCESSING, GenerationStage.PLANNER);
        PlanResult planResult;
        try {
            planResult = planAgent.plan(context.getTaskId(), context.getDocumentsSummary(), "", "", "",
                    safe(context.getRequest().getUserPrompt()), questionCount(context.getRequest()));
        } catch (Exception exception) {
            log.warn("PlanAgent failed, fallback plan used: taskId={}, message={}", context.getTaskId(), exception.getMessage());
            planResult = fallbackPlan(context.getRequest());
        }
        scope.writeState("planResult", normalizePlan(planResult, context.getRequest()));
    }

    private void runCreator(AgenticScope scope, DraftAgent draftAgent, SearchAgent searchAgent, CreatorStageContext context) {
        agentRepository.updateTaskStage(context.getTaskId(), GenerationStatus.PROCESSING, GenerationStage.CREATOR);
        PlanResult planResult = readPlan(scope, context.getRequest());
        List<DraftItem> allDrafts = Collections.synchronizedList(new ArrayList<>());
        List<SearchResult> allEvidence = Collections.synchronizedList(new ArrayList<>());
        List<String> failedModules = Collections.synchronizedList(new ArrayList<>());
        List<Object> moduleAgents = new ArrayList<>();

        for (PlanItem planItem : safePlanItems(planResult, context.getRequest())) {
            moduleAgents.add(AgenticServices.agentAction(moduleScope -> {
                try {
                    List<SearchResult> evidence = searchAgent.search(context.getUserId(), context.getRequest().getDocumentIds(), planItem);
                    allEvidence.addAll(evidence);
                    allDrafts.addAll(draftModule(draftAgent, DraftModuleContext.builder()
                            .taskId(context.getTaskId())
                            .request(context.getRequest())
                            .planItem(planItem)
                            .evidence(evidence)
                            .previousDrafts(allDrafts)
                            .build()));
                } catch (Exception exception) {
                    failedModules.add(planItem.moduleTag() + ": " + safe(exception.getMessage()));
                    log.warn("Creator module failed: taskId={}, module={}, message={}",
                            context.getTaskId(), planItem.moduleTag(), exception.getMessage());
                }
            }));
        }

        UntypedAgent creator = AgenticServices.parallelBuilder()
                .name("CREATOR")
                .description("按模块并发执行 SearchAgent 到 DraftAgent")
                .executor(context.getExecutor())
                .subAgents(moduleAgents)
                .output(moduleScope -> allDrafts)
                .build();
        creator.invoke(Map.of("taskId", context.getTaskId()));

        List<DraftItem> deduplicated = deduplicate(allDrafts);
        scope.writeState("allDrafts", deduplicated);
        scope.writeState("allEvidence", deduplicateEvidence(allEvidence));
        if (!failedModules.isEmpty()) {
            scope.writeState("creatorFailedModules", List.copyOf(failedModules));
        }
    }

    private List<DraftItem> draftModule(DraftAgent draftAgent, DraftModuleContext context) {
        int remaining = Math.max(0, context.getPlanItem().questionCount());
        List<DraftItem> moduleDrafts = new ArrayList<>();
        while (remaining > 0) {
            int batchCount = Math.min(MAX_MODULE_QUESTIONS_PER_BATCH, remaining);
            List<DraftItem> batch = draftBatch(draftAgent, DraftBatchContext.builder()
                    .taskId(context.getTaskId())
                    .request(context.getRequest())
                    .planItem(context.getPlanItem())
                    .evidence(context.getEvidence())
                    .previousQuestions(previousQuestions(context.getPreviousDrafts()) + previousQuestions(moduleDrafts))
                    .batchCount(batchCount)
                    .extraNote("")
                    .build());
            moduleDrafts.addAll(batch);
            remaining -= batchCount;
        }
        return moduleDrafts;
    }

    private List<DraftItem> draftBatch(DraftAgent draftAgent, DraftBatchContext context) {
        try {
            String response = draftAgent.draft(
                    context.getTaskId(),
                    context.getPlanItem().moduleTag(),
                    JSON.toJSONString(context.getEvidence()),
                    "",
                    "",
                    answerStyle(context.getRequest()),
                    JSON.toJSONString(context.getPlanItem().difficultyDistribution()),
                    context.getBatchCount(),
                    context.getPreviousQuestions(),
                    generationNote(context.getRequest(), context.getExtraNote())
            );
            List<DraftItem> parsed = JSON.parseArray(extractJsonArray(response), DraftItem.class);
            return parsed == null ? List.of() : parsed;
        } catch (Exception exception) {
            log.warn("DraftAgent failed, fallback drafts used: module={}, message={}",
                    context.getPlanItem().moduleTag(), exception.getMessage());
            return fallbackDrafts(new PlanItem(context.getPlanItem().moduleTag(), context.getBatchCount(),
                    context.getPlanItem().difficultyDistribution(),
                    context.getPlanItem().focusTopics(),
                    context.getPlanItem().suggestedQuestionTypes()), context.getEvidence());
        }
    }

    private void runValidator(AgenticScope scope, EvaluateAgent evaluateAgent, AmendAgent amendAgent,
                              ValidatorStageContext context) {
        agentRepository.updateTaskStage(context.getTaskId(), GenerationStatus.PROCESSING, GenerationStage.VALIDATOR);
        List<DraftItem> drafts = readDrafts(scope);
        List<SearchResult> evidence = readEvidence(scope);
        ValidationCoordinator.ValidationOutcome outcome = new ValidationCoordinator(MAX_MODULE_QUESTIONS_PER_BATCH)
                .run(context.getTaskId(), context.getRequest(), evaluateAgent, amendAgent, drafts, evidence);

        List<DraftItem> finalDrafts = cleanDrafts(filterSourceChunkIds(deduplicate(outcome.passedDrafts()), evidence),
                context.getRequest(), evidence);
        if (finalDrafts.isEmpty()) {
            throw new GenerateException(ErrorType.ALL_REJECTED, "Evaluator 未通过任何题目");
        }
        scope.writeState("passedDrafts", finalDrafts);
        scope.writeState("rejectedCount", outcome.rejectedCount());
    }

    private void runSummarizer(AgenticScope scope, SummarizeAgent summarizeAgent, SummarizerStageContext context) {
        agentRepository.updateTaskStage(context.getTaskId(), GenerationStatus.PROCESSING, GenerationStage.SUMMARIZER);
        PlanResult planResult = readPlan(scope, context.getRequest());
        List<DraftItem> passedDrafts = readPassedDrafts(scope);
        SummarizeAgent.SummaryResult summary = summarizeAgent.summarize(context.getTaskId(), context.getUserId(), context.getRequest(), planResult,
                passedDrafts, readInt(scope, "rejectedCount"), readStrings(scope, "creatorFailedModules"),
                context.getPublisher().totalTokens());
        agentRepository.markTaskCompleted(context.getTaskId(), summary.qaSetId());
        scope.writeState("qaSetId", summary.qaSetId());
        context.getPublisher().publishEvent(GenerationStage.COMPLETED, GenerationStatus.COMPLETED, summary.message(), 0);
    }

    private void fail(String taskId, EventPublisher publisher, ErrorType errorType, String message) {
        String errorMessage = message == null || message.isBlank() ? errorType.name() : message;
        log.error("QA generation task failed: taskId={}, errorType={}, message={}", taskId, errorType, errorMessage);
        publisher.publishFailure(errorType, errorMessage);
    }

    private boolean isAborted(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof GenerateAbortedException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
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

    private List<DraftItem> readDrafts(AgenticScope scope) {
        Object value = scope.readState("allDrafts");
        return value instanceof List<?> list ? (List<DraftItem>) list : List.of();
    }

    private List<DraftItem> readPassedDrafts(AgenticScope scope) {
        Object value = scope.readState("passedDrafts");
        return value instanceof List<?> list ? (List<DraftItem>) list : List.of();
    }

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
        String note = safe(request.getUserPrompt());
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

    private String extractJsonArray(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
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

}
