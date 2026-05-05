package com.dasi.qa.agent.domain.document.service.rag.search;

import com.dasi.qa.agent.types.dto.request.document.SearchRequest;
import com.dasi.qa.agent.types.dto.response.document.SearchResult;

import java.util.List;

public interface ISearchService {

    List<SearchResult> execute(SearchRequest request);
}
