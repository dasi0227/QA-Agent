package com.dasi.qa.agent.domain.document.service.crud;

import com.dasi.qa.agent.types.dto.request.document.DocumentChunkRequest;
import com.dasi.qa.agent.types.dto.request.document.SourceDocumentRequest;
import com.dasi.qa.agent.types.dto.response.document.DocumentChunkResponse;
import com.dasi.qa.agent.types.dto.response.document.SourceDocumentResponse;

import java.util.List;

public interface IDocumentCrudService {

    SourceDocumentResponse detailSourceDocument(String id);

    List<SourceDocumentResponse> querySourceDocument(SourceDocumentRequest request);

    SourceDocumentResponse createSourceDocument(SourceDocumentRequest request);

    SourceDocumentResponse updateSourceDocument(SourceDocumentRequest request);

    void deleteSourceDocument(String id);

    DocumentChunkResponse detailDocumentChunk(String id);

    List<DocumentChunkResponse> queryDocumentChunk(DocumentChunkRequest request);

    DocumentChunkResponse createDocumentChunk(DocumentChunkRequest request);

    DocumentChunkResponse updateDocumentChunk(DocumentChunkRequest request);

    void deleteDocumentChunk(String id);

    List<DocumentChunkResponse> batchQueryDocumentChunk(List<String> chunkIds);
}
