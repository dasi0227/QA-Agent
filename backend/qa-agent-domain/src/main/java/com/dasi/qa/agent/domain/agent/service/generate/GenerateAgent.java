package com.dasi.qa.agent.domain.agent.service.generate;

import com.dasi.qa.agent.domain.agent.model.vo.UserProfileAllowVO;
import com.dasi.qa.agent.domain.agent.model.vo.UserProfileInfoVO;
import com.dasi.qa.agent.domain.agent.model.vo.UserProfileStyleVO;
import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.generate.model.context.*;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GeneratePhase;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.GenerateStatus;
import com.dasi.qa.agent.domain.agent.service.generate.model.enumeration.VerdictType;
import com.dasi.qa.agent.domain.agent.service.generate.model.exception.GenerateException;
import com.dasi.qa.agent.domain.agent.service.generate.model.result.*;
import com.dasi.qa.agent.domain.agent.service.generate.subagent.*;
import com.dasi.qa.agent.domain.agent.service.generate.support.GenerateAgentFactory;
import com.dasi.qa.agent.domain.agent.service.generate.support.GenerateSaver;
import com.dasi.qa.agent.domain.agent.service.generate.support.GenerateSupervisor;
import com.dasi.qa.agent.domain.agent.service.shared.RagEvidenceProvider;
import com.dasi.qa.agent.domain.agent.service.shared.WebEvidenceProvider;
import com.dasi.qa.agent.domain.agent.service.shared.EventPublisher;
import com.dasi.qa.agent.domain.agent.service.shared.SseEvent;
import com.dasi.qa.agent.domain.agent.service.shared.UserLlmModelProvider;
import com.dasi.qa.agent.domain.agent.service.shared.UserMemoryProvider;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import com.dasi.qa.agent.domain.util.IPromptUtil;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
import com.dasi.qa.agent.types.enumeration.AgentErrorType;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.output.TokenUsage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.concurrent.CompletableFuture;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

@Service
@Slf4j
@SuppressWarnings("unchecked")
public class GenerateAgent implements IGenerateAgent {

    private static final int BATCH_SIZE = 10;
    private static final int MAX_RETRY = 2;
    private static final int GROUP_SIZE = 3;
    private static final int MAX_SOURCE_CHUNK_COUNT = 5;
    private static final int FALLBACK_SOURCE_CHUNK_COUNT = 2;


    private final IJsonUtil jsonUtil;
    private final IPromptUtil promptUtil;
    private final IAgentRepository agentRepository;
    private final GenerateAgentFactory generateAgentFactory;
    private final RagEvidenceProvider ragEvidenceProvider;
    private final WebEvidenceProvider webEvidenceProvider;
    private final UserLlmModelProvider userLlmModelProvider;
    private final UserMemoryProvider userMemoryProvider;
    private final ChatModel supervisorChatModel;
    private final ThreadPoolTaskExecutor applicationTaskExecutor;
    private final GenerateSaver generateSaver;

    public GenerateAgent(IJsonUtil jsonUtil,
                         IPromptUtil promptUtil,
                         IAgentRepository agentRepository,
                         UserLlmModelProvider userLlmModelProvider,
                         UserMemoryProvider userMemoryProvider,
                         GenerateAgentFactory generateAgentFactory,
                         RagEvidenceProvider ragEvidenceProvider,
                         WebEvidenceProvider webEvidenceProvider,
                         @Qualifier("supervisorModel") ChatModel supervisorModel,
                         @Qualifier("applicationTaskExecutor") ThreadPoolTaskExecutor applicationTaskExecutor,
                         GenerateSaver generateSaver) {
        this.jsonUtil = jsonUtil;
        this.promptUtil = promptUtil;
        this.agentRepository = agentRepository;
        this.userLlmModelProvider = userLlmModelProvider;
        this.userMemoryProvider = userMemoryProvider;
        this.generateAgentFactory = generateAgentFactory;
        this.ragEvidenceProvider = ragEvidenceProvider;
        this.webEvidenceProvider = webEvidenceProvider;
        this.supervisorChatModel = supervisorModel;
        this.applicationTaskExecutor = applicationTaskExecutor;
        this.generateSaver = generateSaver;
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
        String taskId = request.getTaskId();

        // 读取用户信息
        UserProfileInfoVO info = agentRepository.getUserProfileInfo(userId);
        UserProfileStyleVO style = agentRepository.getUserProfileStyle(userId);
        UserProfileAllowVO allow = agentRepository.getUserProfileAllow(userId);
        String userProfileJson = jsonUtil.toJsonString(info);
        String memoryProfileJson = userMemoryProvider.getGenerationMemory(userId);
        String answerStyle = style.getAnswerStyle();

        // 跨阶段累计 token，用于并发场景下的线程安全统计
        AtomicInteger totalTokens = new AtomicInteger(0);

        // 创建事件发布器
        EventPublisher eventPublisher = new EventPublisher(agentRepository, taskId, userId, sseEventHandler, totalTokens, jsonUtil);

        // 发送任务创建事件
        eventPublisher.publishEvent(GeneratePhase.INIT, GenerateStatus.PROCESSING, "生成任务已创建");

        try {
            Thread.sleep(1000);

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

            // 创建阶段总结器，负责在每个 Agent 调用成功后生成进度消息并推送 SSE
            GenerateSupervisor supervisor = new GenerateSupervisor(taskId, promptUtil, supervisorChatModel, eventPublisher, totalTokens);

            // 构建各阶段执行上下文
            PlanContext planContext = PlanContext.builder()
                    .taskId(taskId)
                    .userId(userId)
                    .request(request)
                    .userProfileJson(userProfileJson)
                    .memoryProfileJson(memoryProfileJson)
                    .allow(allow)
                    .supervisor(supervisor)
                    .eventPublisher(eventPublisher)
                    .build();

            ValidateContext validateContext = ValidateContext.builder()
                    .taskId(taskId)
                    .request(request)
                    .answerStyle(answerStyle)
                    .supervisor(supervisor)
                    .eventPublisher(eventPublisher)
                    .build();

            DecideContext decideContext = DecideContext.builder()
                    .taskId(taskId)
                    .request(request)
                    .supervisor(supervisor)
                    .eventPublisher(eventPublisher)
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
                    .webEvidenceProvider(Boolean.TRUE.equals(allow.getAllowWebSearch()) ? webEvidenceProvider : null)
                    .targetCompany(info.getTargetCompany() != null ? info.getTargetCompany() : "")
                    .targetRole(info.getTargetRole() != null ? info.getTargetRole() : "")
                    .userProfileJson(userProfileJson)
                    .answerStyle(answerStyle)
                    .supervisor(supervisor)
                    .eventPublisher(eventPublisher)
                    .build();

            // 构建 DAG 运行上下文
            GenerateContext generateContext = GenerateContext.builder()
                    .userModel(userModel)
                    .decideStep((scope, decideAgent) -> doDecide(scope, decideAgent, decideContext))
                    .abortStep((scope, abortAgent) -> doAbort(scope, abortAgent, abortContext))
                    .planStep((scope, planAgent) -> doPlan(scope, planAgent, planContext))
                    .writeStep((scope, draftAgent) -> doWrite(scope, draftAgent, writeContext))
                    .validateStep((scope, evaluateAgent, amendAgent) -> doValidate(scope, evaluateAgent, amendAgent, validateContext))
                    .summarizeStep((scope, summarizeAgent) -> doSummarize(scope, summarizeAgent, summarizeContext))
                    .build();

            // 构建智能体
            UntypedAgent generateAgent = generateAgentFactory.build(generateContext);

            // 初始化 Scope 数据，供各阶段读取
            Map<String, Object> initialData = Map.of(
                    "taskId", taskId,
                    "userPrompt", request.getUserPrompt()
            );

            // 执行智能体
            ResultWithAgenticScope<?> dagResult = generateAgent.invokeWithAgenticScope(initialData);

            // 落库并标记完成
            generateSaver.save(dagResult.agenticScope(), taskId, userId, request);

            // 发送终态完成事件
            eventPublisher.publishEvent(GeneratePhase.COMPLETE, GenerateStatus.SOLVED, "问答集生成完成");
        }
        // 已知业务异常：按类型发布失败事件
        catch (GenerateException exception) {
            eventPublisher.publishFailure(exception.getAgentErrorType(), exception.getMessage());
        }
        // 未知异常：映射错误类型后发布失败事件
        catch (Exception exception) {
            eventPublisher.publishFailure(AgentErrorType.fromException(exception), exception.getMessage());
        }
    }

    /**
     * DecideAgent 负责判断用户需求是否符合问答集生成场景，输出 valid 判定结果写入 scope。
     */
    private void doDecide(AgenticScope scope, DecideAgent decideAgent, DecideContext decideContext) {
        // 1. 更新状态
        agentRepository.updateTaskPhase(decideContext.getTaskId(), GeneratePhase.DECIDE);
        decideContext.getEventPublisher().publishProgress("💭 需求分析", "正在分析需求是否符合生成场景...");

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
                    log.warn("【生成问答集】决策最终失败: maxRetries={}, taskId={}", MAX_RETRY, decideContext.getTaskId(), exception);
                    writeDecideResult(scope, null);
                } else {
                    log.warn("【生成问答集】决策失败，重试: attempt={}, taskId={}", attempt + 1, decideContext.getTaskId(), exception);
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
            log.warn("【生成问答集】中断调用异常: taskId={}", abortContext.getTaskId(), exception);
            message = reason;
        }

        // 4. 发送中断信息
        abortContext.getEventPublisher().publishCanceled(AgentErrorType.CONTENT_FILTERED, message);
    }

    /**
     * PlanAgent 负责分析资料目录结构并规划模块化题集方案，输出 planResult 写入 scope。
     */
    private void doPlan(AgenticScope scope, PlanAgent planAgent, PlanContext planContext) {
        // 1. 更新状态
        agentRepository.updateTaskPhase(planContext.getTaskId(), GeneratePhase.PLAN);
        planContext.getEventPublisher().publishProgress("🧭 模块规划", "正在分析资料结构，规划模块分配...");

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
                        planContext.getMemoryProfileJson(),
                        planContext.getRequest().getUserPrompt(),
                        planContext.getRequest().getJobDescription(),
                        planContext.getRequest().getRequestedQuestionCount(),
                        retryHint
                );
                break;
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                if (attempt == MAX_RETRY) {
                    log.warn("【生成问答集】规划最终失败: maxRetries={}, taskId={}", MAX_RETRY, planContext.getTaskId(), exception);
                } else {
                    log.warn("【生成问答集】规划失败，重试: attempt={}, taskId={}", attempt + 1, planContext.getTaskId(), exception);
                }
            }
        }
        if (planResult == null) {
            if (Boolean.TRUE.equals(planContext.getAllow().getAllowFallback())) {
                log.warn("【生成问答集】规划失败，启用兜底方案: taskId={}", planContext.getTaskId());
                planResult = fallbackPlan(planContext.getRequest());
            } else {
                throw new GenerateException(AgentErrorType.UNKNOWN, "PlanAgent 调用失败，已重试 " + MAX_RETRY + " 次");
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
        List<PlanResult.PlanItem> planItems = planResult.getPlanItems();

        // 3. Phase 1: 每 3 个模块一组并发预搜证据（RAG + Web），组间串行
        writeContext.getEventPublisher().publishProgress("📚 证据检索", "开始检索资料证据，共覆盖 " + planItems.size() + " 个模块...");
        Map<String, String> evidenceMap = Collections.synchronizedMap(new LinkedHashMap<>());
        Map<String, List<String>> chunkIdsMap = Collections.synchronizedMap(new LinkedHashMap<>());
        Map<String, List<String>> fallbackChunkIdsMap = Collections.synchronizedMap(new LinkedHashMap<>());

        for (int g = 0; g < planItems.size(); g += GROUP_SIZE) {
            List<PlanResult.PlanItem> group = planItems.subList(g, Math.min(g + GROUP_SIZE, planItems.size()));
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (PlanResult.PlanItem planItem : group) {
                futures.add(CompletableFuture.runAsync(() -> {
                    try {
                        List<RagEvidenceProvider.EvidenceItem> ragEvidence = writeContext.getRagEvidenceProvider().searchByPlanItem(
                                writeContext.getUserId(), writeContext.getRequest().getDocumentIds(), planItem);
                        String evidenceJson;
                        if (writeContext.getWebEvidenceProvider() != null) {
                            List<InterviewInsights> webEvidence = writeContext.getWebEvidenceProvider().search(
                                    writeContext.getTargetCompany(), writeContext.getTargetRole(), planItem);
                            evidenceJson = jsonUtil.toJsonString(Map.of(
                                    "ragResults", ragEvidence,
                                    "interviewInsights", webEvidence));
                        } else {
                            evidenceJson = jsonUtil.toJsonString(ragEvidence);
                        }
                        evidenceMap.put(planItem.getModule(), evidenceJson);
                        chunkIdsMap.put(planItem.getModule(), ragEvidence.stream()
                                .map(RagEvidenceProvider.EvidenceItem::getChunkId)
                                .filter(StringUtils::hasText)
                                .distinct()
                                .toList());
                        fallbackChunkIdsMap.put(planItem.getModule(), ragEvidence.stream()
                                .map(RagEvidenceProvider.EvidenceItem::getChunkId)
                                .filter(StringUtils::hasText)
                                .distinct()
                                .limit(FALLBACK_SOURCE_CHUNK_COUNT)
                                .toList());
                    } catch (Exception e) {
                        log.warn("【生成问答集】证据预搜失败: taskId={}, module={}", writeContext.getTaskId(), planItem.getModule(), e);
                        evidenceMap.put(planItem.getModule(), jsonUtil.toJsonString(List.of()));
                        chunkIdsMap.put(planItem.getModule(), List.of());
                        fallbackChunkIdsMap.put(planItem.getModule(), List.of());
                    }
                }, applicationTaskExecutor));
            }
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            for (PlanResult.PlanItem planItem : group) {
                writeContext.getEventPublisher().publishProgress("📚 证据检索", "已完成「" + planItem.getModule() + "」证据检索");
            }
        }

        // 4. Phase 2: 并行出题，agentAction 内只做纯 LLM 调用，零 DB 访问
        writeContext.getEventPublisher().publishProgress("️️✏️ 题目起草", "证据收集完毕，开始起草 " + planItems.size() + " 个模块的题目...");
        List<DraftResult> draftResults = Collections.synchronizedList(new ArrayList<>());
        List<Object> moduleAgents = new ArrayList<>();
        agentRepository.updateTaskPhase(writeContext.getTaskId(), GeneratePhase.DRAFT);
        for (PlanResult.PlanItem planItem : planItems) {
            String evidenceJson = evidenceMap.get(planItem.getModule());
            List<String> sourceChunkIds = chunkIdsMap.get(planItem.getModule());
            List<String> fallbackSourceChunkIds = fallbackChunkIdsMap.get(planItem.getModule());
            AgenticServices.AgenticScopeAction agentAction = AgenticServices.agentAction(moduleScope -> {
                try {
                    DraftContext draftContext = DraftContext.builder()
                            .taskId(writeContext.getTaskId())
                            .request(writeContext.getRequest())
                            .planItem(planItem)
                            .evidence(evidenceJson)
                            .userProfileJson(writeContext.getUserProfileJson())
                            .answerStyle(writeContext.getAnswerStyle())
                            .supervisor(writeContext.getSupervisor())
                            .allowedSourceChunkIds(sourceChunkIds)
                            .fallbackSourceChunkIds(fallbackSourceChunkIds)
                            .build();
                    draftResults.addAll(doDraft(draftAgent, draftContext));
                } catch (Exception exception) {
                    log.warn("【生成问答集】出题并行调用异常: taskId={}, module={}", writeContext.getTaskId(), planItem.getModule(), exception);
                }
            });
            moduleAgents.add(agentAction);
        }

        // 5. 组装为并发工作流
        UntypedAgent writer = AgenticServices.parallelBuilder()
                .name(GeneratePhase.WRITE.getAgentName())
                .description(GeneratePhase.WRITE.getAgentDesc())
                .executor(writeContext.getExecutor())
                .subAgents(moduleAgents)
                .output(moduleScope -> draftResults)
                .build();

        // 6. 调用智能体
        try {
            writer.invoke(Map.of());
        } catch (Exception exception) {
            log.warn("【生成问答集】出题阶段整体异常: taskId={}", writeContext.getTaskId(), exception);
            writeDraftResult(scope, draftResults);
            throw new GenerateException(AgentErrorType.fromException(exception), "WriteAgent 调用失败: " + exception.getMessage());
        }

        // 7. 写入共享领域
        writeDraftResult(scope, draftResults);
    }

    /**
     * DraftAgent 负责按模块证据分批起草结构化问答题目。
     */
    private List<DraftResult> doDraft(DraftAgent draftAgent, DraftContext draftContext) {
        // 当前模块题数
        int remaining = draftContext.getPlanItem().getQuestionCount();
        List<DraftResult> draftResults = new ArrayList<>();
        while (remaining > 0) {
            // 当前批次题数
            int batchCount = Math.min(BATCH_SIZE, remaining);
            String previousQuestions = jsonUtil.toJsonString(draftResults.stream()
                    .filter(item -> item != null && item.getQuestion() != null)
                    .map(DraftResult::getQuestion)
                    .toList());
            List<DraftResult> batchItems = null;
            String retryHint = "";
            for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
                try {
                    String response = draftAgent.draft(
                            draftContext.getTaskId(),
                            draftContext.getPlanItem().getModule(),
                            draftContext.getPlanItem().getKeyConcepts(),
                            draftContext.getEvidence(),
                            draftContext.getUserProfileJson(),
                            draftContext.getRequest().getUserPrompt(),
                            draftContext.getRequest().getJobDescription(),
                            batchCount,
                            previousQuestions,
                            draftContext.getAnswerStyle(),
                            retryHint
                    );
                    batchItems = jsonUtil.parseJsonArray(response, DraftResult.class);
                    for (DraftResult item : batchItems) {
                        item.setSourceChunkIds(cleanSourceChunkIds(item.getSourceChunkIds(),
                                draftContext.getAllowedSourceChunkIds(),
                                draftContext.getFallbackSourceChunkIds()));
                    }
                    draftContext.getSupervisor().doSupervise(GeneratePhase.DRAFT, response);
                    break;
                } catch (Exception exception) {
                    retryHint = exception.getMessage();
                    if (attempt == MAX_RETRY) {
                        log.warn("【生成问答集】出题批次最终失败: maxRetries={}, taskId={}, module={}", MAX_RETRY, draftContext.getTaskId(), draftContext.getPlanItem().getModule(), exception);
                        batchItems = fallbackDraft(draftContext.getPlanItem(), draftContext.getEvidence(), draftContext.getFallbackSourceChunkIds());
                    } else {
                        log.warn("【生成问答集】出题批次失败，重试: attempt={}, taskId={}, module={}", attempt + 1, draftContext.getTaskId(), draftContext.getPlanItem().getModule(), exception);
                    }
                }
            }
            draftResults.addAll(batchItems);
            remaining -= batchCount;
        }
        return draftResults;
    }

    /**
     * ValidateAgent 负责审校 draftResult 中的题目并修订可修复项，输出 validatedResult 写入 scope。
     */
    private void doValidate(AgenticScope scope, EvaluateAgent evaluateAgent, AmendAgent amendAgent, ValidateContext validateContext) {
        // 1. 更新状态
        agentRepository.updateTaskPhase(validateContext.getTaskId(), GeneratePhase.VALIDATE);
        validateContext.getEventPublisher().publishProgress("🔬 审校修订", "开始审校已生成的题目...");

        // 2. 初次生成的问答集合
        List<DraftResult> draftResults = readDraftResult(scope);

        // 3. 获取不同批次集合
        List<List<DraftResult>> batchList = new ArrayList<>();
        for (int i = 0; i < draftResults.size(); i += BATCH_SIZE) {
            batchList.add(draftResults.subList(i, Math.min(i + BATCH_SIZE, draftResults.size())));
        }

        // 4. 创建每个批次的异步任务
        List<CompletableFuture<List<DraftResult>>> futureList = batchList.stream()
                .map(batch -> CompletableFuture
                        .supplyAsync(() -> doValidateLoop(
                                validateContext.getTaskId(),
                                evaluateAgent, amendAgent,
                                ValidateLoopContext.builder()
                                        .batch(batch)
                                        .userPrompt(validateContext.getRequest().getUserPrompt())
                                        .jobDescription(validateContext.getRequest().getJobDescription())
                                        .answerStyle(validateContext.getAnswerStyle())
                                        .supervisor(validateContext.getSupervisor())
                                        .build()),
                                applicationTaskExecutor)
                        .exceptionally(exception -> {
                            log.warn("【生成问答集】审校批次异常，退回归档: taskId={}", validateContext.getTaskId(), exception);
                            return batch;
                        }))
                .toList();

        // 5. 等待所有批次的异步任务执行完成并汇总结果
        List<DraftResult> validatedItems = futureList.stream()
                .map(CompletableFuture::join)
                .flatMap(List::stream)
                .toList();

        // 6. 写入共享领域
        validateContext.getEventPublisher().publishProgress("🔬 审校修订", "审校完成，" + validatedItems.size() + " 题通过");
        writeValidateResult(scope, validatedItems);
    }

    private List<DraftResult> doValidateLoop(String taskId, EvaluateAgent evaluateAgent, AmendAgent amendAgent, ValidateLoopContext loopContext) {
        List<DraftResult> passItems = new ArrayList<>();
        AtomicReference<List<DraftResult>> evaluateItems = new AtomicReference<>(loopContext.getBatch());
        AtomicReference<List<AmendContext.AmendItem>> amendItems = new AtomicReference<>(List.of());
        AtomicBoolean flag = new AtomicBoolean(false);

        UntypedAgent validateAgent = AgenticServices.loopBuilder()
                .name(GeneratePhase.VALIDATE.getAgentName())
                .description(GeneratePhase.VALIDATE.getAgentDesc())
                .maxIterations(2)
                .exitCondition((scope, iteration) -> flag.get())
                .subAgents(
                        // 校验
                        AgenticServices.agentAction(scope -> {
                            List<EvaluateResult> evaluates = doEvaluate(taskId,
                                    evaluateAgent,
                                    EvaluateContext.builder()
                                            .drafts(evaluateItems.get())
                                            .userPrompt(loopContext.getUserPrompt())
                                            .jobDescription(loopContext.getJobDescription())
                                            .supervisor(loopContext.getSupervisor())
                                            .build()
                            );

                            List<AmendContext.AmendItem> amends = new ArrayList<>();
                            for (int i = 0; i < Math.min(evaluateItems.get().size(), evaluates.size()); i++) {
                                if (VerdictType.PASS.name().equals(evaluates.get(i).getVerdict())) {
                                    passItems.add(evaluateItems.get().get(i));
                                } else {
                                    AmendContext.AmendItem amendItem = AmendContext.AmendItem.builder()
                                            .draftResult(evaluateItems.get().get(i))
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
                            List<DraftResult> amended = doAmend(taskId, amendAgent,
                                    AmendContext.builder()
                                            .items(amendItems.get())
                                            .userPrompt(loopContext.getUserPrompt())
                                            .jobDescription(loopContext.getJobDescription())
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
            log.warn("【生成问答集】审校阶段整体异常: taskId={}", taskId, exception);
            if (passItems.isEmpty()) {
                passItems.addAll(loopContext.getBatch());
            }
        }

        return passItems;
    }

    private List<EvaluateResult> doEvaluate(String taskId, EvaluateAgent evaluateAgent, EvaluateContext evaluateContext) {
        String retryHint = "";
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                String response = evaluateAgent.evaluate(taskId, jsonUtil.toJsonString(evaluateContext.getDrafts()),
                        evaluateContext.getUserPrompt(), evaluateContext.getJobDescription(), retryHint);
                List<EvaluateResult> results = jsonUtil.parseJsonArray(response, EvaluateResult.class);
                evaluateContext.getSupervisor().doSupervise(GeneratePhase.EVALUATE, response);
                return results;
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                if (attempt == MAX_RETRY) {
                    log.warn("【生成问答集】评估最终失败: maxRetries={}, taskId={}", MAX_RETRY, taskId, exception);
                    return fallbackEvaluate(evaluateContext.getDrafts());
                }
                log.warn("【生成问答集】评估失败，重试: attempt={}, taskId={}", attempt + 1, taskId, exception);
            }
        }
        return fallbackEvaluate(evaluateContext.getDrafts());
    }

    private List<DraftResult> doAmend(String taskId, AmendAgent amendAgent, AmendContext amendContext) {
        String retryHint = "";
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                String response = amendAgent.amend(taskId, jsonUtil.toJsonString(amendContext.getItems()),
                        amendContext.getUserPrompt(), amendContext.getJobDescription(),
                        amendContext.getAnswerStyle(), retryHint);
                List<DraftResult> results = jsonUtil.parseJsonArray(response, DraftResult.class);
                for (int i = 0; i < results.size() && i < amendContext.getItems().size(); i++) {
                    DraftResult original = amendContext.getItems().get(i).getDraftResult();
                    results.get(i).setSourceReliable(original.getSourceReliable());
                    results.get(i).setSourceChunkIds(original.getSourceChunkIds());
                }
                amendContext.getSupervisor().doSupervise(GeneratePhase.AMEND, response);
                return results;
            } catch (Exception exception) {
                retryHint = exception.getMessage();
                if (attempt == MAX_RETRY) {
                    log.warn("【生成问答集】修订最终失败: maxRetries={}, taskId={}", MAX_RETRY, taskId, exception);
                    return fallbackAmend(amendContext.getItems());
                }
                log.warn("【生成问答集】修订失败，重试: attempt={}, taskId={}", attempt + 1, taskId, exception);
            }
        }
        return fallbackAmend(amendContext.getItems());
    }

    /**
     * SummarizeAgent 负责生成完成说明并写入 scope。
     */
    private void doSummarize(AgenticScope scope, SummarizeAgent summarizeAgent, SummarizeContext summarizeContext) {
        // 1. 更新状态
        agentRepository.updateTaskPhase(summarizeContext.getTaskId(), GeneratePhase.SUMMARIZE);
        summarizeContext.getEventPublisher().publishEvent(GeneratePhase.SUMMARIZE, GenerateStatus.PROCESSING, "正在生成完成摘要...");

        // 2. 计划结果
        PlanResult planResult = readPlanResult(scope);

        // 3. 生成结果
        List<DraftResult> validatedResult = readValidateResult(scope);

        // 4. 解析结果
        int requiredCount = summarizeContext.getRequest().getRequestedQuestionCount();
        int generatedCount = validatedResult.size();
        String modules = planResult.getPlanItems().stream()
                .map(item -> item.getModule() == null ? "" : item.getModule())
                .filter(StringUtils::hasText)
                .reduce((a, b) -> a + ", " + b)
                .orElse("");
        String tags = validatedResult.stream()
                .map(DraftResult::getTag)
                .filter(StringUtils::hasText)
                .distinct()
                .reduce((a, b) -> a + ", " + b)
                .orElse("");

        // 5. 调用智能体
        String summaryMessage;
        try {
            summaryMessage = summarizeAgent.summarize(
                    summarizeContext.getTaskId(),
                    summarizeContext.getRequest().getUserPrompt(),
                    summarizeContext.getRequest().getJobDescription(),
                    summarizeContext.getUserProfileJson(),
                    planResult.getTitle() != null && !planResult.getTitle().isEmpty() ? planResult.getTitle() : summarizeContext.getRequest().getTitle(),
                    planResult.getDescription() != null ? planResult.getDescription() : "",
                    requiredCount,
                    generatedCount,
                    modules,
                    tags,
                    jsonUtil.toJsonString(validatedResult)
            );
        } catch (Exception exception) {
            log.warn("【生成问答集】总结调用异常: taskId={}", summarizeContext.getTaskId(), exception);
            summaryMessage = fallbackSummarize(requiredCount, generatedCount, modules, tags);
        }

        // 6. 发送消息
        summarizeContext.getEventPublisher().publishEvent(GeneratePhase.COMPLETE, GenerateStatus.SOLVED, summaryMessage);
    }

    private DecideResult fallbackDecide() {
        return new DecideResult(false, "DecideAgent 执行出错，默认判定为不可继续执行");
    }

    private List<String> cleanSourceChunkIds(List<String> rawSourceChunkIds,
                                             List<String> allowedChunkIds,
                                             List<String> fallbackChunkIds) {
        Set<String> allowed = allowedChunkIds == null ? Set.of() : new LinkedHashSet<>(allowedChunkIds);
        List<String> cleaned = new ArrayList<>();
        if (rawSourceChunkIds != null && !allowed.isEmpty()) {
            for (String chunkId : rawSourceChunkIds) {
                if (!StringUtils.hasText(chunkId) || !allowed.contains(chunkId) || cleaned.contains(chunkId)) {
                    continue;
                }
                cleaned.add(chunkId);
                if (cleaned.size() >= MAX_SOURCE_CHUNK_COUNT) {
                    break;
                }
            }
        }
        if (!cleaned.isEmpty()) {
            return cleaned;
        }
        if (fallbackChunkIds == null || fallbackChunkIds.isEmpty()) {
            return List.of();
        }
        for (String chunkId : fallbackChunkIds) {
            if (!StringUtils.hasText(chunkId) || (!allowed.isEmpty() && !allowed.contains(chunkId)) || cleaned.contains(chunkId)) {
                continue;
            }
            cleaned.add(chunkId);
            if (cleaned.size() >= FALLBACK_SOURCE_CHUNK_COUNT) {
                break;
            }
        }
        return cleaned;
    }

    private PlanResult fallbackPlan(CreateQaSetRequest request) {
        return new PlanResult(
                request.getTitle(),
                "根据用户资料生成的技术面试问答集",
                List.of(new PlanResult.PlanItem("General", request.getRequestedQuestionCount(),
                        List.of("核心知识点"), "核心考点描述"))
        );
    }

    private List<DraftResult> fallbackDraft(PlanResult.PlanItem planItem, String evidence, List<String> sourceChunkIds) {
        int count = Math.max(1, planItem.getQuestionCount());
        List<DraftResult> drafts = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            drafts.add(DraftResult.builder()
                    .question(planItem.getModule() + " 的核心问题 " + (i + 1))
                    .knowledgeNote(evidence)
                    .answer(evidence)
                    .tag(planItem.getModule())
                    .difficulty("MEDIUM")
                    .sourceReliable(StringUtils.hasText(evidence) && sourceChunkIds != null && !sourceChunkIds.isEmpty())
                    .sourceChunkIds(sourceChunkIds)
                    .build());
        }
        return drafts;
    }

    private List<DraftResult> fallbackAmend(List<AmendContext.AmendItem> items) {
        List<DraftResult> results = new ArrayList<>();
        for (AmendContext.AmendItem item : items) {
            DraftResult amended = item.getDraftResult();
            results.add(amended);
        }
        return results;
    }

    private List<EvaluateResult> fallbackEvaluate(List<DraftResult> drafts) {
        List<EvaluateResult> results = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            results.add(new EvaluateResult(VerdictType.PASS.name(), "fallback pass", ""));
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

    private List<DraftResult> readValidateResult(AgenticScope scope) {
        return (List<DraftResult>) scope.readState(GeneratePhase.VALIDATE.getScopeKey());
    }

    private List<DraftResult> readDraftResult(AgenticScope scope) {
        return (List<DraftResult>) scope.readState(GeneratePhase.WRITE.getScopeKey());
    }

    private void writeDecideResult(AgenticScope scope, DecideResult result) {
        scope.writeState(GeneratePhase.DECIDE.getScopeKey(), result != null ? result : fallbackDecide());
    }

    private void writePlanResult(AgenticScope scope, PlanResult result, CreateQaSetRequest request) {
        scope.writeState(GeneratePhase.PLAN.getScopeKey(), result != null ? result : fallbackPlan(request));
    }

    private void writeDraftResult(AgenticScope scope, List<DraftResult> result) {
        scope.writeState(GeneratePhase.WRITE.getScopeKey(), result != null ? result : List.of());
    }

    private void writeValidateResult(AgenticScope scope, List<DraftResult> result) {
        scope.writeState(GeneratePhase.VALIDATE.getScopeKey(), result != null ? result : List.of());
    }

}
