package com.dasi.qa.agent.domain.document.service.rag.retrieval;

import com.dasi.qa.agent.domain.document.model.ChunkSearchRow;

import java.util.List;

public interface IRetriever {

    List<ChunkSearchRow> search(RetrieveContext ctx);
}
