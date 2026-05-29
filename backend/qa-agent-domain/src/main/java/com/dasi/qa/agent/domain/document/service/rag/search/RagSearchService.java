package com.dasi.qa.agent.domain.document.service.rag.search;

import com.dasi.qa.agent.domain.document.service.rag.dashscope.IDashScopeService;
import com.dasi.qa.agent.domain.document.model.ChunkSearchRow;
import com.dasi.qa.agent.domain.document.service.rag.retriever.impl.HybridRetriever;
import com.dasi.qa.agent.domain.document.service.rag.retriever.impl.RetrieveContext;
import com.dasi.qa.agent.types.dto.request.document.RagSearchRequest;
import com.dasi.qa.agent.types.dto.response.document.SearchResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * RAG 检索服务，协调查询改写、向量化、检索、重排序的完整流程。
 */
@Slf4j
@Service
public class RagSearchService implements IRagSearchService {

    private static final int topK = 10;

    private final IDashScopeService IDashScopeService;
    private final HybridRetriever hybridRetriever;

    public RagSearchService(IDashScopeService IDashScopeService,
                            HybridRetriever hybridRetriever) {
        this.IDashScopeService = IDashScopeService;
        this.hybridRetriever = hybridRetriever;
    }

    @Override
    public List<SearchResult> search(RagSearchRequest request) {
        String queryText = request.getQueryText();
        float[] queryVector = IDashScopeService.embed(List.of(queryText)).get(0);

        RetrieveContext retrieveContext = RetrieveContext.builder()
                .queryText(queryText)
                .queryVector(queryVector)
                .userId(request.getUserId())
                .filterDocumentIds(request.getFilterDocumentIds())
                .topK(topK * 2)
                .build();
        List<ChunkSearchRow> rows = hybridRetriever.search(retrieveContext);

        return rows.stream()
                .map(row -> SearchResult.builder()
                        .chunkId(row.getChunkId())
                        .documentId(row.getDocumentId())
                        .headingPath(row.getHeadingPath())
                        .content(row.getContent())
                        .summary(row.getSummary())
                        .vectorScore(row.getVectorScore())
                        .keywordScore(row.getKeywordScore())
                        .build())
                .toList();
    }

    @Override
    public List<SearchResult> rerank(String queryText, List<SearchResult> results) {
        if (results.isEmpty()) {
            return results;
        }
        results = IDashScopeService.rerank(queryText, results);
        if (results.size() > topK) {
            results = results.subList(0, topK);
        }
        return results;
    }

}
