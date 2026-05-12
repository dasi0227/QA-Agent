package com.dasi.qa.agent.interfaces.controller;

import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.generate.IGenerateAgent;
import com.dasi.qa.agent.domain.qa.service.crud.IQaCrudService;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaItemRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaSetRequest;
import com.dasi.qa.agent.types.dto.response.qa.QaItemResponse;
import com.dasi.qa.agent.types.dto.response.qa.QaSetResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskMessageResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskListItemResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskStatusResponse;
import com.dasi.qa.agent.domain.agent.shared.sse.SseEvent;
import com.dasi.qa.agent.interfaces.handler.SseEventHandler;
import com.dasi.qa.agent.types.result.Result;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

@RestController
@RequestMapping("/qa")
public class QaController {

    private final IQaCrudService qaService;
    private final IGenerateAgent generationAgent;
    private final IAgentRepository agentRepository;
    private final IContextUtil contextUtil;
    private final ThreadPoolTaskExecutor applicationTaskExecutor;

    public QaController(IQaCrudService qaService,
                        IGenerateAgent generationAgent,
                        IAgentRepository agentRepository,
                        IContextUtil contextUtil,
                        @Qualifier("applicationTaskExecutor") ThreadPoolTaskExecutor applicationTaskExecutor) {
        this.qaService = qaService;
        this.generationAgent = generationAgent;
        this.agentRepository = agentRepository;
        this.contextUtil = contextUtil;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    @GetMapping("/set/detail")
    public Result<QaSetResponse> qaSetDetail(@RequestParam("id") String id) {
        return Result.success(qaService.detailQaSet(id));
    }

    @PostMapping("/set/query")
    public Result<List<QaSetResponse>> qaSetQuery(@RequestBody QaSetRequest request) {
        return Result.success(qaService.queryQaSet(request));
    }

    @PostMapping("/set/update")
    public Result<QaSetResponse> qaSetUpdate(@RequestBody QaSetRequest request) {
        return Result.success(qaService.updateQaSet(request));
    }

    @PostMapping("/set/delete")
    public Result<Void> qaSetDelete(@RequestBody QaSetRequest request) {
        qaService.deleteQaSet(request.getId());
        return Result.success();
    }

    @PostMapping("/set/create")
    public SseEmitter qaSetCreate(@RequestBody @Valid CreateQaSetRequest request) {
        SseEmitter emitter = new SseEmitter(600000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(emitter::completeWithError);

        Consumer<SseEvent> sseEventHandler = new SseEventHandler(emitter);
        String userId = contextUtil.getUserId();
        applicationTaskExecutor.execute(() -> generationAgent.execute(userId, request, sseEventHandler));

        return emitter;
    }

    @Profile("dev")
    @PostMapping("/set/create/test")
    public SseEmitter qaSetCreateTest() {
        SseEmitter emitter = new SseEmitter(120000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(emitter::completeWithError);

        String taskId = "mock-" + UUID.randomUUID().toString().substring(0, 8);
        applicationTaskExecutor.execute(() -> {
            try {
                // INIT
                mockEvent(emitter, taskId, "🚀 任务启动", "PROCESSING", "生成任务已创建", 0, 0, false);
                Thread.sleep(1500);
                // DECIDE
                mockEvent(emitter, taskId, "🤔 请求判定", "PROCESSING", "您的需求符合技术面试问答集生成场景，系统已确认进入生成流程。", 120, 120, false);
                Thread.sleep(10000);
                // PLAN
                mockEvent(emitter, taskId, "☑️ 规划模块", "PROCESSING", "计划覆盖 Redis、JVM、Spring 三大核心模块，共规划 10 道题目，难度分布均衡。", 350, 470, false);
                Thread.sleep(10000);

                // DRAFT
                mockEvent(emitter, taskId, "✍️ 检索起草", "PROCESSING", "已生成 4 道 Redis 核心题目，覆盖跳表、持久化 RDB/AOF、缓存淘汰策略、数据结构对比。", 850, 1320, false);
                Thread.sleep(1500);
                mockEvent(emitter, taskId, "✍️ 检索起草", "PROCESSING", "已生成 3 道 JVM 模块题目，涉及类加载机制、GC 算法选择、JMM 内存模型与并发。", 680, 2000, false);
                Thread.sleep(2000);
                mockEvent(emitter, taskId, "✍️ 检索起草", "PROCESSING", "已生成 3 道 Spring 模块题目，涵盖 IoC 与 AOP 原理、事务传播机制、循环依赖解决方案。", 620, 2620, false);
                Thread.sleep(2500);

                // EVALUATE
                mockEvent(emitter, taskId, "🔍 内容审校", "PROCESSING", "已完成首轮审校，8 题通过，2 题需修订。修订项：Redis 缓存淘汰策略（答案缺少 eviction 流程）、JVM GC 算法对比（缺少 G1 细节）。", 420, 3040, false);
                Thread.sleep(10000);
                // AMEND
                mockEvent(emitter, taskId, "🔧 修订完善", "PROCESSING", "已完成 2 道题目的修订：Redis 缓存淘汰策略已补充 LRU/LFU 对比，JVM GC 对比已补充 G1 回收流程与适用场景。", 380, 3420, false);
                Thread.sleep(3000);
                // 复审
                mockEvent(emitter, taskId, "🔍 内容审校", "PROCESSING", "已完成修订后复审，全部 10 题通过。修订项均通过资料证据校验，无事实错误。", 310, 3730, false);
                Thread.sleep(4000);

                // COMPLETE
                mockEvent(emitter, taskId, "🎉 任务完成", "SOLVED", "共生成 10 道技术面试题，全部通过审校，覆盖 Redis、JVM、Spring 三大模块。", 0, 3730, true);
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }

    private void mockEvent(SseEmitter emitter, String taskId, String phase, String status,
                           String message, int currentTokens, int totalTokens, boolean isCompleted) throws Exception {
        String data = "{\n"
                + "  \"taskId\": \"" + taskId + "\",\n"
                + "  \"phase\": \"" + phase + "\",\n"
                + "  \"status\": \"" + status + "\",\n"
                + "  \"message\": \"" + message + "\",\n"
                + "  \"timestamp\": " + System.currentTimeMillis() + ",\n"
                + "  \"currentTokens\": " + currentTokens + ",\n"
                + "  \"totalTokens\": " + totalTokens + ",\n"
                + "  \"isCompleted\": " + isCompleted + "\n"
                + "}";
        emitter.send(SseEmitter.event().data(data));
    }

    @GetMapping("/set/task-status")
    public Result<TaskStatusResponse> taskStatus(@RequestParam("taskId") String taskId) {
        return Result.success(agentRepository.getTaskStatus(taskId, contextUtil.getUserId()));
    }

    @GetMapping("/set/task-messages")
    public Result<List<TaskMessageResponse>> taskMessages(@RequestParam("taskId") String taskId) {
        return Result.success(agentRepository.getTaskMessages(taskId, contextUtil.getUserId()));
    }

    @GetMapping("/set/task-list")
    public Result<List<TaskListItemResponse>> taskList() {
        return Result.success(agentRepository.getTaskList(contextUtil.getUserId()));
    }

    @GetMapping("/item/detail")
    public Result<QaItemResponse> qaItemDetail(@RequestParam("id") String id) {
        return Result.success(qaService.detailQaItem(id));
    }

    @PostMapping("/item/query")
    public Result<List<QaItemResponse>> qaItemQuery(@RequestBody QaItemRequest request) {
        return Result.success(qaService.queryQaItem(request));
    }

    @PostMapping("/item/create")
    public Result<QaItemResponse> qaItemCreate(@RequestBody QaItemRequest request) {
        return Result.success(qaService.createQaItem(request));
    }

    @PostMapping("/item/update")
    public Result<QaItemResponse> qaItemUpdate(@RequestBody QaItemRequest request) {
        return Result.success(qaService.updateQaItem(request));
    }

    @PostMapping("/item/delete")
    public Result<Void> qaItemDelete(@RequestBody QaItemRequest request) {
        qaService.deleteQaItem(request.getId());
        return Result.success();
    }

}
