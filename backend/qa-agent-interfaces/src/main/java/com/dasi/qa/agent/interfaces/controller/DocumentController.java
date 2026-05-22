package com.dasi.qa.agent.interfaces.controller;

import com.dasi.qa.agent.domain.document.service.crud.IDocumentCrudService;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.domain.util.IMqUtil;
import com.dasi.qa.agent.types.dto.request.document.SourceDocumentRequest;
import com.dasi.qa.agent.types.dto.response.document.DocumentChunkResponse;
import com.dasi.qa.agent.types.dto.response.document.SourceDocumentResponse;
import com.dasi.qa.agent.types.result.Result;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/document")
public class DocumentController {

    private final IDocumentCrudService documentService;
    private final IMqUtil mqUtil;
    private final IContextUtil contextUtil;

    public DocumentController(IDocumentCrudService documentService,
                              IMqUtil mqUtil,
                              IContextUtil contextUtil) {
        this.documentService = documentService;
        this.mqUtil = mqUtil;
        this.contextUtil = contextUtil;
    }

    // ======================== source-document CRUD ========================

    @GetMapping("/source/detail")
    public Result<SourceDocumentResponse> sourceDocumentDetail(@RequestParam("id") String id) {
        return Result.success(documentService.detailSourceDocument(id));
    }

    @PostMapping("/source/query")
    public Result<List<SourceDocumentResponse>> sourceDocumentQuery(@RequestBody SourceDocumentRequest request) {
        return Result.success(documentService.querySourceDocument(request));
    }

    @PostMapping("/source/upload")
    public Result<SourceDocumentResponse> sourceDocumentUpload(@RequestBody SourceDocumentRequest request) {
        SourceDocumentResponse response = documentService.createSourceDocument(request);
        mqUtil.sendIndexMessage(response.getId(), Map.of("documentId", response.getId(), "userId", contextUtil.getUserId()));
        return Result.success(response);
    }

    @PostMapping("/source/update")
    public Result<SourceDocumentResponse> sourceDocumentUpdate(@RequestBody SourceDocumentRequest request) {
        return Result.success(documentService.updateSourceDocument(request));
    }

    @PostMapping("/source/delete")
    public Result<Void> sourceDocumentDelete(@RequestBody SourceDocumentRequest request) {
        documentService.deleteSourceDocument(request.getId());
        return Result.success();
    }

    // ======================== chunk query ========================

    @PostMapping("/chunk/query")
    public Result<List<DocumentChunkResponse>> chunkBatchQuery(@RequestBody List<String> chunkIds) {
        return Result.success(documentService.batchQueryDocumentChunk(chunkIds));
    }
}
