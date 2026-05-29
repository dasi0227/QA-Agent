package com.dasi.qa.agent.interfaces.controller;

import com.dasi.qa.agent.domain.agent.service.shared.SseEvent;
import com.dasi.qa.agent.domain.qa.service.item.IQaItemService;
import com.dasi.qa.agent.domain.qa.service.set.IQaSetService;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.interfaces.handler.SseEventHandler;
import com.dasi.qa.agent.types.dto.request.qa.*;
import com.dasi.qa.agent.types.dto.response.qa.*;
import com.dasi.qa.agent.types.result.Result;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

@RestController
@RequestMapping("/qa")
@Slf4j
public class QaController {

    private final IQaSetService qaSetService;
    private final IQaItemService qaItemService;
    private final IContextUtil contextUtil;

    public QaController(IQaSetService qaSetService,
                        IQaItemService qaItemService,
                        IContextUtil contextUtil) {
        this.qaSetService = qaSetService;
        this.qaItemService = qaItemService;
        this.contextUtil = contextUtil;
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

    @GetMapping(value = "/set/export", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<ByteArrayResource> qaSetExport(@RequestParam("id") String id) {
        QaSetExportResponse response = qaSetService.exportQaSet(id);
        String encodedFileName = URLEncoder.encode(response.getFileName(), StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename*=UTF-8''" + encodedFileName)
                .header(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION)
                .body(new ByteArrayResource(response.getContent()));
    }

    @PostMapping(value = "/set/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<QaSetResponse> qaSetImport(@RequestParam("file") MultipartFile file) throws IOException {
        return Result.success(qaSetService.importQaSet(QaSetImportRequest.builder()
                .fileName(file.getOriginalFilename())
                .content(file.getBytes())
                .build()));
    }

    @PostMapping("/set/empty")
    public Result<QaSetResponse> qaSetEmptyCreate(@RequestBody @Valid CreateEmptyQaSetRequest request) {
        return Result.success(qaSetService.createEmptyQaSet(request));
    }

    @PostMapping("/set/reindex")
    public Result<Void> qaSetReindex(@RequestBody @Valid QaSetReindexRequest request) {
        qaSetService.reindexQaSet(request);
        return Result.success();
    }

    @PostMapping("/set/task")
    public Result<TaskCreateResponse> taskCreate(@RequestBody @Valid CreateQaSetRequest request) {
        return Result.success(qaSetService.createTask(request));
    }

    @PostMapping("/set/create")
    public SseEmitter qaSetCreate(@RequestBody @Valid CreateQaSetRequest request) {
        SseEmitter emitter = new SseEmitter(600000L);
        emitter.onTimeout(emitter::complete);
        emitter.onError(throwable -> {
            qaSetService.abortTask(request.getTaskId(), contextUtil.getUserId());
            emitter.completeWithError(throwable);
        });

        log.info("【路由追踪】Controller 收到 /qa/set/create 请求: taskId={}", request.getTaskId());
        Consumer<SseEvent> sseEventHandler = new SseEventHandler(emitter);
        qaSetService.createQaSet(request, sseEventHandler);

        return emitter;
    }

    @PostMapping("/set/abort")
    public Result<Void> qaSetAbort(@RequestBody @Valid AbortTaskRequest request) {
        qaSetService.abortTask(request.getTaskId(), contextUtil.getUserId());
        return Result.success();
    }

    @GetMapping("/set/task-status")
    public Result<TaskStatusResponse> taskStatus(@RequestParam("taskId") String taskId) {
        return Result.success(qaSetService.getTaskStatus(taskId));
    }

    @GetMapping("/set/task-messages")
    public Result<List<TaskMessageResponse>> taskMessages(@RequestParam("taskId") String taskId) {
        return Result.success(qaSetService.getTaskMessages(taskId));
    }

    @GetMapping("/set/task-list")
    public Result<List<TaskListItemResponse>> taskList() {
        return Result.success(qaSetService.getTaskList());
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

    @PostMapping("/item/create/single")
    public Result<QaItemResponse> qaItemCreateSingle(@RequestBody @Valid CreateQaItemSingleRequest request) {
        return Result.success(qaItemService.createQaItem(request));
    }

    @PostMapping("/item/create/batch")
    public Result<List<QaItemResponse>> qaItemCreateBatch(@RequestBody @Valid CreateQaItemBatchRequest request) {
        return Result.success(qaItemService.createQaItems(request));
    }

    @PostMapping("/item/complete")
    public Result<QaItemResponse> qaItemComplete(@RequestBody @Valid QaItemCompleteRequest request) {
        return Result.success(qaItemService.completeQaItem(request));
    }

    @PostMapping("/item/delete")
    public Result<Void> qaItemDelete(@RequestBody QaItemRequest request) {
        qaItemService.deleteQaItem(request.getId());
        return Result.success();
    }

}
