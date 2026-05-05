package com.dasi.qa.agent.interfaces.controller;

import com.alibaba.fastjson2.JSON;
import com.dasi.qa.agent.domain.document.service.crud.IDocumentCrudService;
import com.dasi.qa.agent.domain.document.service.rag.index.IIndexService;
import com.dasi.qa.agent.domain.document.service.rag.search.ISearchService;
import com.dasi.qa.agent.domain.util.IMqUtil;
import com.dasi.qa.agent.domain.util.IContextUtil;
import com.dasi.qa.agent.types.dto.request.document.DocumentChunkRequest;
import com.dasi.qa.agent.types.dto.request.document.SearchRequest;
import com.dasi.qa.agent.types.dto.request.document.SourceDocumentRequest;
import com.dasi.qa.agent.types.dto.response.document.DocumentChunkResponse;
import com.dasi.qa.agent.types.dto.response.document.SearchResult;
import static com.dasi.qa.agent.types.constant.SystemConstant.INDEX_JOB_ID_PREFIX;

import com.dasi.qa.agent.types.dto.response.document.SourceDocumentResponse;
import com.dasi.qa.agent.types.result.Result;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/qa-agent/api/v1")
public class DocumentController {

    private final IDocumentCrudService documentService;
    private final ISearchService searchService;
    private final IIndexService indexService;
    private final IContextUtil contextUtil;
    private final IMqUtil mqUtil;
    private final String indexingTopic;

    public DocumentController(IDocumentCrudService documentService,
                              ISearchService searchService,
                              IIndexService indexService,
                              IContextUtil contextUtil,
                              IMqUtil mqUtil,
                              @Value("${qa-agent.kafka.topic-document-indexing}") String indexingTopic) {
        this.documentService = documentService;
        this.searchService = searchService;
        this.indexService = indexService;
        this.contextUtil = contextUtil;
        this.mqUtil = mqUtil;
        this.indexingTopic = indexingTopic;
    }

    // ======================== source-document CRUD ========================

    @GetMapping("/source-document/detail")
    public Result<SourceDocumentResponse> sourceDocumentDetail(@RequestParam("id") String id) {
        return Result.success(documentService.detailSourceDocument(id));
    }

    @PostMapping("/source-document/query")
    public Result<List<SourceDocumentResponse>> sourceDocumentQuery(@RequestBody SourceDocumentRequest request) {
        return Result.success(documentService.querySourceDocument(request));
    }

    @PostMapping("/source-document/upload")
    public Result<SourceDocumentResponse> sourceDocumentUpload(@RequestBody SourceDocumentRequest request) {
        SourceDocumentResponse response = documentService.createSourceDocument(request);
        String jobId = INDEX_JOB_ID_PREFIX + response.getId();
        String content = JSON.toJSONString(Map.of("documentId", response.getId()));
        mqUtil.send(indexingTopic, jobId, content);
        return Result.success(response);
    }

    @PostMapping("/source-document/update")
    public Result<SourceDocumentResponse> sourceDocumentUpdate(@RequestBody SourceDocumentRequest request) {
        SourceDocumentResponse response = documentService.updateSourceDocument(request);
        String jobId = INDEX_JOB_ID_PREFIX + request.getId();
        String content = JSON.toJSONString(Map.of("documentId", request.getId()));
        mqUtil.send(indexingTopic, jobId, content);
        return Result.success(response);
    }

    @PostMapping("/source-document/delete")
    public Result<Void> sourceDocumentDelete(@RequestBody SourceDocumentRequest request) {
        documentService.deleteSourceDocument(request.getId());
        return Result.success();
    }

    // ======================== document-chunk CRUD ========================

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

    // ======================== V2 RAG endpoints ========================

    @PostMapping("/source-document/search")
    public Result<List<SearchResult>> sourceDocumentSearch(@RequestBody SearchRequest request) {
        request.setUserId(contextUtil.getUserId());
        return Result.success(searchService.execute(request));
    }

    @PostMapping("/source-document/reindex")
    public Result<Void> sourceDocumentReindex(@RequestBody SourceDocumentRequest request) {
        indexService.index(request.getId());
        return Result.success();
    }

    @GetMapping("/source-document/chunks")
    public Result<List<DocumentChunkResponse>> sourceDocumentChunks(@RequestParam("documentId") String documentId) {
        DocumentChunkRequest query = new DocumentChunkRequest();
        query.setDocumentId(documentId);
        return Result.success(documentService.queryDocumentChunk(query));
    }
}
