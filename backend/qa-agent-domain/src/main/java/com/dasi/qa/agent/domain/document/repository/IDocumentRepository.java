package com.dasi.qa.agent.domain.document.repository;

import com.dasi.qa.agent.types.model.request.document.DocumentChunkRequest;
import com.dasi.qa.agent.types.model.request.document.SourceDocumentRequest;
import com.dasi.qa.agent.types.model.response.document.DocumentChunkResponse;
import com.dasi.qa.agent.types.model.response.document.SourceDocumentResponse;

import java.util.List;

public interface IDocumentRepository {

    SourceDocumentResponse detailSourceDocument(String id, String userId);

    List<SourceDocumentResponse> querySourceDocument(SourceDocumentRequest request, String userId);

    SourceDocumentResponse createSourceDocument(SourceDocumentRequest request, String userId);

    SourceDocumentResponse updateSourceDocument(SourceDocumentRequest request, String userId);

    void deleteSourceDocument(String id, String userId);

    DocumentChunkResponse detailDocumentChunk(String id, String userId);

    List<DocumentChunkResponse> queryDocumentChunk(DocumentChunkRequest request, String userId);

    DocumentChunkResponse createDocumentChunk(DocumentChunkRequest request, String userId);

    DocumentChunkResponse updateDocumentChunk(DocumentChunkRequest request, String userId);

    void deleteDocumentChunk(String id, String userId);
}
