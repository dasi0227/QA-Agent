package com.dasi.qa.agent.domain.document.service.rag.dashscope;

import com.dasi.qa.agent.types.dto.response.document.SearchResult;

import java.util.List;

public interface IDashScopeService {

    List<float[]> embed(List<String> texts);

    List<SearchResult> rerank(String query, List<SearchResult> candidates);
}
