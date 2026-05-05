package com.dasi.qa.agent.domain.document.service.rag.retrieval.impl;

import com.dasi.qa.agent.domain.document.model.ChunkSearchRow;
import com.dasi.qa.agent.domain.document.repository.IDocumentRepository;
import com.dasi.qa.agent.domain.document.service.rag.retrieval.IRetriever;
import com.dasi.qa.agent.domain.document.service.rag.retrieval.RetrieveContext;
import org.springframework.stereotype.Component;

import java.util.List;

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
                ctx.getFilterModuleTags(),
                ctx.getFilterTitlePathPrefix(),
                ctx.getTopK()
        );
    }
}
