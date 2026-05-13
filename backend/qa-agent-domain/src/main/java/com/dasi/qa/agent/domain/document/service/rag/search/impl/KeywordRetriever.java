package com.dasi.qa.agent.domain.document.service.rag.search.impl;

import com.dasi.qa.agent.domain.document.model.ChunkSearchRow;
import com.dasi.qa.agent.domain.document.repository.IDocumentRepository;
import com.dasi.qa.agent.domain.document.service.rag.search.IRetriever;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 关键词检索器，基于 PostgreSQL zhparser 中文分词和 ts_rank 做全文检索。
 */
@Component
public class KeywordRetriever implements IRetriever {

    private final IDocumentRepository documentRepository;

    public KeywordRetriever(IDocumentRepository documentRepository) {
        this.documentRepository = documentRepository;
    }

    @Override
    public List<ChunkSearchRow> search(RetrieveContext ctx) {
        return documentRepository.keywordSearch(
                ctx.getQueryText(),
                ctx.getUserId(),
                ctx.getFilterDocumentIds(),
                ctx.getTopK()
        );
    }
}
