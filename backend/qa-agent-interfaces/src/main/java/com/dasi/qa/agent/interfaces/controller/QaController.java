package com.dasi.qa.agent.interfaces.controller;

import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.generate.IGenerateAgent;
import com.dasi.qa.agent.domain.agent.service.shared.SseEvent;
import com.dasi.qa.agent.domain.qa.service.set.IQaSetService;
import com.dasi.qa.agent.domain.qa.service.item.IQaItemService;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.interfaces.handler.SseEventHandler;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaSetRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaItemCompleteRetryRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaItemRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaSetRequest;
import com.dasi.qa.agent.types.dto.request.qa.CreateQaItemRequest;
import com.dasi.qa.agent.types.dto.response.qa.*;
import com.dasi.qa.agent.types.result.Result;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.function.Consumer;

@RestController
@RequestMapping("/qa")
public class QaController {

    private final IQaSetService qaSetService;
    private final IQaItemService qaItemService;
    private final IGenerateAgent generationAgent;
    private final IAgentRepository agentRepository;
    private final IContextUtil contextUtil;
    private final ThreadPoolTaskExecutor applicationTaskExecutor;

    public QaController(IQaSetService qaSetService,
                        IQaItemService qaItemService,
                        IGenerateAgent generationAgent,
                        IAgentRepository agentRepository,
                        IContextUtil contextUtil,
                        @Qualifier("applicationTaskExecutor") ThreadPoolTaskExecutor applicationTaskExecutor) {
        this.qaSetService = qaSetService;
        this.qaItemService = qaItemService;
        this.generationAgent = generationAgent;
        this.agentRepository = agentRepository;
        this.contextUtil = contextUtil;
        this.applicationTaskExecutor = applicationTaskExecutor;
    }

    @GetMapping("/set/detail")
    public Result<QaSetResponse> qaSetDetail(@RequestParam("id") String id) {
        return Result.success(qaSetService.detailQaSet(id));
    }

    @PostMapping("/set/query")
    public Result<List<QaSetResponse>> qaSetQuery(@RequestBody QaSetRequest request) {
        return Result.success(qaSetService.queryQaSet(request));
    }

    @PostMapping("/set/update")
    public Result<QaSetResponse> qaSetUpdate(@RequestBody QaSetRequest request) {
        return Result.success(qaSetService.updateQaSet(request));
    }

    @PostMapping("/set/delete")
    public Result<Void> qaSetDelete(@RequestBody QaSetRequest request) {
        qaSetService.deleteQaSet(request.getId());
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
        return Result.success(qaItemService.detailQaItem(id));
    }

    @PostMapping("/item/query")
    public Result<List<QaItemResponse>> qaItemQuery(@RequestBody QaItemRequest request) {
        return Result.success(qaItemService.queryQaItem(request));
    }

    @PostMapping("/item/update")
    public Result<QaItemResponse> qaItemUpdate(@RequestBody QaItemRequest request) {
        return Result.success(qaItemService.updateQaItem(request));
    }

    @PostMapping("/item/create")
    public Result<QaItemResponse> qaItemCreate(@RequestBody @Valid CreateQaItemRequest request) {
        return Result.success(qaItemService.createQaItem(request));
    }

    @PostMapping("/item/complete")
    public Result<QaItemResponse> qaItemComplete(@RequestBody @Valid QaItemCompleteRetryRequest request) {
        return Result.success(qaItemService.completeQaItem(request));
    }

    @PostMapping("/item/delete")
    public Result<Void> qaItemDelete(@RequestBody QaItemRequest request) {
        qaItemService.deleteQaItem(request.getId());
        return Result.success();
    }

}
