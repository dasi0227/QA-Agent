package com.dasi.qa.agent.domain.document.repository;

import com.dasi.qa.agent.domain.document.model.ChunkDraft;
import com.dasi.qa.agent.domain.document.model.ChunkSearchRow;
import com.dasi.qa.agent.types.dto.request.document.DocumentChunkRequest;
import com.dasi.qa.agent.types.dto.request.document.SourceDocumentRequest;
import com.dasi.qa.agent.types.dto.response.document.DocumentChunkResponse;
import com.dasi.qa.agent.types.dto.response.document.SourceDocumentResponse;

import java.util.List;

public interface IDocumentRepository {

    SourceDocumentResponse detailSourceDocument(String id, String userId);

    List<SourceDocumentResponse> querySourceDocument(SourceDocumentRequest request, String userId);

    boolean existsSourceDocumentByFileName(String fileName, String userId);

    SourceDocumentResponse createSourceDocument(SourceDocumentRequest request, String userId);

    SourceDocumentResponse updateSourceDocument(SourceDocumentRequest request, String userId);

    void deleteSourceDocument(String id, String userId);

    DocumentChunkResponse detailDocumentChunk(String id, String userId);

    List<DocumentChunkResponse> queryDocumentChunk(DocumentChunkRequest request, String userId);

    DocumentChunkResponse createDocumentChunk(DocumentChunkRequest request, String userId);

    DocumentChunkResponse updateDocumentChunk(DocumentChunkRequest request, String userId);

    void deleteDocumentChunk(String id, String userId);

    // -- V2 RAG: MySQL document_chunk batch operations --

    void replaceDocumentChunks(String documentId, String userId, List<ChunkDraft> drafts);

    List<String> getChunkIdsByDocumentId(String documentId);

    void deleteDocumentChunksByDocumentId(String documentId);

    List<DocumentChunkResponse> batchQueryDocumentChunk(List<String> chunkIds);

    void updateIndexStatus(String documentId, String userId, String indexStatus);

    List<SourceDocumentResponse> listFinishedDocuments(String userId);

    String getDocumentUserId(String documentId);

    // -- V2 RAG: PostgreSQL chunk_search operations --

    void batchInsertChunkSearch(List<ChunkSearchRow> rows);

    void deleteChunkSearchByDocumentId(String documentId);

    List<ChunkSearchRow> semanticSearch(float[] queryVector, String userId,
            List<String> docIds, int limit);

    List<ChunkSearchRow> keywordSearch(String queryText, String userId,
            List<String> docIds, int limit);
}
