package com.dasi.qa.agent.domain.agent.service.generate;

import com.alibaba.fastjson2.JSON;
import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.generate.model.context.*;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GeneratePhase;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GenerateStatus;
import com.dasi.qa.agent.domain.agent.service.generate.model.exception.GenerateException;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.*;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.*;
import com.dasi.qa.agent.domain.agent.service.generate.support.*;
import com.dasi.qa.agent.domain.agent.service.generate.tool.RagSearchTool;
import com.dasi.qa.agent.domain.agent.service.generate.tool.WebSearchTool;
import com.dasi.qa.agent.domain.agent.shared.enumeration.Difficulty;
import com.dasi.qa.agent.domain.agent.shared.enumeration.ErrorType;
import com.dasi.qa.agent.domain.agent.shared.sse.EventPublisher;
import com.dasi.qa.agent.domain.agent.shared.sse.SseEvent;
import com.dasi.qa.agent.domain.document.service.rag.search.ISearchService;
import com.dasi.qa.agent.domain.util.IPromptUtil;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
import com.dasi.qa.agent.types.dto.response.document.SearchResult;
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

    private final IPromptUtil promptUtil;
    private final IAgentRepository agentRepository;
    private final GenerateAgentFactory generateAgentFactory;
    private final ISearchService searchService;
    private final RagEvidenceProvider ragEvidenceProvider;
    private final UserLlmModelProvider userLlmModelProvider;
    private final ChatMemoryProvider chatMemoryProvider;
    private final ChatModel webSearchModel;
    private final ChatModel supervisorChatModel;
    private final ThreadPoolTaskExecutor applicationTaskExecutor;

    public GenerateAgent(IPromptUtil promptUtil,
                         IAgentRepository agentRepository,
                         UserLlmModelProvider userLlmModelProvider,
                         GenerateAgentFactory generateAgentFactory,
                         ISearchService searchService,
                         RagEvidenceProvider ragEvidenceProvider,
                         ChatMemoryProvider chatMemoryProvider,
                         @Qualifier("webSearchModel") ChatModel webSearchModel,
                         @Qualifier("supervisorModel") ChatModel supervisorModel,
                         @Qualifier("applicationTaskExecutor") ThreadPoolTaskExecutor applicationTaskExecutor) {
        this.promptUtil = promptUtil;
        this.agentRepository = agentRepository;
        this.userLlmModelProvider = userLlmModelProvider;
        this.generateAgentFactory = generateAgentFactory;
        this.searchService = searchService;
        this.ragEvidenceProvider = ragEvidenceProvider;
        this.chatMemoryProvider = chatMemoryProvider;
        this.webSearchModel = webSearchModel;
        this.supervisorChatModel = supervisorModel;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    /**
     * 执行一次生成问答集主流程：
     * 1. 创建任务并推送 PENDING 事件
     * 2. 读取用户 LLM 配置
     * 3. 构建并运行 Generate DAG
     * 4. 按异常类型统一发布失败结果
     *
     * @param userId    当前用户 ID
     * @param request   创建问答集的请求参数
     * @param sseEventHandler SSE 事件消费回调
     */
    @Override
    public void execute(String userId, CreateQaSetRequest request, Consumer<SseEvent> sseEventHandler) {
        // 生成本次任务唯一标识
        String taskId = UUID.randomUUID().toString();

        // 写入任务主记录，确保后续状态可追踪
        agentRepository.createGenerationTask(taskId, userId, request);

        // 跨阶段累计 token，用于并发场景下的线程安全统计
        AtomicInteger totalTokens = new AtomicInteger(0);

        // 创建事件发布器
        EventPublisher eventPublisher = new EventPublisher(agentRepository, taskId, sseEventHandler, totalTokens);

        // 发送任务创建事件
        eventPublisher.publishEvent(GeneratePhase.INIT, GenerateStatus.PROCESSING, "生成任务已创建", 0);

        try {
            // 读取并构建用户专属模型
            ChatModel userModel = userLlmModelProvider.getUserLlmModel(userId);

            // 准备可调用工具
            RagSearchTool ragSearchTool = new RagSearchTool(searchService, userId, request.getDocumentIds());
            WebSearchTool webSearchTool = new WebSearchTool(webSearchModel, promptUtil);
            List<Object> writeTools = Boolean.TRUE.equals(request.getAllowWebSearch())
                    ? List.of(ragSearchTool, webSearchTool)
                    : List.of(ragSearchTool);
            List<Object> validateTools = List.of(ragSearchTool);

            // 创建链路监听器，负责汇总阶段输出与 token 信息
            GenerateAgentListener agentListener = new GenerateAgentListener(taskId, promptUtil, eventPublisher, totalTokens, supervisorChatModel);

            // 组装各阶段执行上下文
            PlanContext planContext = PlanContext.builder()
                    .taskId(taskId)
                    .userId(userId)
                    .request(request)
                    .build();

            ValidateContext validateContext = ValidateContext.builder()
                    .taskId(taskId)
                    .request(request)
                    .build();

            DecideContext decideContext = DecideContext.builder()
                    .taskId(taskId)
                    .request(request)
                    .build();

            AbortContext abortContext = AbortContext.builder()
                    .taskId(taskId)
                    .request(request)
                    .eventPublisher(eventPublisher)
                    .build();

            SummarizeContext summarizeContext = SummarizeContext.builder()
                    .taskId(taskId)
                    .userId(userId)
                    .request(request)
                    .eventPublisher(eventPublisher)
                    .build();

            WriteContext writeContext = WriteContext.builder()
                    .taskId(taskId)
                    .userId(userId)
                    .request(request)
                    .executor(applicationTaskExecutor)
                    .ragEvidenceProvider(ragEvidenceProvider)
                    .build();

            // 汇总 DAG 运行上下文，并传入各阶段执行回调
            GenerateContext generateContext = GenerateContext.builder()
                    .userModel(userModel)
                    .writeTools(writeTools)
                    .validateTools(validateTools)
                    .agentListener(agentListener)
                    .chatMemoryProvider(chatMemoryProvider)
                    .decideStep((scope, decideAgent) -> runDecide(scope, decideAgent, decideContext))
                    .abortStep((scope, abortAgent) -> runAbort(scope, abortAgent, abortContext))
                    .planStep((scope, planAgent) -> runPlan(scope, planAgent, planContext))
                    .writeStep((scope, draftAgent) -> runWrite(scope, draftAgent, writeContext))
                    .validateStep((scope, evaluateAgent, amendAgent) -> runValidate(scope, evaluateAgent, amendAgent, validateContext))
                    .summarizeStep((scope, summarizeAgent) -> runSummarize(scope, summarizeAgent, summarizeContext))
                    .build();

            // 构建任务 DAG
            UntypedAgent generateAgent = generateAgentFactory.build(generateContext);

            // 初始化 Scope 数据，供各阶段读取
            Map<String, Object> initialData = Map.of(
                    "taskId", taskId,
                    "userPrompt", safe(request.getUserPrompt())
            );

            // 启动 DAG 执行
            generateAgent.invoke(initialData);
        }
        // 已知业务异常：按类型发布失败事件
        catch (GenerateException exception) {
            eventPublisher.publishFailure(exception.getErrorType(), exception.getMessage());
        }
        // 未知异常：映射错误类型后发布失败事件
        catch (Exception exception) {
            eventPublisher.publishFailure(ErrorType.fromException(exception), exception.getMessage());
        }
    }

    private void runDecide(AgenticScope scope, DecideAgent decideAgent, DecideContext context) {
        agentRepository.updateTaskStatus(context.getTaskId(), GenerateStatus.PROCESSING, GeneratePhase.DECIDE);
        DecideResult decideResult = decideAgent.decide(context.getTaskId(), safe(context.getRequest().getUserPrompt()));
        scope.writeState("decideResult", decideResult);
    }

    private void runAbort(AgenticScope scope, AbortAgent abortAgent, AbortContext context) {
        DecideResult decideResult = readDecideResult(scope);
        String reason = hasText(decideResult.getReason()) ? decideResult.getReason() : "用户要求与生成问答集无关";
        String message;
        try {
            message = abortAgent.abort(context.getTaskId(), safe(context.getRequest().getUserPrompt()), reason);
        } catch (Exception exception) {
            log.warn("AbortAgent failed, fallback reason used: message={}", exception.getMessage());
            message = reason;
        }
        if (!hasText(message)) {
            message = reason;
        }
        context.getEventPublisher().publishFailure(ErrorType.CONTENT_FILTERED, message);
    }

    private void runPlan(AgenticScope scope, PlanAgent planAgent, PlanContext context) {
        agentRepository.updateTaskStatus(context.getTaskId(), GenerateStatus.PROCESSING, GeneratePhase.PLAN);
        PlanResult planResult;
        // 读取用户所选资料，作为规划阶段输入上下文
        String documentsSummary = agentRepository.getDocumentsSummary(
                context.getRequest().getDocumentIds(),
                context.getUserId()
        );

        try {
            planResult = planAgent.plan(context.getTaskId(), documentsSummary, "", "", "",
                    safe(context.getRequest().getUserPrompt()), questionCount(context.getRequest()));
        } catch (Exception exception) {
            log.warn("PlanAgent failed, fallback plan used: taskId={}, message={}", context.getTaskId(), exception.getMessage());
            planResult = fallbackPlan(context.getRequest());
        }
        scope.writeState("planResult", normalizePlan(planResult, context.getRequest()));
    }

    private void runWrite(AgenticScope scope, DraftAgent draftAgent, WriteContext context) {
        agentRepository.updateTaskStatus(context.getTaskId(), GenerateStatus.PROCESSING, GeneratePhase.CREATE);
        PlanResult planResult = readPlan(scope, context.getRequest());
        List<DraftItem> allDrafts = Collections.synchronizedList(new ArrayList<>());
        List<SearchResult> allEvidence = Collections.synchronizedList(new ArrayList<>());
        List<String> failedModules = Collections.synchronizedList(new ArrayList<>());
        List<Object> moduleAgents = new ArrayList<>();

        for (PlanItem planItem : safePlanItems(planResult, context.getRequest())) {
            moduleAgents.add(AgenticServices.agentAction(moduleScope -> {
                try {
                    List<SearchResult> evidence = context.getRagEvidenceProvider()
                            .search(context.getUserId(), context.getRequest().getDocumentIds(), planItem);
                    allEvidence.addAll(evidence);
                    allDrafts.addAll(draftModule(draftAgent, DraftModuleContext.builder()
                            .taskId(context.getTaskId())
                            .request(context.getRequest())
                            .planItem(planItem)
                            .evidence(evidence)
                            .previousDrafts(allDrafts)
                            .build()));
                } catch (Exception exception) {
                    failedModules.add(planItem.getModuleTag() + ": " + safe(exception.getMessage()));
                    log.warn("Write module failed: taskId={}, module={}, message={}",
                            context.getTaskId(), planItem.getModuleTag(), exception.getMessage());
                }
            }));
        }

        UntypedAgent writer = AgenticServices.parallelBuilder()
                .name("WRITE")
                .description("按模块并发执行 draft 分支")
                .executor(context.getExecutor())
                .subAgents(moduleAgents)
                .output(moduleScope -> allDrafts)
                .build();
        writer.invoke(Map.of("taskId", context.getTaskId()));

        List<DraftItem> deduplicated = deduplicate(allDrafts);
        scope.writeState("allDrafts", deduplicated);
        scope.writeState("allEvidence", deduplicateEvidence(allEvidence));
        if (!failedModules.isEmpty()) {
            scope.writeState("writeFailedModules", List.copyOf(failedModules));
        }
    }

    private List<DraftItem> draftModule(DraftAgent draftAgent, DraftModuleContext context) {
        int remaining = Math.max(0, context.getPlanItem().getQuestionCount());
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
                    context.getPlanItem().getModuleTag(),
                    JSON.toJSONString(context.getEvidence()),
                    "",
                    "",
                    answerStyle(context.getRequest()),
                    JSON.toJSONString(context.getPlanItem().getDifficultyDistribution()),
                    context.getBatchCount(),
                    context.getPreviousQuestions(),
                    generationNote(context.getRequest(), context.getExtraNote())
            );
            List<DraftItem> parsed = JSON.parseArray(extractJsonArray(response), DraftItem.class);
            return parsed == null ? List.of() : parsed;
        } catch (Exception exception) {
            log.warn("DraftAgent failed, fallback drafts used: module={}, message={}",
                    context.getPlanItem().getModuleTag(), exception.getMessage());
            return fallbackDrafts(new PlanItem(context.getPlanItem().getModuleTag(), context.getBatchCount(),
                    context.getPlanItem().getDifficultyDistribution(),
                    context.getPlanItem().getFocusTopics(),
                    context.getPlanItem().getSuggestedQuestionTypes()), context.getEvidence());
        }
    }

    private void runValidate(AgenticScope scope, EvaluateAgent evaluateAgent, AmendAgent amendAgent, ValidateContext context) {
        agentRepository.updateTaskStatus(context.getTaskId(), GenerateStatus.PROCESSING, GeneratePhase.VALIDATE);
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

    private void runSummarize(AgenticScope scope, SummarizeAgent summarizeAgent, SummarizeContext context) {
        agentRepository.updateTaskStatus(context.getTaskId(), GenerateStatus.PROCESSING, GeneratePhase.SUMMARIZE);
        PlanResult planResult = readPlan(scope, context.getRequest());
        List<DraftItem> passedDrafts = readPassedDrafts(scope);
        int rejectedCount = readInt(scope, "rejectedCount");
        List<String> failedModules = readStrings(scope, "writeFailedModules");
        String qaSetId = agentRepository.saveGeneratedQaSet(
                context.getTaskId(),
                context.getUserId(),
                context.getRequest(),
                planResult,
                passedDrafts
        );
        String summaryMessage;
        try {
            summaryMessage = summarizeAgent.summarize(
                    context.getTaskId(),
                    safe(context.getRequest().getUserPrompt()),
                    title(context.getRequest()),
                    JSON.toJSONString(planResult),
                    plannedCount(planResult, context.getRequest()),
                    passedDrafts.size(),
                    rejectedCount,
                    JSON.toJSONString(failedModules),
                    JSON.toJSONString(moduleCounts(passedDrafts)),
                    JSON.toJSONString(difficultyCounts(passedDrafts)),
                    context.getEventPublisher().totalTokens()
            );
        } catch (Exception exception) {
            log.warn("SummarizeAgent failed, fallback summary used: message={}", exception.getMessage());
            summaryMessage = fallbackSummaryMessage(planResult, context.getRequest(), passedDrafts, rejectedCount,
                    failedModules, context.getEventPublisher().totalTokens());
        }
        if (!hasText(summaryMessage)) {
            summaryMessage = fallbackSummaryMessage(planResult, context.getRequest(), passedDrafts, rejectedCount,
                    failedModules, context.getEventPublisher().totalTokens());
        }
        agentRepository.markTaskCompleted(context.getTaskId(), qaSetId);
        scope.writeState("qaSetId", qaSetId);
        context.getEventPublisher().publishEvent(GeneratePhase.COMPLETE, GenerateStatus.SOLVED, summaryMessage, 0);
    }

    private PlanResult normalizePlan(PlanResult planResult, CreateQaSetRequest request) {
        List<PlanItem> items = safePlanItems(planResult, request);
        int total = items.stream().mapToInt(item -> Math.max(0, item.getQuestionCount())).sum();
        int target = questionCount(request);
        if (total != target && !items.isEmpty()) {
            PlanItem first = items.get(0);
            int fixedCount = Math.max(1, first.getQuestionCount() + target - total);
            List<PlanItem> fixedItems = new ArrayList<>(items);
            fixedItems.set(0, new PlanItem(first.getModuleTag(), fixedCount,
                    normalizeDistribution(first.getDifficultyDistribution(), fixedCount),
                    first.getFocusTopics(), first.getSuggestedQuestionTypes()));
            items = fixedItems;
        }
        return new PlanResult(
                planResult == null || planResult.getTitle() == null || planResult.getTitle().isBlank()
                        ? title(request) : planResult.getTitle(),
                planResult == null || planResult.getDescription() == null ? "" : planResult.getDescription(),
                items.stream()
                        .map(item -> new PlanItem(item.getModuleTag(), item.getQuestionCount(),
                                normalizeDistribution(item.getDifficultyDistribution(), item.getQuestionCount()),
                                item.getFocusTopics() == null ? List.of() : item.getFocusTopics(),
                                item.getSuggestedQuestionTypes() == null ? List.of() : item.getSuggestedQuestionTypes()))
                        .toList()
        );
    }

    private DifficultyDistribution normalizeDistribution(DifficultyDistribution distribution, int questionCount) {
        if (distribution == null) {
            return new DifficultyDistribution(0, questionCount, 0);
        }
        int total = distribution.getEasy() + distribution.getMedium() + distribution.getHard();
        if (total == questionCount) {
            return distribution;
        }
        return new DifficultyDistribution(distribution.getEasy(), Math.max(0, questionCount - distribution.getEasy() - distribution.getHard()), distribution.getHard());
    }

    private List<DraftItem> deduplicate(List<DraftItem> items) {
        Map<String, DraftItem> map = new LinkedHashMap<>();
        for (DraftItem item : items) {
            if (item != null && item.getQuestion() != null && !item.getQuestion().isBlank()) {
                map.putIfAbsent(item.getQuestion(), item);
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
                .map(item -> new DraftItem(item.getQuestion(), item.getKnowledgeNote(), item.getAnswer(), item.getModuleTag(),
                        item.getDifficulty(), item.getConflictTip(), filterSourceChunkIds(item.getSourceChunkIds(), validChunkIds)))
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

    private List<DraftItem> cleanDrafts(List<DraftItem> drafts, CreateQaSetRequest request, List<SearchResult> evidence) {
        boolean strictEvidence = !Boolean.TRUE.equals(request.getAllowGeneralKnowledge());
        boolean hasEvidence = evidence != null && !evidence.isEmpty();
        return drafts.stream()
                .filter(item -> item != null)
                .filter(item -> hasText(item.getQuestion()) && hasText(item.getAnswer()))
                .map(item -> sanitizeDraftItem(item, strictEvidence, hasEvidence))
                .toList();
    }

    private DraftItem sanitizeDraftItem(DraftItem item, boolean strictEvidence, boolean hasEvidence) {
        List<String> sourceChunkIds = item.getSourceChunkIds() == null ? List.of() : item.getSourceChunkIds();
        String conflictTip = safe(item.getConflictTip());
        if (strictEvidence && sourceChunkIds.isEmpty()) {
            conflictTip = conflictTip.isBlank() ? "资料证据不足，答案仅保留为低置信度基础题" : conflictTip;
        }
        if (!hasEvidence && conflictTip.isBlank()) {
            conflictTip = "未检索到可用资料证据";
        }
        return new DraftItem(
                item.getQuestion().trim(),
                safe(item.getKnowledgeNote()).trim(),
                item.getAnswer().trim(),
                hasText(item.getModuleTag()) ? item.getModuleTag().trim() : "General",
                item.getDifficulty() == null ? Difficulty.MEDIUM : item.getDifficulty(),
                conflictTip,
                sourceChunkIds
        );
    }

    private List<DraftItem> fallbackDrafts(PlanItem planItem, List<SearchResult> evidence) {
        List<String> chunkIds = evidence.stream().map(SearchResult::getChunkId).filter(id -> id != null).limit(3).toList();
        String evidenceText = evidence.isEmpty() ? "" : evidence.get(0).getContent();
        int count = Math.max(1, planItem.getQuestionCount());
        List<DraftItem> drafts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            drafts.add(new DraftItem(
                    planItem.getModuleTag() + " 的核心问题 " + (i + 1),
                    evidenceText,
                    evidenceText,
                    planItem.getModuleTag(),
                    Difficulty.MEDIUM,
                    evidence.isEmpty() ? "资料证据不足" : "",
                    chunkIds
            ));
        }
        return drafts;
    }

    private PlanResult fallbackPlan(CreateQaSetRequest request) {
        int count = questionCount(request);
        return new PlanResult(
                title(request),
                "根据用户资料生成的技术面试问答集",
                List.of(new PlanItem("General", count, new DifficultyDistribution(0, count, 0),
                        List.of("核心知识点"), List.of("概念题", "场景题")))
        );
    }

    private List<PlanItem> safePlanItems(PlanResult planResult, CreateQaSetRequest request) {
        if (planResult == null || planResult.getPlanItems() == null || planResult.getPlanItems().isEmpty()) {
            return fallbackPlan(request).getPlanItems();
        }
        return planResult.getPlanItems().stream()
                .filter(item -> item.getModuleTag() != null && !item.getModuleTag().isBlank())
                .filter(item -> item.getQuestionCount() > 0)
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

    private PlanResult readPlan(AgenticScope scope, CreateQaSetRequest request) {
        Object value = scope.readState("planResult");
        return value instanceof PlanResult planResult ? planResult : fallbackPlan(request);
    }

    private int questionCount(CreateQaSetRequest request) {
        return request.getRequestedQuestionCount() == null || request.getRequestedQuestionCount() <= 0
                ? DEFAULT_QUESTION_COUNT : request.getRequestedQuestionCount();
    }

    private String previousQuestions(List<DraftItem> previous) {
        return JSON.toJSONString(previous.stream()
                .filter(item -> item != null && item.getQuestion() != null)
                .map(DraftItem::getQuestion)
                .toList());
    }

    private String generationNote(CreateQaSetRequest request, String extraNote) {
        String note = safe(request.getUserPrompt());
        if (!Boolean.TRUE.equals(request.getAllowGeneralKnowledge())) {
            note += "\n禁止使用资料外事实；证据不足时写 conflictTip。";
        }
        if (extraNote != null && !extraNote.isBlank()) {
            note += "\n" + extraNote;
        }
        return note;
    }

    private String answerStyle(CreateQaSetRequest request) {
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

    private String title(CreateQaSetRequest request) {
        return request.getTitle() == null || request.getTitle().isBlank() ? "生成问答集" : request.getTitle();
    }

    private int plannedCount(PlanResult planResult, CreateQaSetRequest request) {
        if (planResult != null && planResult.getPlanItems() != null && !planResult.getPlanItems().isEmpty()) {
            return planResult.getPlanItems().stream()
                    .mapToInt(item -> Math.max(0, item.getQuestionCount()))
                    .sum();
        }
        return request.getRequestedQuestionCount() == null ? 0 : request.getRequestedQuestionCount();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private List<String> readStrings(AgenticScope scope, String key) {
        Object value = scope.readState(key);
        return value instanceof List<?> list ? (List<String>) list : List.of();
    }

    private int readInt(AgenticScope scope, String key) {
        Object value = scope.readState(key);
        return value instanceof Integer integer ? integer : 0;
    }

    private DecideResult readDecideResult(AgenticScope scope) {
        Object value = scope.readState("decideResult");
        return value instanceof DecideResult result
                ? result
                : new DecideResult(false, "用户要求与生成问答集无关");
    }

    private Map<String, Long> moduleCounts(List<DraftItem> draftItems) {
        Map<String, Long> moduleCounts = new LinkedHashMap<>();
        for (DraftItem item : draftItems) {
            String moduleTag = hasText(item.getModuleTag()) ? item.getModuleTag().trim() : "General";
            moduleCounts.merge(moduleTag, 1L, Long::sum);
        }
        return moduleCounts;
    }

    private Map<String, Long> difficultyCounts(List<DraftItem> draftItems) {
        Map<String, Long> difficultyCounts = new LinkedHashMap<>();
        for (Difficulty difficulty : Difficulty.values()) {
            long count = draftItems.stream()
                    .filter(item -> item.getDifficulty() == difficulty)
                    .count();
            difficultyCounts.put(difficulty.name(), count);
        }
        return difficultyCounts;
    }

    private String fallbackSummaryMessage(PlanResult planResult, CreateQaSetRequest request, List<DraftItem> draftItems,
                                          int rejectedCount, List<String> failedModules, int totalTokens) {
        String message = "问答集已生成，共 " + draftItems.size() + " 题（计划 " + plannedCount(planResult, request)
                + " 题，未通过或丢弃 " + rejectedCount + " 题）。模块分布：" + moduleCounts(draftItems)
                + "。难度分布：" + difficultyCounts(draftItems) + "。Write 失败模块 "
                + (failedModules == null ? 0 : failedModules.size()) + " 个，累计消耗 "
                + totalTokens + " tokens。";
        return message;
    }

}
