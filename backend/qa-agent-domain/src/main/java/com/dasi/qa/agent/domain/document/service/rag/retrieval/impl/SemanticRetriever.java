package com.dasi.qa.agent.domain.document.service.rag.retrieval.impl;

import com.dasi.qa.agent.domain.document.model.ChunkSearchRow;
import com.dasi.qa.agent.domain.document.repository.IDocumentRepository;
import com.dasi.qa.agent.domain.document.service.rag.retrieval.IRetriever;
import com.dasi.qa.agent.domain.document.service.rag.retrieval.RetrieveContext;
import org.springframework.stereotype.Component;

import java.util.List;

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
                ctx.getFilterModuleTags(),
                ctx.getFilterTitlePathPrefix(),
                ctx.getTopK()
        );
    }
}
