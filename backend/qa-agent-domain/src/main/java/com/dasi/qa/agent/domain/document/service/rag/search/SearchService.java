package com.dasi.qa.agent.domain.document.service.rag.search;

import com.dasi.qa.agent.domain.document.adapter.ISemanticAdapter;
import com.dasi.qa.agent.domain.document.model.ChunkSearchRow;
import com.dasi.qa.agent.domain.document.model.enumeration.SearchStrategy;
import com.dasi.qa.agent.domain.agent.model.enumeration.AgentType;
import com.dasi.qa.agent.domain.document.service.rag.retrieval.impl.HybridRetriever;
import com.dasi.qa.agent.domain.document.service.rag.retrieval.impl.KeywordRetriever;
import com.dasi.qa.agent.domain.document.service.rag.retrieval.RetrieveContext;
import com.dasi.qa.agent.domain.document.service.rag.retrieval.impl.SemanticRetriever;
import com.dasi.qa.agent.types.dto.request.document.SearchRequest;
import com.dasi.qa.agent.types.dto.response.document.SearchResult;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class SearchService implements ISearchService {

    private final ISemanticAdapter semanticAdapter;
    private final SemanticRetriever semanticRetriever;
    private final KeywordRetriever keywordRetriever;
    private final HybridRetriever hybridRetriever;
    private final EvidenceClipper evidenceClipper;

    public SearchService(ISemanticAdapter semanticAdapter,
                         SemanticRetriever semanticRetriever,
                         KeywordRetriever keywordRetriever,
                         HybridRetriever hybridRetriever,
                         EvidenceClipper evidenceClipper) {
        this.semanticAdapter = semanticAdapter;
        this.semanticRetriever = semanticRetriever;
        this.keywordRetriever = keywordRetriever;
        this.hybridRetriever = hybridRetriever;
        this.evidenceClipper = evidenceClipper;
    }

    @Override
    public List<SearchResult> execute(SearchRequest request) {
        SearchStrategy strategy = SearchStrategy.fromValue(request.getStrategy());
        String queryText = semanticAdapter.rewriteQuery(request.getQueryText());
        int topK = request.getTopK() > 0 ? request.getTopK() : 10;
        AgentType agentType = request.getAgentType() == null || request.getAgentType().isBlank()
                ? null
                : AgentType.fromValue(request.getAgentType());

        float[] queryVector = null;
        if (strategy == SearchStrategy.SEMANTIC || strategy == SearchStrategy.HYBRID) {
            queryVector = semanticAdapter.embed(List.of(queryText)).get(0);
        }

        RetrieveContext ctx = new RetrieveContext();
        ctx.setQueryText(queryText);
        ctx.setQueryVector(queryVector);
        ctx.setUserId(request.getUserId());
        ctx.setFilterDocumentIds(request.getFilterDocumentIds());
        ctx.setFilterModuleTags(request.getFilterModuleTags());
        ctx.setFilterTitlePathPrefix(request.getFilterTitlePathPrefix());
        ctx.setTopK(topK * 2);

        List<ChunkSearchRow> rows = switch (strategy) {
            case SEMANTIC -> semanticRetriever.search(ctx);
            case KEYWORD -> keywordRetriever.search(ctx);
            case HYBRID -> hybridRetriever.search(ctx);
        };

        List<SearchResult> results = new ArrayList<>();
        for (ChunkSearchRow row : rows) {
            results.add(SearchResult.builder()
                    .chunkId(row.getChunkId())
                    .documentId(row.getDocumentId())
                    .titlePath(row.getTitlePath())
                    .content(row.getContent())
                    .summary(row.getSummary())
                    .moduleTags(row.getModuleTags())
                    .vectorScore(row.getVectorScore())
                    .keywordScore(row.getKeywordScore())
                    .source(strategy.name())
                    .build());
        }

        results = semanticAdapter.rerank(queryText, results);
        results = evidenceClipper.clip(results, agentType);

        if (results.size() > topK) {
            results = results.subList(0, topK);
        }

        return results;
    }
}
