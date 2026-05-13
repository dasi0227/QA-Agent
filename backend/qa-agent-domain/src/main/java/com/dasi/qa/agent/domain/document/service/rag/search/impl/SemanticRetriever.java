package com.dasi.qa.agent.domain.document.service.rag.search.impl;

import com.dasi.qa.agent.domain.document.model.ChunkSearchRow;
import com.dasi.qa.agent.domain.document.repository.IDocumentRepository;
import com.dasi.qa.agent.domain.document.service.rag.search.IRetriever;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 语义检索器，基于向量余弦距离在 HNSW 索引上做近似最近邻检索。
 */
@Component
public class SemanticRetriever implements IRetriever {

    private final IDocumentRepository documentRepository;

    public SemanticRetriever(IDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    public List<ChunkSearchRow> search(RetrieveContext ctx) {
        return documentRepository.semanticSearch(
                ctx.getQueryVector(),
                ctx.getUserId(),
                ctx.getFilterDocumentIds(),
                ctx.getTopK()
        );
    }
}
