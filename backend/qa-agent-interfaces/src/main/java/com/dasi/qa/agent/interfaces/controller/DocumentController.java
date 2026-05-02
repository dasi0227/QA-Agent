package com.dasi.qa.agent.interfaces.controller;

import com.dasi.qa.agent.domain.document.service.crud.IDocumentCrudService;
import com.dasi.qa.agent.types.model.request.document.DocumentChunkRequest;
import com.dasi.qa.agent.types.model.request.document.SourceDocumentRequest;
import com.dasi.qa.agent.types.model.response.document.DocumentChunkResponse;
import com.dasi.qa.agent.types.model.response.document.SourceDocumentResponse;
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
public class DocumentController {

    private final IDocumentCrudService documentService;

    public DocumentController(IDocumentCrudService documentService) {
        this.documentService = documentService;
    }

    @GetMapping("/source-document/detail")
    public Result<SourceDocumentResponse> sourceDocumentDetail(@RequestParam("id") String id) {
        return Result.success(documentService.detailSourceDocument(id));
    }

    @PostMapping("/source-document/query")
    public Result<List<SourceDocumentResponse>> sourceDocumentQuery(@RequestBody SourceDocumentRequest request) {
        return Result.success(documentService.querySourceDocument(request));
    }

    @PostMapping("/source-document/update")
    public Result<SourceDocumentResponse> sourceDocumentUpdate(@RequestBody SourceDocumentRequest request) {
        return Result.success(documentService.updateSourceDocument(request));
    }

    @PostMapping("/source-document/delete")
    public Result<Void> sourceDocumentDelete(@RequestBody SourceDocumentRequest request) {
        documentService.deleteSourceDocument(request.getId());
        return Result.success();
    }

    @GetMapping("/document-chunk/detail")
    public Result<DocumentChunkResponse> documentChunkDetail(@RequestParam("id") String id) {
        return Result.success(documentService.detailDocumentChunk(id));
    }

    @PostMapping("/document-chunk/query")
    public Result<List<DocumentChunkResponse>> documentChunkQuery(@RequestBody DocumentChunkRequest request) {
        return Result.success(documentService.queryDocumentChunk(request));
    }

    @PostMapping("/document-chunk/create")
    public Result<DocumentChunkResponse> documentChunkCreate(@RequestBody DocumentChunkRequest request) {
        return Result.success(documentService.createDocumentChunk(request));
    }

    @PostMapping("/document-chunk/update")
    public Result<DocumentChunkResponse> documentChunkUpdate(@RequestBody DocumentChunkRequest request) {
        return Result.success(documentService.updateDocumentChunk(request));
    }

    @PostMapping("/document-chunk/delete")
    public Result<Void> documentChunkDelete(@RequestBody DocumentChunkRequest request) {
        documentService.deleteDocumentChunk(request.getId());
        return Result.success();
    }
}
