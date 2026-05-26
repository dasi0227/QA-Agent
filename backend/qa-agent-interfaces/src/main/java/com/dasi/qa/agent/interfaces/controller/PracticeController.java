package com.dasi.qa.agent.interfaces.controller;

import com.dasi.qa.agent.domain.practice.service.crud.IPracticeCrudService;
import com.dasi.qa.agent.domain.practice.service.flow.IPracticeFlowService;
import com.dasi.qa.agent.types.dto.request.practice.*;
import com.dasi.qa.agent.types.dto.response.practice.PracticeItemResponse;
import com.dasi.qa.agent.types.dto.response.practice.PracticeStateResponse;
import com.dasi.qa.agent.types.dto.response.practice.PracticeDetailResponse;
import com.dasi.qa.agent.types.dto.response.practice.PracticeSessionResponse;
import com.dasi.qa.agent.types.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/practice")
public class PracticeController {

    private final IPracticeCrudService practiceService;
    private final IPracticeFlowService practiceFlowService;

    public PracticeController(IPracticeCrudService practiceService, IPracticeFlowService practiceFlowService) {
        this.practiceService = practiceService;
        this.practiceFlowService = practiceFlowService;
    }

    @PostMapping("/session/query")
    public Result<List<PracticeSessionResponse>> query(@RequestBody PracticeQueryRequest request) {
        return Result.success(practiceService.query(request));
    }

    // 新建练习
    @PostMapping("/session/init")
    public Result<PracticeDetailResponse> init(@RequestBody @Valid PracticeInitRequest request) {
        return Result.success(practiceFlowService.init(request));
    }

    // 重新开始练习
    @PostMapping("/session/restart")
    public Result<PracticeDetailResponse> restart(@RequestBody @Valid PracticeRestartRequest request) {
        return Result.success(practiceFlowService.restart(request));
    }

    // 抛弃练习
    @PostMapping("/session/abandon")
    public Result<Void> abandon(@RequestBody @Valid PracticeAbandonRequest request) {
        practiceFlowService.abandon(request);
        return Result.success();
    }

    // 判断是否还有未完成的练习
    @GetMapping("/session/exist")
    public Result<PracticeStateResponse> exist(@RequestParam("qaSetId") String qaSetId) {
        return Result.success(practiceFlowService.exist(qaSetId));
    }

    // 获取已完成练习历史
    @GetMapping("/session/history")
    public Result<List<PracticeSessionResponse>> history(@RequestParam("qaSetId") String qaSetId) {
        return Result.success(practiceFlowService.history(qaSetId));
    }

    // 获取练习信息
    @GetMapping("/session/detail")
    public Result<PracticeDetailResponse> detail(@RequestParam("sessionId") String sessionId) {
        return Result.success(practiceFlowService.detail(sessionId));
    }

    // 提交该轮练习
    @PostMapping("/session/submit")
    public Result<PracticeDetailResponse> submit(@RequestBody @Valid PracticeSubmitRequest request) {
        return Result.success(practiceFlowService.submit(request));
    }

    // 保存单题记录
    @PostMapping("/item/save")
    public Result<PracticeItemResponse> save(@RequestBody @Valid ItemSaveRequest request) {
        return Result.success(practiceFlowService.save(request));
    }

    // 单题不会
    @PostMapping("/item/unknown")
    public Result<PracticeItemResponse> unknown(@RequestBody @Valid ItemSaveRequest request) {
        return Result.success(practiceFlowService.unknown(request));
    }

    // 单题回答
    @PostMapping("/item/answer")
    public Result<PracticeItemResponse> answer(@RequestBody @Valid ItemSubmitRequest request) {
        return Result.success(practiceFlowService.answer(request));
    }

}
