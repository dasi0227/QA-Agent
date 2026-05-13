package com.dasi.qa.agent.domain.document.service.rag.index;

import lombok.extern.slf4j.Slf4j;

import com.dasi.qa.agent.domain.document.model.ChunkDraft;
import com.dasi.qa.agent.domain.document.model.ChunkSearchRow;
import com.dasi.qa.agent.domain.document.repository.IDocumentRepository;
import com.dasi.qa.agent.domain.document.service.rag.dashscope.IDashScopeService;
import com.dasi.qa.agent.types.dto.request.document.SourceDocumentRequest;
import com.dasi.qa.agent.types.dto.response.document.SourceDocumentResponse;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 资料索引服务，执行切片、向量化并同步写入 MySQL 和 PostgreSQL 检索引擎。
 */
@Service
@Slf4j
public class IndexService implements IIndexService {

    private final IDocumentRepository documentRepository;
    private final MarkdownChunker chunker;
    private final IDashScopeService IDashScopeService;

    public IndexService(IDocumentRepository documentRepository,
                        MarkdownChunker chunker,
                        IDashScopeService IDashScopeService) {
        this.documentRepository = documentRepository;
        this.chunker = chunker;
        this.IDashScopeService = IDashScopeService;
    }

    @Override
    public void index(String documentId) {
        String userId = documentRepository.getDocumentUserId(documentId);

        SourceDocumentRequest query = new SourceDocumentRequest();
        query.setId(documentId);
        List<SourceDocumentResponse> docs = documentRepository.querySourceDocument(query, userId);
        if (docs.isEmpty()) {
            log.warn("Document {} not found, skip indexing", documentId);
            return;
        }
        String rawContent = docs.get(0).getRawContent();
        if (rawContent == null || rawContent.isBlank()) {
            log.warn("Document {} has no raw content, skip indexing", documentId);
            return;
        }

        log.info("Indexing document: {}, userId: {}", documentId, userId);

        List<ChunkDraft> drafts = chunker.chunk(rawContent);
        log.info("Chunked document {} into {} drafts", documentId, drafts.size());

        for (ChunkDraft draft : drafts) {
            draft.setChunkId(UUID.randomUUID().toString());
        }

        List<String> contents = drafts.stream().map(ChunkDraft::getContent).toList();
        List<float[]> embeddings = IDashScopeService.embed(contents);

        documentRepository.replaceDocumentChunks(documentId, userId, drafts);

        List<ChunkSearchRow> searchRows = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            ChunkDraft draft = drafts.get(i);
            ChunkSearchRow row = new ChunkSearchRow();
            row.setChunkId(draft.getChunkId());
            row.setDocumentId(documentId);
            row.setUserId(userId);
            row.setChunkIndex(draft.getChunkIndex());
            row.setTitlePath(draft.getTitlePath());
            row.setContent(draft.getContent());
            row.setSummary(draft.getSummary());
            row.setModuleTags(draft.getModuleTags());
            row.setEmbedding(embeddings.get(i));
            searchRows.add(row);
        }

        documentRepository.deleteChunkSearchByDocumentId(documentId);
        documentRepository.batchInsertChunkSearch(searchRows);

        log.info("Indexed document {}: {} chunks written", documentId, searchRows.size());
    }

    @Override
    public void remove(String documentId) {
        documentRepository.deleteDocumentChunksByDocumentId(documentId);
        documentRepository.deleteChunkSearchByDocumentId(documentId);
        log.info("Removed index for document {}", documentId);
    }
}
