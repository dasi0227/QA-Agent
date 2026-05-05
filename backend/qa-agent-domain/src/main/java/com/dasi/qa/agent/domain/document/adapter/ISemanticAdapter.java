package com.dasi.qa.agent.domain.document.adapter;

import com.dasi.qa.agent.types.model.response.document.SearchResult;

import java.util.List;

public interface ISemanticAdapter {

    List<float[]> embed(List<String> texts);

    List<SearchResult> rerank(String query, List<SearchResult> candidates);
}
