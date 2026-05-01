package com.dasi.qa.agent.interfaces.controller;

import com.dasi.qa.agent.domain.practice.service.IPracticeService;
import com.dasi.qa.agent.types.model.request.practice.PracticeSessionItemRequest;
import com.dasi.qa.agent.types.model.request.practice.PracticeSessionRequest;
import com.dasi.qa.agent.types.model.response.practice.PracticeSessionItemResponse;
import com.dasi.qa.agent.types.model.response.practice.PracticeSessionResponse;
import com.dasi.qa.agent.types.result.Result;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
public class PracticeController {

    private final IPracticeService practiceService;

    public PracticeController(IPracticeService practiceService) {
        this.practiceService = practiceService;
    }

    @GetMapping("/practice-session/detail")
    public Result<PracticeSessionResponse> practiceSessionDetail(@RequestParam("id") String id) {
        return Result.success(practiceService.detailPracticeSession(id));
    }

    @PostMapping("/practice-session/query")
    public Result<List<PracticeSessionResponse>> practiceSessionQuery(@RequestBody PracticeSessionRequest request) {
        return Result.success(practiceService.queryPracticeSession(request));
    }

    @PostMapping("/practice-session/create")
    public Result<PracticeSessionResponse> practiceSessionCreate(@RequestBody PracticeSessionRequest request) {
        return Result.success(practiceService.createPracticeSession(request));
    }

    @PostMapping("/practice-session/update")
    public Result<PracticeSessionResponse> practiceSessionUpdate(@RequestBody PracticeSessionRequest request) {
        return Result.success(practiceService.updatePracticeSession(request));
    }

    @PostMapping("/practice-session/delete")
    public Result<Void> practiceSessionDelete(@RequestBody PracticeSessionRequest request) {
        practiceService.deletePracticeSession(request.getId());
        return Result.success();
    }

    @GetMapping("/practice-session-item/detail")
    public Result<PracticeSessionItemResponse> practiceSessionItemDetail(@RequestParam("id") String id) {
        return Result.success(practiceService.detailPracticeSessionItem(id));
    }

    @PostMapping("/practice-session-item/query")
    public Result<List<PracticeSessionItemResponse>> practiceSessionItemQuery(@RequestBody PracticeSessionItemRequest request) {
        return Result.success(practiceService.queryPracticeSessionItem(request));
    }

    @PostMapping("/practice-session-item/create")
    public Result<PracticeSessionItemResponse> practiceSessionItemCreate(@RequestBody PracticeSessionItemRequest request) {
        return Result.success(practiceService.createPracticeSessionItem(request));
    }

    @PostMapping("/practice-session-item/update")
    public Result<PracticeSessionItemResponse> practiceSessionItemUpdate(@RequestBody PracticeSessionItemRequest request) {
        return Result.success(practiceService.updatePracticeSessionItem(request));
    }

    @PostMapping("/practice-session-item/delete")
    public Result<Void> practiceSessionItemDelete(@RequestBody PracticeSessionItemRequest request) {
        practiceService.deletePracticeSessionItem(request.getId());
        return Result.success();
    }
}
