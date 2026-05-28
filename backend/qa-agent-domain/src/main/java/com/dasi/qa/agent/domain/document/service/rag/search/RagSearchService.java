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
    public List<SearchResult> execute(RagSearchRequest request) {

        String queryText = request.getQueryText();

        // 1. 将查询文本转换为向量，用于后续向量检索
        float[] queryVector = IDashScopeService.embed(List.of(queryText)).get(0);

        // 2. 执行混合检索，结合向量相似度和关键词匹配结果进行初步召回
        RetrieveContext retrieveContext = RetrieveContext.builder()
                .queryText(queryText)
                .queryVector(queryVector)
                .userId(request.getUserId())
                .filterDocumentIds(request.getFilterDocumentIds())
                .topK(topK * 2)
                .build();
        List<ChunkSearchRow> rows = hybridRetriever.search(retrieveContext);

        // 3. 将数据库检索结果转换为业务层返回对象，屏蔽底层存储结构
        List<SearchResult> results = rows.stream()
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

        // 4. 对初步召回结果进行语义重排序，使最终结果更贴近用户问题意图
        results = IDashScopeService.rerank(queryText, results);

        // 5. 按最终 topK 截断结果，避免返回过多低相关内容
        if (results.size() > topK) {
            results = results.subList(0, topK);
        }

        return results;
    }

}
