package com.dasi.qa.agent.domain.document.service.rag.retrieval.impl;

import com.dasi.qa.agent.domain.document.model.ChunkSearchRow;
import com.dasi.qa.agent.domain.document.service.rag.retrieval.IRetriever;
import com.dasi.qa.agent.domain.document.service.rag.retrieval.RetrieveContext;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class HybridRetriever implements IRetriever {

    private static final int RRF_K = 60;

    private final SemanticRetriever semanticRetriever;
    private final KeywordRetriever keywordRetriever;

    public HybridRetriever(SemanticRetriever semanticRetriever,
                           KeywordRetriever keywordRetriever) {
        this.semanticRetriever = semanticRetriever;
        this.keywordRetriever = keywordRetriever;
    }

    @Override
    public List<ChunkSearchRow> search(RetrieveContext ctx) {
        List<ChunkSearchRow> semanticResults = semanticRetriever.search(ctx);
        List<ChunkSearchRow> keywordResults = keywordRetriever.search(ctx);
        return rrfFuse(semanticResults, keywordResults, ctx.getTopK());
    }

    private List<ChunkSearchRow> rrfFuse(List<ChunkSearchRow> semantic,
                                          List<ChunkSearchRow> keyword, int topK) {
        Map<String, ChunkSearchRow> chunkMap = new HashMap<>();
        Map<String, Double> rrfScore = new HashMap<>();

        for (int i = 0; i < semantic.size(); i++) {
            ChunkSearchRow row = semantic.get(i);
            chunkMap.put(row.getChunkId(), row);
            double score = 1.0 / (RRF_K + i + 1);
            rrfScore.merge(row.getChunkId(), score, Double::sum);
            row.setVectorScore((float) (semantic.size() - i) / semantic.size());
        }

        for (int i = 0; i < keyword.size(); i++) {
            ChunkSearchRow row = keyword.get(i);
            chunkMap.putIfAbsent(row.getChunkId(), row);
            double score = 1.0 / (RRF_K + i + 1);
            rrfScore.merge(row.getChunkId(), score, Double::sum);
            row.setKeywordScore((float) (keyword.size() - i) / keyword.size());
        }

        return chunkMap.values().stream()
                .sorted(Comparator.comparingDouble(
                        (ChunkSearchRow r) -> rrfScore.getOrDefault(r.getChunkId(), 0.0)).reversed())
                .limit(topK)
                .peek(r -> r.setVectorScore((float) (double) rrfScore.getOrDefault(r.getChunkId(), 0.0)))
                .toList();
    }
}
