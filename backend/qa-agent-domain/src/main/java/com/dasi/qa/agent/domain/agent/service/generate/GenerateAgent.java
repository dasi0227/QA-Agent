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
import com.dasi.qa.agent.domain.agent.shared.enumeration.ErrorType;
import com.dasi.qa.agent.domain.agent.shared.sse.EventPublisher;
import com.dasi.qa.agent.domain.agent.shared.sse.SseEvent;
import com.dasi.qa.agent.domain.agent.shared.vo.UserProfileAllowVO;
import com.dasi.qa.agent.domain.agent.shared.vo.UserProfileInfoVO;
import com.dasi.qa.agent.domain.agent.shared.vo.UserProfileStyleVO;
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
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

@SuppressWarnings("unchecked")
@Service
@Slf4j
public class GenerateAgent implements IGenerateAgent {

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

        // 读取用户信息
        UserProfileInfoVO info = agentRepository.getUserProfileInfo(userId);
        UserProfileStyleVO style = agentRepository.getUserProfileStyle(userId);
        UserProfileAllowVO allow = agentRepository.getUserProfileAllow(userId);
        String userProfileJson = JSON.toJSONString(info);
        String answerStyle = style.getAnswerStyle();

        // 写入任务主记录，确保后续状态可追踪
        agentRepository.createGenerationTask(taskId, userId, request, allow);

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
            List<Object> writeTools = Boolean.TRUE.equals(allow.getAllowWebSearch())
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
                    .userProfileJson(userProfileJson)
                    .build();

            ValidateContext validateContext = ValidateContext.builder()
                    .taskId(taskId)
                    .request(request)
                    .allow(allow)
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
                    .userProfileJson(userProfileJson)
                    .build();

            WriteContext writeContext = WriteContext.builder()
                    .taskId(taskId)
                    .userId(userId)
                    .request(request)
                    .executor(applicationTaskExecutor)
                    .ragEvidenceProvider(ragEvidenceProvider)
                    .userProfileJson(userProfileJson)
                    .answerStyle(answerStyle)
                    .allow(allow)
                    .build();

            // 汇总 DAG 运行上下文，并传入各阶段执行回调
            GenerateContext generateContext = GenerateContext.builder()
                    .userModel(userModel)
                    .writeTools(writeTools)
                    .validateTools(validateTools)
                    .agentListener(agentListener)
                    .chatMemoryProvider(chatMemoryProvider)
                    .decideStep((scope, decideAgent) -> doDecide(scope, decideAgent, decideContext))
                    .abortStep((scope, abortAgent) -> doAbort(scope, abortAgent, abortContext))
                    .planStep((scope, planAgent) -> doPlan(scope, planAgent, planContext))
                    .writeStep((scope, draftAgent) -> doWrite(scope, draftAgent, writeContext))
                    .validateStep((scope, evaluateAgent, amendAgent) -> doValidate(scope, evaluateAgent, amendAgent, validateContext))
                    .summarizeStep((scope, summarizeAgent) -> doSummarize(scope, summarizeAgent, summarizeContext))
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

    /**
     * DecideAgent 判断用户需求是否符合 Generate 工作流，防止恶意/无关/错误需求进入 DAG
     */
    private void doDecide(AgenticScope scope, DecideAgent decideAgent, DecideContext context) {
        // 1. 更新状态
        agentRepository.updateTaskPhase(context.getTaskId(), GeneratePhase.DECIDE);

        // 2. 调用智能体
        try {
            decideAgent.decide(context.getTaskId(), context.getRequest().getUserPrompt());
        } catch (Exception exception) {
            log.warn("【GenerateAgent - DecideAgent】调用智能体出错: taskId={}, error={}", context.getTaskId(), exception.getMessage());
            scope.writeState("decideResult", new DecideResult(false, "DecideAgent 执行出错，默认判定为不可继续执行"));
        }
    }

    /**
     * AbortAgent 在 DecideAgent 判定为 INVALID 的时候，生成自然语言文本让用户理解非法原因
     */
    private void doAbort(AgenticScope scope, AbortAgent abortAgent, AbortContext context) {
        // 1. 更新状态
        agentRepository.updateTaskPhase(context.getTaskId(), GeneratePhase.ABORT);

        // 2. 拿到决策结果
        DecideResult decideResult = DecideResult.fromScope(scope);
        String reason = decideResult.getReason();

        // 3. 调用智能体
        String message;
        try {
            message = abortAgent.abort(context.getTaskId(), context.getRequest().getUserPrompt(), reason);
        } catch (Exception exception) {
            log.warn("【GenerateAgent - AbortAgent】调用智能体出错: taskId={}, error={}", context.getTaskId(), exception.getMessage());
            message = reason;
        }
        context.getEventPublisher().publishCanceled(ErrorType.CONTENT_FILTERED, message);
    }

    /**
     * PlanAgent 负责根据用户资料指定执行计划
     */
    private void doPlan(AgenticScope scope, PlanAgent planAgent, PlanContext context) {
        // 1. 更新状态
        agentRepository.updateTaskPhase(context.getTaskId(), GeneratePhase.PLAN);

        // 2. 拿到资料摘要
        String documentsSummary = agentRepository.getDocumentsSummary(
                context.getRequest().getDocumentIds(),
                context.getUserId()
        );

        // 3. 调用智能体
        PlanResult planResult;
        try {
            planResult = planAgent.plan(
                    context.getTaskId(),
                    documentsSummary,
                    context.getUserProfileJson(),
                    context.getRequest().getUserPrompt(),
                    context.getRequest().getRequestedQuestionCount()
            );
        } catch (Exception exception) {
            log.warn("【GenerateAgent - PlanAgent】调用智能体出错: taskId={}, error={}", context.getTaskId(), exception.getMessage());
            throw new GenerateException(ErrorType.fromException(exception), "PlanAgent 调用失败: " + exception.getMessage());
        }
        scope.writeState("planResult", planResult);
    }

    /**
     * WriteAgent 负责根据用户资料编写 QA
     */
    private void doWrite(AgenticScope scope, DraftAgent draftAgent, WriteContext context) {
        // 1. 更新状态
        agentRepository.updateTaskPhase(context.getTaskId(), GeneratePhase.WRITE);

        // 2. 拿到计划结果
        PlanResult planResult = readPlan(scope, context.getRequest());
        List<PlanItem> planItems;
        if (planResult == null || planResult.getPlanItems() == null || planResult.getPlanItems().isEmpty()) {
            planItems = fallbackPlan(context.getRequest()).getPlanItems();
        } else {
            planItems = planResult.getPlanItems().stream()
                    .filter(item -> StringUtils.hasText(item.getModuleTag()))
                    .filter(item -> item.getQuestionCount() > 0)
                    .toList();
        }

        // 3. 调用智能体
        List<DraftItem> allDrafts = Collections.synchronizedList(new ArrayList<>());
        List<String> failedModules = Collections.synchronizedList(new ArrayList<>());
        List<Object> moduleAgents = new ArrayList<>();
        for (PlanItem planItem : planItems) {
            moduleAgents.add(AgenticServices.agentAction(moduleScope -> {
                try {
                    List<SearchResult> evidence = context.getRagEvidenceProvider()
                            .search(context.getUserId(), context.getRequest().getDocumentIds(), planItem);
                    allDrafts.addAll(draftModule(draftAgent, DraftModuleContext.builder()
                            .taskId(context.getTaskId())
                            .request(context.getRequest())
                            .planItem(planItem)
                            .evidence(evidence)
                            .build(), context));
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
        scope.writeState("draftItem", deduplicated);
        if (!failedModules.isEmpty()) {
            scope.writeState("writeFailedModules", List.copyOf(failedModules));
        }
    }

    private List<DraftItem> draftModule(DraftAgent draftAgent, DraftModuleContext context,
                                        WriteContext writeContext) {
        int remaining = Math.max(0, context.getPlanItem().getQuestionCount());
        List<DraftItem> moduleDrafts = new ArrayList<>();
        while (remaining > 0) {
            int batchCount = Math.min(MAX_MODULE_QUESTIONS_PER_BATCH, remaining);
            List<DraftItem> batch = draftBatch(draftAgent, DraftBatchContext.builder()
                    .taskId(context.getTaskId())
                    .request(context.getRequest())
                    .planItem(context.getPlanItem())
                    .evidence(context.getEvidence())
                    .previousQuestions(previousQuestions(moduleDrafts))
                    .batchCount(batchCount)
                    .extraNote("")
                    .build(), writeContext);
            moduleDrafts.addAll(batch);
            remaining -= batchCount;
        }
        return moduleDrafts;
    }

    private List<DraftItem> draftBatch(DraftAgent draftAgent, DraftBatchContext context,
                                       WriteContext writeContext) {
        try {
            String response = draftAgent.draft(
                    context.getTaskId(),
                    context.getPlanItem().getModuleTag(),
                    JSON.toJSONString(context.getEvidence()),
                    writeContext.getUserProfileJson(),
                    context.getBatchCount(),
                    context.getPreviousQuestions(),
                    generationNote(context.getRequest(), context.getExtraNote(),
                            !Boolean.TRUE.equals(writeContext.getAllow().getAllowGeneralKnowledge()))
            );
            List<DraftItem> parsed = JSON.parseArray(extractJsonArray(response), DraftItem.class);
            return parsed == null ? List.of() : parsed;
        } catch (Exception exception) {
            log.warn("DraftAgent failed, fallback drafts used: module={}, message={}",
                    context.getPlanItem().getModuleTag(), exception.getMessage());
            return fallbackDrafts(new PlanItem(context.getPlanItem().getModuleTag(), context.getBatchCount(),
                    context.getPlanItem().getFocusTopics(),
                    context.getPlanItem().getSuggestedQuestionTypes()), context.getEvidence());
        }
    }

    private void doValidate(AgenticScope scope, EvaluateAgent evaluateAgent, AmendAgent amendAgent, ValidateContext context) {
        agentRepository.updateTaskPhase(context.getTaskId(), GeneratePhase.VALIDATE);
        List<DraftItem> drafts = readDrafts(scope);
        boolean strictEvidence = !Boolean.TRUE.equals(context.getAllow().getAllowGeneralKnowledge());
        ValidationCoordinator.ValidationOutcome outcome = new ValidationCoordinator(MAX_MODULE_QUESTIONS_PER_BATCH)
                .run(context.getTaskId(), context.getRequest(), evaluateAgent, amendAgent, drafts, strictEvidence);

        List<DraftItem> finalDrafts = cleanDrafts(deduplicate(outcome.passedDrafts()), strictEvidence);
        if (finalDrafts.isEmpty()) {
            throw new GenerateException(ErrorType.ALL_REJECTED, "Evaluator 未通过任何题目");
        }
        scope.writeState("validatedResult", finalDrafts);
    }

    private void doSummarize(AgenticScope scope, SummarizeAgent summarizeAgent, SummarizeContext context) {
        agentRepository.updateTaskPhase(context.getTaskId(), GeneratePhase.SUMMARIZE);
        PlanResult planResult = readPlan(scope, context.getRequest());
        List<DraftItem> validatedResult = readValidatedResult(scope);
        List<String> failedModules = readStrings(scope, "writeFailedModules");

        String qaSetId = agentRepository.saveGeneratedQaSet(
                context.getTaskId(),
                context.getUserId(),
                context.getRequest(),
                planResult,
                validatedResult
        );

        int requiredCount = context.getRequest().getRequestedQuestionCount();
        int generatedCount = validatedResult.size();
        String modules = planModulesText(planResult);
        String tags = tagsText(validatedResult);

        String summaryMessage;
        try {
            summaryMessage = summarizeAgent.summarize(
                    context.getTaskId(),
                    context.getRequest().getUserPrompt(),
                    context.getUserProfileJson(),
                    title(context.getRequest()),
                    planResult.getDescription() != null ? planResult.getDescription() : "",
                    requiredCount,
                    generatedCount,
                    modules,
                    tags,
                    JSON.toJSONString(validatedResult)
            );
        } catch (Exception exception) {
            log.warn("SummarizeAgent failed, fallback summary used: message={}", exception.getMessage());
            summaryMessage = fallbackSummaryMessage(planResult, context.getRequest(), validatedResult,
                    requiredCount, generatedCount, failedModules);
        }
        if (!hasText(summaryMessage)) {
            summaryMessage = fallbackSummaryMessage(planResult, context.getRequest(), validatedResult,
                    requiredCount, generatedCount, failedModules);
        }
        agentRepository.markTaskCompleted(context.getTaskId(), qaSetId);
        scope.writeState("qaSetId", qaSetId);
        context.getEventPublisher().publishEvent(GeneratePhase.COMPLETE, GenerateStatus.SOLVED, summaryMessage, 0);
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

    private List<DraftItem> cleanDrafts(List<DraftItem> drafts, boolean strictEvidence) {
        return drafts.stream()
                .filter(item -> item != null)
                .filter(item -> hasText(item.getQuestion()) && hasText(item.getAnswer()))
                .map(item -> sanitizeDraftItem(item, strictEvidence))
                .toList();
    }

    private DraftItem sanitizeDraftItem(DraftItem item, boolean strictEvidence) {
        String conflictTip = safe(item.getConflictTip());
        String evidence = safe(item.getEvidence());
        if (strictEvidence && !hasText(evidence)) {
            conflictTip = conflictTip.isBlank() ? "资料证据不足，答案仅保留为低置信度基础题" : conflictTip;
        }
        return new DraftItem(
                item.getQuestion().trim(),
                item.getAnswer().trim(),
                safe(item.getKnowledgeNote()).trim(),
                hasText(item.getTag()) ? item.getTag().trim() : "General",
                hasText(item.getDifficulty()) ? item.getDifficulty().trim() : "MEDIUM",
                conflictTip,
                evidence
        );
    }

    private List<DraftItem> fallbackDrafts(PlanItem planItem, List<SearchResult> evidence) {
        String evidenceText = evidence.isEmpty() ? "" : evidence.get(0).getContent();
        int count = Math.max(1, planItem.getQuestionCount());
        List<DraftItem> drafts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            drafts.add(new DraftItem(
                    planItem.getModuleTag() + " 的核心问题 " + (i + 1),
                    evidenceText,
                    evidenceText,
                    planItem.getModuleTag(),
                    "MEDIUM",
                    evidence.isEmpty() ? "资料证据不足" : "",
                    evidenceText
            ));
        }
        return drafts;
    }

    private PlanResult fallbackPlan(CreateQaSetRequest request) {
        int count = request.getRequestedQuestionCount();
        return new PlanResult(
                title(request),
                "根据用户资料生成的技术面试问答集",
                List.of(new PlanItem("General", count, "核心知识点", "概念题, 场景题"))
        );
    }

    private List<DraftItem> readDrafts(AgenticScope scope) {
        Object value = scope.readState("draftItem");
        return value instanceof List<?> list ? (List<DraftItem>) list : List.of();
    }

    private List<DraftItem> readValidatedResult(AgenticScope scope) {
        Object value = scope.readState("validatedResult");
        return value instanceof List<?> list ? (List<DraftItem>) list : List.of();
    }

    private PlanResult readPlan(AgenticScope scope, CreateQaSetRequest request) {
        Object value = scope.readState("planResult");
        return value instanceof PlanResult planResult ? planResult : fallbackPlan(request);
    }

    private String previousQuestions(List<DraftItem> previous) {
        return JSON.toJSONString(previous.stream()
                .filter(item -> item != null && item.getQuestion() != null)
                .map(DraftItem::getQuestion)
                .toList());
    }

    private String generationNote(CreateQaSetRequest request, String extraNote, boolean strictEvidence) {
        String note = safe(request.getUserPrompt());
        if (strictEvidence) {
            note += "\n禁止使用资料外事实；证据不足时写 conflictTip。";
        }
        if (extraNote != null && !extraNote.isBlank()) {
            note += "\n" + extraNote;
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

    private String title(CreateQaSetRequest request) {
        return request.getTitle() == null || request.getTitle().isBlank() ? "生成问答集" : request.getTitle();
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

    private String planModulesText(PlanResult planResult) {
        if (planResult == null || planResult.getPlanItems() == null) {
            return "";
        }
        return planResult.getPlanItems().stream()
                .map(item -> item.getModuleTag() == null ? "" : item.getModuleTag())
                .filter(tag -> !tag.isBlank())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private String tagsText(List<DraftItem> draftItems) {
        if (draftItems == null || draftItems.isEmpty()) {
            return "";
        }
        return draftItems.stream()
                .map(DraftItem::getTag)
                .filter(tag -> tag != null && !tag.isBlank())
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
    }

    private String fallbackSummaryMessage(PlanResult planResult, CreateQaSetRequest request, List<DraftItem> draftItems,
                                          int requiredCount, int generatedCount, List<String> failedModules) {
        return "问答集已生成，请求 " + requiredCount + " 题，实际通过 " + generatedCount + " 题。"
                + "模块：" + planModulesText(planResult)
                + "。标签：" + tagsText(draftItems)
                + "。Write 失败模块 " + (failedModules == null ? 0 : failedModules.size()) + " 个。";
    }

}
