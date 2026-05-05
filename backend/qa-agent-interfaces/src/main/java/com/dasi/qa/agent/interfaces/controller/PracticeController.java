package com.dasi.qa.agent.interfaces.controller;

import com.dasi.qa.agent.domain.practice.service.crud.IPracticeCrudService;
import com.dasi.qa.agent.types.dto.request.practice.PracticeSessionItemRequest;
import com.dasi.qa.agent.types.dto.request.practice.PracticeSessionRequest;
import com.dasi.qa.agent.types.dto.response.practice.PracticeSessionItemResponse;
import com.dasi.qa.agent.types.dto.response.practice.PracticeSessionResponse;
import com.dasi.qa.agent.types.result.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/practice")
public class PracticeController {

    private final IPracticeCrudService practiceService;

    public PracticeController(IPracticeCrudService practiceService) {
        this.practiceService = practiceService;
    }

    @GetMapping("/session/detail")
    public Result<PracticeSessionResponse> practiceSessionDetail(@RequestParam("id") String id) {
        return Result.success(practiceService.detailPracticeSession(id));
    }

    @PostMapping("/session/query")
    public Result<List<PracticeSessionResponse>> practiceSessionQuery(@RequestBody PracticeSessionRequest request) {
        return Result.success(practiceService.queryPracticeSession(request));
    }

    @PostMapping("/session/create")
    public Result<PracticeSessionResponse> practiceSessionCreate(@RequestBody PracticeSessionRequest request) {
        return Result.success(practiceService.createPracticeSession(request));
    }

    @PostMapping("/session/update")
    public Result<PracticeSessionResponse> practiceSessionUpdate(@RequestBody PracticeSessionRequest request) {
        return Result.success(practiceService.updatePracticeSession(request));
    }

    @PostMapping("/session/delete")
    public Result<Void> practiceSessionDelete(@RequestBody PracticeSessionRequest request) {
        practiceService.deletePracticeSession(request.getId());
        return Result.success();
    }

    @GetMapping("/session-item/detail")
    public Result<PracticeSessionItemResponse> practiceSessionItemDetail(@RequestParam("id") String id) {
        return Result.success(practiceService.detailPracticeSessionItem(id));
    }

    @PostMapping("/session-item/query")
    public Result<List<PracticeSessionItemResponse>> practiceSessionItemQuery(@RequestBody PracticeSessionItemRequest request) {
        return Result.success(practiceService.queryPracticeSessionItem(request));
    }

    @PostMapping("/session-item/create")
    public Result<PracticeSessionItemResponse> practiceSessionItemCreate(@RequestBody PracticeSessionItemRequest request) {
        return Result.success(practiceService.createPracticeSessionItem(request));
    }

    @PostMapping("/session-item/update")
    public Result<PracticeSessionItemResponse> practiceSessionItemUpdate(@RequestBody PracticeSessionItemRequest request) {
        return Result.success(practiceService.updatePracticeSessionItem(request));
    }

    @PostMapping("/session-item/delete")
    public Result<Void> practiceSessionItemDelete(@RequestBody PracticeSessionItemRequest request) {
        practiceService.deletePracticeSessionItem(request.getId());
        return Result.success();
    }
}
