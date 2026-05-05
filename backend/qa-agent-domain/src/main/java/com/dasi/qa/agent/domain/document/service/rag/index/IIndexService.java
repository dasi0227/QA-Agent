package com.dasi.qa.agent.domain.document.service.rag.index;

public interface IIndexService {

    void index(String documentId);

    void remove(String documentId);
}
