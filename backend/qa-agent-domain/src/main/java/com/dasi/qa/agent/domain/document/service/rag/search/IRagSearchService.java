package com.dasi.qa.agent.domain.document.service.rag.search;

import com.dasi.qa.agent.types.dto.request.document.RagSearchRequest;
import com.dasi.qa.agent.types.dto.response.document.SearchResult;

import java.util.List;

public interface IRagSearchService {

    List<SearchResult> execute(RagSearchRequest request);
}
