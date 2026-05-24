package com.dasi.qa.agent.interfaces.controller;

import com.dasi.qa.agent.domain.memory.service.IMemoryService;
import com.dasi.qa.agent.types.dto.request.memory.MemoryHideRequest;
import com.dasi.qa.agent.types.dto.response.memory.UserMemoryDetailResponse;
import com.dasi.qa.agent.types.dto.response.memory.UserMemoryResponse;
import com.dasi.qa.agent.types.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/memory")
public class MemoryController {

    private final IMemoryService memoryService;

    public MemoryController(IMemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @GetMapping("/list")
    public Result<List<UserMemoryResponse>> list() {
        return Result.success(memoryService.list());
    }

    @GetMapping("/detail")
    public Result<UserMemoryDetailResponse> detail(@RequestParam("memoryId") String memoryId) {
        return Result.success(memoryService.detail(memoryId));
    }

    @PostMapping("/hide")
    public Result<Void> hide(@RequestBody @Valid MemoryHideRequest request) {
        memoryService.hide(request);
        return Result.success();
    }
}
