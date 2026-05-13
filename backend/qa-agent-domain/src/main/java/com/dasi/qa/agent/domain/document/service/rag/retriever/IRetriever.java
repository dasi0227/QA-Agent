package com.dasi.qa.agent.domain.document.service.rag.retriever;

import com.dasi.qa.agent.domain.document.model.ChunkSearchRow;
import com.dasi.qa.agent.domain.document.service.rag.retriever.impl.RetrieveContext;

import java.util.List;

public interface IRetriever {

    List<ChunkSearchRow> search(RetrieveContext ctx);
}
