package com.dasi.qa.agent.interfaces.controller;

import com.dasi.qa.agent.domain.agent.repository.IAgentRepository;
import com.dasi.qa.agent.domain.agent.service.generate.IGenerateAgent;
import com.dasi.qa.agent.domain.qa.service.crud.IQaCrudService;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.types.dto.request.qa.CreateTaskRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaItemRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaSetRequest;
import com.dasi.qa.agent.types.dto.response.qa.QaItemResponse;
import com.dasi.qa.agent.types.dto.response.qa.QaSetResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskMessageResponse;
import com.dasi.qa.agent.types.dto.response.qa.TaskStatusResponse;
import com.dasi.qa.agent.domain.agent.shared.sse.SseEvent;
import com.dasi.qa.agent.interfaces.handler.SseEventHandler;
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
    public SseEmitter qaSetCreate(@RequestBody @Valid CreateTaskRequest request) {
        SseEmitter emitter = new SseEmitter(120000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(emitter::completeWithError);

        Consumer<SseEvent> sseEventHandler = new SseEventHandler(emitter);
        applicationTaskExecutor.execute(() -> generationAgent.execute(contextUtil.getUserId(), request, sseEventHandler));

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
