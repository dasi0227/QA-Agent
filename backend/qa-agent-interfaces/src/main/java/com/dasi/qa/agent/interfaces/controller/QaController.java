package com.dasi.qa.agent.interfaces.controller;

import com.dasi.qa.agent.domain.qa.service.crud.IQaCrudService;
import com.dasi.qa.agent.types.dto.request.qa.QaItemRequest;
import com.dasi.qa.agent.types.dto.request.qa.QaSetRequest;
import com.dasi.qa.agent.types.dto.response.qa.QaItemResponse;
import com.dasi.qa.agent.types.dto.response.qa.QaSetResponse;
import com.dasi.qa.agent.types.result.Result;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/qa-agent/api/v1")
public class QaController {

    private final IQaCrudService qaService;

    public QaController(IQaCrudService qaService) {
        this.qaService = qaService;
    }

    @GetMapping("/qa-set/detail")
    public Result<QaSetResponse> qaSetDetail(@RequestParam("id") String id) {
        return Result.success(qaService.detailQaSet(id));
    }

    @PostMapping("/qa-set/query")
    public Result<List<QaSetResponse>> qaSetQuery(@RequestBody QaSetRequest request) {
        return Result.success(qaService.queryQaSet(request));
    }

    @PostMapping("/qa-set/update")
    public Result<QaSetResponse> qaSetUpdate(@RequestBody QaSetRequest request) {
        return Result.success(qaService.updateQaSet(request));
    }

    @PostMapping("/qa-set/delete")
    public Result<Void> qaSetDelete(@RequestBody QaSetRequest request) {
        qaService.deleteQaSet(request.getId());
        return Result.success();
    }

    @GetMapping("/qa-item/detail")
    public Result<QaItemResponse> qaItemDetail(@RequestParam("id") String id) {
        return Result.success(qaService.detailQaItem(id));
    }

    @PostMapping("/qa-item/query")
    public Result<List<QaItemResponse>> qaItemQuery(@RequestBody QaItemRequest request) {
        return Result.success(qaService.queryQaItem(request));
    }

    @PostMapping("/qa-item/create")
    public Result<QaItemResponse> qaItemCreate(@RequestBody QaItemRequest request) {
        return Result.success(qaService.createQaItem(request));
    }

    @PostMapping("/qa-item/update")
    public Result<QaItemResponse> qaItemUpdate(@RequestBody QaItemRequest request) {
        return Result.success(qaService.updateQaItem(request));
    }

    @PostMapping("/qa-item/delete")
    public Result<Void> qaItemDelete(@RequestBody QaItemRequest request) {
        qaService.deleteQaItem(request.getId());
        return Result.success();
    }
}
