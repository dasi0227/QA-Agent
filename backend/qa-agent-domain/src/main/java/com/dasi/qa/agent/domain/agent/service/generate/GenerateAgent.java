package com.dasi.qa.agent.domain.agent.service.generate;

import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.generate.model.context.*;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GeneratePhase;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GenerateStatus;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.VerdictType;
import com.dasi.qa.agent.domain.agent.service.generate.model.exception.GenerateException;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.*;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.*;
import com.dasi.qa.agent.domain.agent.service.generate.support.GenerateAgentFactory;
import com.dasi.qa.agent.domain.agent.service.generate.support.GenerateSupervisor;
import com.dasi.qa.agent.domain.agent.service.generate.support.RagEvidenceProvider;
import com.dasi.qa.agent.domain.agent.service.generate.support.UserLlmModelProvider;
import com.dasi.qa.agent.domain.agent.service.generate.tool.RagSearchTool;
import com.dasi.qa.agent.domain.agent.service.generate.tool.WebSearchTool;
import com.dasi.qa.agent.domain.agent.shared.enumeration.ErrorType;
import com.dasi.qa.agent.domain.agent.shared.sse.EventPublisher;
import com.dasi.qa.agent.domain.agent.shared.sse.SseEvent;
import com.dasi.qa.agent.domain.agent.shared.vo.UserProfileAllowVO;
import com.dasi.qa.agent.domain.agent.shared.vo.UserProfileInfoVO;
import com.dasi.qa.agent.domain.agent.shared.vo.UserProfileStyleVO;
import com.dasi.qa.agent.domain.document.service.rag.search.ISearchService;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import com.dasi.qa.agent.domain.util.IPromptUtil;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
import com.dasi.qa.agent.types.dto.response.document.SearchResult;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Service
@Slf4j
public class GenerateAgent implements IGenerateAgent {

    private static final int BATCH_SIZE = 10;
    private static final int MAX_RETRY = 2;

    private final IJsonUtil jsonUtil;
    private final IPromptUtil promptUtil;
    private final IAgentRepository agentRepository;
    private final GenerateAgentFactory generateAgentFactory;
    private final ISearchService searchService;
    private final RagEvidenceProvider ragEvidenceProvider;
    private final UserLlmModelProvider userLlmModelProvider;
    private final ChatModel webSearchModel;
    private final ChatModel supervisorChatModel;
    private final ThreadPoolTaskExecutor applicationTaskExecutor;

    public GenerateAgent(IJsonUtil jsonUtil,
                         IPromptUtil promptUtil,
                         IAgentRepository agentRepository,
                         UserLlmModelProvider userLlmModelProvider,
                         GenerateAgentFactory generateAgentFactory,
                         ISearchService searchService,
                         RagEvidenceProvider ragEvidenceProvider,
                         @Qualifier("webSearchModel") ChatModel webSearchModel,
                         @Qualifier("supervisorModel") ChatModel supervisorModel,
                         @Qualifier("applicationTaskExecutor") ThreadPoolTaskExecutor applicationTaskExecutor) {
        this.jsonUtil = jsonUtil;
        this.promptUtil = promptUtil;
        this.agentRepository = agentRepository;
        this.userLlmModelProvider = userLlmModelProvider;
        this.generateAgentFactory = generateAgentFactory;
        this.searchService = searchService;
        this.ragEvidenceProvider = ragEvidenceProvider;
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
        String userProfileJson = jsonUtil.toJsonString(info);
        String answerStyle = style.getAnswerStyle();
        // 写入任务主记录，确保后续状态可追踪
        agentRepository.createGenerationTask(taskId, userId, request, allow);

        // 跨阶段累计 token，用于并发场景下的线程安全统计
        AtomicInteger totalTokens = new AtomicInteger(0);

        // 创建事件发布器
        EventPublisher eventPublisher = new EventPublisher(agentRepository, taskId, userId, sseEventHandler, totalTokens);

        // 发送任务创建事件
        eventPublisher.publishEvent(GeneratePhase.INIT, GenerateStatus.PROCESSING, "生成任务已创建", 0);

        try {
            // 读取并构建用户专属模型
            ChatModelListener tokenListener = new ChatModelListener() {
                @Override
                public void onResponse(ChatModelResponseContext ctx) {
                    TokenUsage usage = ctx.chatResponse().tokenUsage();
                    if (usage != null && usage.totalTokenCount() != null) {
                        totalTokens.addAndGet(usage.totalTokenCount());
                    }
                }
            };
            ChatModel userModel = userLlmModelProvider.getUserLlmModel(userId, tokenListener);

            // 准备可调用工具
            RagSearchTool ragSearchTool = new RagSearchTool(searchService, userId, request.getDocumentIds());
            WebSearchTool webSearchTool = new WebSearchTool(webSearchModel, promptUtil);
            List<Object> writeTools = Boolean.TRUE.equals(allow.getAllowWebSearch())
                    ? List.of(ragSearchTool, webSearchTool)
                    : List.of(ragSearchTool);
            List<Object> validateTools = List.of(ragSearchTool);

            // 创建阶段总结器，负责在每个 Agent 调用成功后生成进度消息并推送 SSE
            GenerateSupervisor supervisor = new GenerateSupervisor(taskId, promptUtil, supervisorChatModel, eventPublisher, totalTokens);

            // 组装各阶段执行上下文
            PlanContext planContext = PlanContext.builder()
                    .taskId(taskId)
                    .userId(userId)
                    .request(request)
                    .userProfileJson(userProfileJson)
                    .allow(allow)
                    .supervisor(supervisor)
                    .build();

            ValidateContext validateContext = ValidateContext.builder()
                    .taskId(taskId)
                    .request(request)
                    .answerStyle(answerStyle)
                    .supervisor(supervisor)
                    .build();

            DecideContext decideContext = DecideContext.builder()
                    .taskId(taskId)
                    .request(request)
                    .supervisor(supervisor)
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
                    .executor(java.util.concurrent.Executors.newFixedThreadPool(3))
                    .ragEvidenceProvider(ragEvidenceProvider)
                    .userProfileJson(userProfileJson)
                    .answerStyle(answerStyle)
                    .supervisor(supervisor)
                    .build();

            // 汇总 DAG 运行上下文，并传入各阶段执行回调
            GenerateContext generateContext = GenerateContext.builder()
                    .userModel(userModel)
                    .writeTools(writeTools)
                    .validateTools(validateTools)
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
                    "userPrompt", request.getUserPrompt()
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
     * DecideAgent 负责判断用户需求是否符合问答集生成场景，输出 valid 判定结果写入 scope。
     */
    private void doDecide(AgenticScope scope, DecideAgent decideAgent, DecideContext decideContext) {
        // 1. 更新状态
        agentRepository.updateTaskPhase(decideContext.getTaskId(), GeneratePhase.DECIDE);

        // 2. 调用智能体
        String retryHint = "";
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                DecideResult result = decideAgent.decide(decideContext.getTaskId(), decideContext.getRequest().getUserPrompt(), retryHint);
                writeDecideResult(scope, result);
                decideContext.getSupervisor().doSupervise(GeneratePhase.DECIDE, jsonUtil.toJsonString(result));
                break;
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                if (attempt == MAX_RETRY) {
                    log.warn("【GenerateAgent - DecideAgent】重试{}次后仍失败: taskId={}, error={}", MAX_RETRY, decideContext.getTaskId(), exception.getMessage());
                    writeDecideResult(scope, null);
                } else {
                    log.warn("【GenerateAgent - DecideAgent】第{}次失败，重试中: taskId={}, error={}", attempt + 1, decideContext.getTaskId(), exception.getMessage());
                }
            }
        }
    }

    /**
     * AbortAgent 负责根据判定原因生成用户可读的终止说明，并以 CANCELED 状态发布失败事件。
     */
    private void doAbort(AgenticScope scope, AbortAgent abortAgent, AbortContext abortContext) {
        // 1. 更新状态
        agentRepository.updateTaskPhase(abortContext.getTaskId(), GeneratePhase.ABORT);

        // 2. 拿到决策结果
        DecideResult decideResult = readDecideResult(scope);
        String reason = decideResult.getReason();

        // 3. 调用智能体
        String message;
        try {
            message = abortAgent.abort(abortContext.getTaskId(), abortContext.getRequest().getUserPrompt(), reason);
        } catch (Exception exception) {
            log.warn("【GenerateAgent - AbortAgent】调用智能体出错: taskId={}, error={}", abortContext.getTaskId(), exception.getMessage());
            message = reason;
        }

        // 4. 发送中断信息
        abortContext.getEventPublisher().publishCanceled(ErrorType.CONTENT_FILTERED, message);
    }

    /**
     * PlanAgent 负责分析资料目录结构并规划模块化题集方案，输出 planResult 写入 scope。
     */
    private void doPlan(AgenticScope scope, PlanAgent planAgent, PlanContext planContext) {
        // 1. 更新状态
        agentRepository.updateTaskPhase(planContext.getTaskId(), GeneratePhase.PLAN);

        // 2. 拿到资料摘要
        String documentsSummary = agentRepository.getDocumentsSummary(
                planContext.getRequest().getDocumentIds(),
                planContext.getUserId()
        );

        // 3. 调用智能体
        PlanResult planResult = null;
        String retryHint = "";
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                planResult = planAgent.plan(
                        planContext.getTaskId(),
                        documentsSummary,
                        planContext.getUserProfileJson(),
                        planContext.getRequest().getUserPrompt(),
                        planContext.getRequest().getRequestedQuestionCount(),
                        retryHint
                );
                break;
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                if (attempt == MAX_RETRY) {
                    log.warn("【GenerateAgent - PlanAgent】重试{}次后仍失败: taskId={}, error={}", MAX_RETRY, planContext.getTaskId(), exception.getMessage());
                } else {
                    log.warn("【GenerateAgent - PlanAgent】第{}次失败，重试中: taskId={}, error={}", attempt + 1, planContext.getTaskId(), exception.getMessage());
                }
            }
        }
        if (planResult == null) {
            if (Boolean.TRUE.equals(planContext.getAllow().getAllowFallback())) {
                log.warn("【GenerateAgent - PlanAgent】启用默认 Plan: taskId={}", planContext.getTaskId());
                planResult = fallbackPlan(planContext.getRequest());
            } else {
                throw new GenerateException(ErrorType.UNKNOWN, "PlanAgent 调用失败，已重试 " + MAX_RETRY + " 次");
            }
        }

        // 4. 写入共享领域
        writePlanResult(scope, planResult, planContext.getRequest());
        planContext.getSupervisor().doSupervise(GeneratePhase.PLAN, jsonUtil.toJsonString(planResult));
    }

    /**
     * WriteAgent 负责按模块并行执行 RAG 检索与 DraftAgent 出题，输出 draftResult 写入 scope。
     */
    private void doWrite(AgenticScope scope, DraftAgent draftAgent, WriteContext writeContext) {
        // 1. 更新状态
        agentRepository.updateTaskPhase(writeContext.getTaskId(), GeneratePhase.WRITE);

        // 2. 拿到计划结果
        PlanResult planResult = readPlanResult(scope);
        List<PlanItem> planItems = planResult.getPlanItems();

        // 3. 解析计划，根据模块划分
        List<DraftItem> draftItems = Collections.synchronizedList(new ArrayList<>());
        List<Object> moduleAgents = new ArrayList<>();
        agentRepository.updateTaskPhase(writeContext.getTaskId(), GeneratePhase.DRAFT);
        for (PlanItem planItem : planItems) {
            // 4. 创建每个模块的 DraftAgent
            AgenticServices.AgenticScopeAction agentAction = AgenticServices.agentAction(moduleScope -> {
                try {
                    // 4.1 拿到 RAG 资料
                    List<SearchResult> evidence = writeContext.getRagEvidenceProvider().search(writeContext.getUserId(), writeContext.getRequest().getDocumentIds(), planItem);
                    // 4.2 构建草稿上下文
                    DraftContext draftContext = DraftContext.builder()
                            .taskId(writeContext.getTaskId())
                            .request(writeContext.getRequest())
                            .planItem(planItem)
                            .evidence(jsonUtil.toJsonString(evidence))
                            .userProfileJson(writeContext.getUserProfileJson())
                            .answerStyle(writeContext.getAnswerStyle())
                            .supervisor(writeContext.getSupervisor())
                            .build();
                    // 4.3 汇总每个模块单独出的题
                    draftItems.addAll(doDraft(draftAgent, draftContext));
                } catch (Exception exception) {
                    log.warn("【GenerateAgent - DraftAgent】调用智能体出错: taskId={}, module={}, error={}", writeContext.getTaskId(), planItem.getModuleTag(), exception.getMessage());
                }
            });
            // 5. 汇总 DraftAgent
            moduleAgents.add(agentAction);
        }

        // 6. 组装为并发工作流
        UntypedAgent writer = AgenticServices.parallelBuilder()
                .name(GeneratePhase.WRITE.getAgentName())
                .description(GeneratePhase.WRITE.getAgentDesc())
                .executor(writeContext.getExecutor())
                .subAgents(moduleAgents)
                .output(moduleScope -> draftItems)
                .build();

        // 7. 调用智能体
        try {
            writer.invoke(Map.of());
        } catch (Exception exception) {
            log.warn("【GenerateAgent - WriteAgent】调用智能体出错: taskId={}, error={}", writeContext.getTaskId(), exception.getMessage());
            writeDraftResult(scope, draftItems);
            throw new GenerateException(ErrorType.fromException(exception), "WriteAgent 调用失败: " + exception.getMessage());
        }

        // 8. 写入共享领域
        writeDraftResult(scope, draftItems);
    }

    /**
     * DraftAgent 负责按模块证据分批起草结构化问答题目。
     */
    private List<DraftItem> doDraft(DraftAgent draftAgent, DraftContext draftContext) {
        // 当前模块题数
        int remaining = draftContext.getPlanItem().getQuestionCount();
        List<DraftItem> draftItems = new ArrayList<>();
        while (remaining > 0) {
            // 当前批次题数
            int batchCount = Math.min(BATCH_SIZE, remaining);
            String previousQuestions = jsonUtil.toJsonString(draftItems.stream()
                    .filter(item -> item != null && item.getQuestion() != null)
                    .map(DraftItem::getQuestion)
                    .toList());
            List<DraftItem> batchItems = null;
            String retryHint = "";
            for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
                try {
                    String response = draftAgent.draft(
                            draftContext.getTaskId(),
                            draftContext.getPlanItem().getModuleTag(),
                            draftContext.getEvidence(),
                            draftContext.getUserProfileJson(),
                            batchCount,
                            previousQuestions,
                            draftContext.getRequest().getUserPrompt(),
                            draftContext.getAnswerStyle(),
                            retryHint
                    );
                    batchItems = jsonUtil.parseJsonArray(response, DraftItem.class);
                    draftContext.getSupervisor().doSupervise(GeneratePhase.DRAFT, response);
                    break;
                } catch (Exception exception) {
                    retryHint = exception.getMessage();
                    if (attempt == MAX_RETRY) {
                        log.warn("【GenerateAgent - DraftAgent】批次重试{}次后仍失败: taskId={}, module={}, error={}", MAX_RETRY, draftContext.getTaskId(), draftContext.getPlanItem().getModuleTag(), exception.getMessage());
                        batchItems = fallbackDraft(draftContext.getPlanItem(), draftContext.getEvidence());
                    } else {
                        log.warn("【GenerateAgent - DraftAgent】第{}次失败，重试中: taskId={}, module={}, error={}", attempt + 1, draftContext.getTaskId(), draftContext.getPlanItem().getModuleTag(), exception.getMessage());
                    }
                }
            }
            draftItems.addAll(batchItems);
            remaining -= batchCount;
        }
        return draftItems;
    }

    /**
     * ValidateAgent 负责审校 draftResult 中的题目并修订可修复项，输出 validatedResult 写入 scope。
     */
    private void doValidate(AgenticScope scope, EvaluateAgent evaluateAgent, AmendAgent amendAgent, ValidateContext validateContext) {
        // 1. 更新状态
        agentRepository.updateTaskPhase(validateContext.getTaskId(), GeneratePhase.VALIDATE);

        // 2. 初次生成的问答集合
        List<DraftItem> draftItems = readDraftResult(scope);

        // 3. 获取不同批次集合
        List<List<DraftItem>> batchList = new ArrayList<>();
        for (int i = 0; i < draftItems.size(); i += BATCH_SIZE) {
            batchList.add(draftItems.subList(i, Math.min(i + BATCH_SIZE, draftItems.size())));
        }

        // 4. 创建每个批次的异步任务
        List<CompletableFuture<List<DraftItem>>> futureList = batchList.stream()
                .map(batch -> CompletableFuture
                        .supplyAsync(() -> doValidateLoop(
                                validateContext.getTaskId(),
                                evaluateAgent, amendAgent,
                                ValidateLoopContext.builder()
                                        .batch(batch)
                                        .userPrompt(validateContext.getRequest().getUserPrompt())
                                        .answerStyle(validateContext.getAnswerStyle())
                                        .supervisor(validateContext.getSupervisor())
                                        .build()),
                                applicationTaskExecutor)
                        .exceptionally(ex -> {
                            log.warn("【GenerateAgent - ValidateAgent】批次执行异常，退回原始题目: taskId={}, error={}", validateContext.getTaskId(), ex.getMessage());
                            return batch;
                        }))
                .toList();

        // 5. 等待所有批次的异步任务执行完成并汇总结果
        List<DraftItem> validatedItems = futureList.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList();

        // 6. 写入共享领域
        writeValidateResult(scope, validatedItems);
    }

    private List<DraftItem> doValidateLoop(String taskId, EvaluateAgent evaluateAgent, AmendAgent amendAgent, ValidateLoopContext loopContext) {
        List<DraftItem> passItems = new ArrayList<>();
        AtomicReference<List<DraftItem>> evaluateItems = new AtomicReference<>(loopContext.getBatch());
        AtomicReference<List<AmendItem>> amendItems = new AtomicReference<>(List.of());
        AtomicBoolean flag = new AtomicBoolean(false);

        UntypedAgent validateAgent = AgenticServices.loopBuilder()
                .name(GeneratePhase.VALIDATE.getAgentName())
                .description(GeneratePhase.VALIDATE.getAgentDesc())
                .maxIterations(2)
                .exitCondition((scope, iteration) -> flag.get())
                .subAgents(
                        // 校验
                        AgenticServices.agentAction(scope -> {
                            List<EvaluateItem> evaluates = doEvaluate(taskId,
                                    evaluateAgent,
                                    EvaluateContext.builder()
                                            .drafts(evaluateItems.get())
                                            .supervisor(loopContext.getSupervisor())
                                            .build()
                            );

                            List<AmendItem> amends = new ArrayList<>();
                            for (int i = 0; i < Math.min(evaluateItems.get().size(), evaluates.size()); i++) {
                                if (VerdictType.PASS.name().equals(evaluates.get(i).getVerdict())) {
                                    passItems.add(evaluateItems.get().get(i));
                                } else {
                                    AmendItem amendItem = AmendItem.builder()
                                            .draftItem(evaluateItems.get().get(i))
                                            .reason(evaluates.get(i).getReason())
                                            .suggestion(evaluates.get(i).getSuggestion())
                                            .build();
                                    amends.add(amendItem);
                                }
                            }

                            amendItems.set(amends);
                            flag.set(amends.isEmpty());
                        }),

                        // 修改
                        AgenticServices.agentAction(scope -> {
                            if (amendItems.get().isEmpty()) {
                                return;
                            }
                            List<DraftItem> amended = doAmend(taskId, amendAgent,
                                    AmendContext.builder()
                                            .items(amendItems.get())
                                            .userPrompt(loopContext.getUserPrompt())
                                            .answerStyle(loopContext.getAnswerStyle())
                                            .supervisor(loopContext.getSupervisor())
                                            .build());
                            evaluateItems.set(amended);
                        })
                )
                .output(scope -> evaluateItems.get())
                .build();

        try {
            validateAgent.invoke(Map.of());
        } catch (Exception exception) {
            log.warn("【GenerateAgent - ValidateAgent】调用智能体出错: taskId={}, error={}", taskId, exception.getMessage());
            if (passItems.isEmpty()) {
                passItems.addAll(loopContext.getBatch());
            }
        }

        return passItems;
    }

    private List<EvaluateItem> doEvaluate(String taskId, EvaluateAgent evaluateAgent, EvaluateContext evaluateContext) {
        String retryHint = "";
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                String response = evaluateAgent.evaluate(taskId, jsonUtil.toJsonString(evaluateContext.getDrafts()), retryHint);
                List<EvaluateItem> results = jsonUtil.parseJsonArray(response, EvaluateItem.class);
                evaluateContext.getSupervisor().doSupervise(GeneratePhase.EVALUATE, response);
                return results;
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                if (attempt == MAX_RETRY) {
                    log.warn("【GenerateAgent - EvaluateAgent】重试{}次后仍失败: taskId={}, error={}", MAX_RETRY, taskId, exception.getMessage());
                    return fallbackEvaluate(evaluateContext.getDrafts());
                }
                log.warn("【GenerateAgent - EvaluateAgent】第{}次失败，重试中: taskId={}, error={}", attempt + 1, taskId, exception.getMessage());
            }
        }
        return fallbackEvaluate(evaluateContext.getDrafts());
    }

    private List<DraftItem> doAmend(String taskId, AmendAgent amendAgent, AmendContext amendContext) {
        String retryHint = "";
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                String response = amendAgent.amend(taskId, jsonUtil.toJsonString(amendContext.getItems()), amendContext.getUserPrompt(), amendContext.getAnswerStyle(), retryHint);
                List<DraftItem> results = jsonUtil.parseJsonArray(response, DraftItem.class);
                amendContext.getSupervisor().doSupervise(GeneratePhase.AMEND, response);
                return results;
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                if (attempt == MAX_RETRY) {
                    log.warn("【GenerateAgent - AmendAgent】重试{}次后仍失败: taskId={}, error={}", MAX_RETRY, taskId, exception.getMessage());
                    return fallbackAmend(amendContext.getItems());
                }
                log.warn("【GenerateAgent - AmendAgent】第{}次失败，重试中: taskId={}, error={}", attempt + 1, taskId, exception.getMessage());
            }
        }
        return fallbackAmend(amendContext.getItems());
    }

    /**
     * SummarizeAgent 负责落库最终问答集并生成完成说明。
     */
    private void doSummarize(AgenticScope scope, SummarizeAgent summarizeAgent, SummarizeContext summarizeContext) {
        // 1. 更新状态
        agentRepository.updateTaskPhase(summarizeContext.getTaskId(), GeneratePhase.SUMMARIZE);

        // 2. 计划结果
        PlanResult planResult = readPlanResult(scope);

        // 3. 生成结果
        List<DraftItem> validatedResult = readValidateResult(scope);

        // 4. 保存 QA Set
        String qaSetId = agentRepository.saveGeneratedQaSet(
                summarizeContext.getTaskId(),
                summarizeContext.getUserId(),
                summarizeContext.getRequest(),
                planResult,
                validatedResult
        );

        // 5. 解析结果
        int requiredCount = summarizeContext.getRequest().getRequestedQuestionCount();
        int generatedCount = validatedResult.size();
        String modules = planResult.getPlanItems().stream()
                .map(item -> item.getModuleTag() == null ? "" : item.getModuleTag())
                .filter(tag -> !tag.isBlank())
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        String tags = validatedResult.stream()
                .map(DraftItem::getTag)
                .filter(tag -> tag != null && !tag.isBlank())
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        // 6. 调用智能体
        String summaryMessage;
        try {
            summaryMessage = summarizeAgent.summarize(
                    summarizeContext.getTaskId(),
                    summarizeContext.getRequest().getUserPrompt(),
                    summarizeContext.getUserProfileJson(),
                    summarizeContext.getRequest().getTitle(),
                    planResult.getDescription() != null ? planResult.getDescription() : "",
                    requiredCount,
                    generatedCount,
                    modules,
                    tags,
                    jsonUtil.toJsonString(validatedResult)
            );
        } catch (Exception exception) {
            log.warn("【GenerateAgent - SummarizeAgent】调用智能体出错: taskId={}, error={}", summarizeContext.getTaskId(), exception.getMessage());
            summaryMessage = fallbackSummarize(requiredCount, generatedCount, modules, tags);
        }

        // 7. 标记任务完成
        agentRepository.markTaskCompleted(summarizeContext.getTaskId(), qaSetId);
        summarizeContext.getEventPublisher().publishEvent(GeneratePhase.COMPLETE, GenerateStatus.SOLVED, summaryMessage, 0);
    }

    private DecideResult fallbackDecide() {
        return new DecideResult(false, "DecideAgent 执行出错，默认判定为不可继续执行");
    }

    private PlanResult fallbackPlan(CreateQaSetRequest request) {
        return new PlanResult(
                request.getTitle(),
                "根据用户资料生成的技术面试问答集",
                List.of(new PlanItem("General", request.getRequestedQuestionCount(),
                        "核心知识点", "概念题, 场景题"))
        );
    }

    private List<DraftItem> fallbackDraft(PlanItem planItem, String evidence) {
        int count = Math.max(1, planItem.getQuestionCount());
        List<DraftItem> drafts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            drafts.add(new DraftItem(
                    planItem.getModuleTag() + " 的核心问题 " + (i + 1),
                    evidence,
                    evidence,
                    planItem.getModuleTag(),
                    "MEDIUM",
                    evidence.isEmpty() ? "资料证据不足" : "",
                    evidence
            ));
        }
        return drafts;
    }

    private List<DraftItem> fallbackAmend(List<AmendItem> items) {
        List<DraftItem> results = new ArrayList<>();
        for (AmendItem item : items) {
            results.add(item.getDraftItem());
        }
        return results;
    }

    private List<EvaluateItem> fallbackEvaluate(List<DraftItem> drafts) {
        List<EvaluateItem> results = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            results.add(new EvaluateItem(VerdictType.PASS.name(), "fallback pass", ""));
        }
        return results;
    }

    private String fallbackSummarize(int requiredCount, int generatedCount, String modules, String tags) {
        return "问答集已生成，请求 " + requiredCount + " 题，实际通过 " + generatedCount + " 题。模块：" + modules + "。标签：" + tags + "。";
    }

    private DecideResult readDecideResult(AgenticScope scope) {
        return (DecideResult) scope.readState(GeneratePhase.DECIDE.getScopeKey());
    }

    private PlanResult readPlanResult(AgenticScope scope) {
        return (PlanResult) scope.readState(GeneratePhase.PLAN.getScopeKey());
    }

    @SuppressWarnings("unchecked")
    private List<DraftItem> readValidateResult(AgenticScope scope) {
        return (List<DraftItem>) scope.readState(GeneratePhase.VALIDATE.getScopeKey());
    }

    @SuppressWarnings("unchecked")
    private List<DraftItem> readDraftResult(AgenticScope scope) {
        return (List<DraftItem>) scope.readState(GeneratePhase.WRITE.getScopeKey());
    }

    private void writeDecideResult(AgenticScope scope, DecideResult result) {
        scope.writeState(GeneratePhase.DECIDE.getScopeKey(), result != null ? result : fallbackDecide());
    }

    private void writePlanResult(AgenticScope scope, PlanResult result, CreateQaSetRequest request) {
        scope.writeState(GeneratePhase.PLAN.getScopeKey(), result != null ? result : fallbackPlan(request));
    }

    private void writeDraftResult(AgenticScope scope, List<DraftItem> result) {
        scope.writeState(GeneratePhase.WRITE.getScopeKey(), result != null ? result : List.of());
    }

    private void writeValidateResult(AgenticScope scope, List<DraftItem> result) {
        scope.writeState(GeneratePhase.VALIDATE.getScopeKey(), result != null ? result : List.of());
    }

}
