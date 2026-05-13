package com.dasi.qa.agent.domain.document.service.rag.dashscope;

import com.alibaba.dashscope.embeddings.TextEmbedding;
import com.alibaba.dashscope.embeddings.TextEmbeddingOutput;
import com.alibaba.dashscope.embeddings.TextEmbeddingParam;
import com.alibaba.dashscope.embeddings.TextEmbeddingResult;
import com.alibaba.dashscope.embeddings.TextEmbeddingResultItem;
import com.alibaba.dashscope.rerank.TextReRank;
import com.alibaba.dashscope.rerank.TextReRankOutput;
import com.alibaba.dashscope.rerank.TextReRankParam;
import com.alibaba.dashscope.rerank.TextReRankResult;
import com.dasi.qa.agent.types.dto.response.document.SearchResult;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.result.ResultCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Slf4j
@Service
public class DashScopeService implements IDashScopeService {

    private static final int EMBED_BATCH_SIZE = 25;
    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = {1000, 2000, 4000};
    private static final int RERANK_LIMIT = 20;

    private final String apiKey;
    private final String embeddingModel;
    private final String rerankModel;

    public DashScopeService(
            @Value("${qa-agent.dashscope.api-key}") String apiKey,
            @Value("${qa-agent.dashscope.embedding-model}") String embeddingModel,
            @Value("${qa-agent.dashscope.rerank-model}") String rerankModel) {
        this.apiKey = apiKey;
        this.embeddingModel = embeddingModel;
        this.rerankModel = rerankModel;
    }

    // ======================== embed ========================

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }
        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += EMBED_BATCH_SIZE) {
            batches.add(texts.subList(i, Math.min(i + EMBED_BATCH_SIZE, texts.size())));
        }
        List<float[]> results = new ArrayList<>();
        for (List<String> batch : batches) {
            results.addAll(embedBatch(batch));
        }
        return results;
    }

    private List<float[]> embedBatch(List<String> texts) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                TextEmbeddingParam param = TextEmbeddingParam.builder()
                        .apiKey(apiKey)
                        .model(embeddingModel)
                        .texts(texts)
                        .build();
                TextEmbeddingResult result = new TextEmbedding().call(param);
                TextEmbeddingOutput output = result.getOutput();
                if (output == null || output.getEmbeddings() == null) {
                    throw new ApiException(ResultCode.INTERNAL_ERROR.getCode(), "Embedding returned empty output");
                }
                List<float[]> vectors = new ArrayList<>();
                for (TextEmbeddingResultItem item : output.getEmbeddings()) {
                    List<Double> embedding = item.getEmbedding();
                    float[] vector = new float[embedding.size()];
                    for (int i = 0; i < embedding.size(); i++) {
                        vector[i] = embedding.get(i).floatValue();
                    }
                    vectors.add(vector);
                }
                return vectors;
            } catch (ApiException e) {
                throw e;
            } catch (Exception e) {
                if (attempt < MAX_RETRIES - 1) {
                    long delay = RETRY_DELAYS_MS[attempt];
                    log.warn("【嵌入向量】第 {} 次尝试失败，过 {}ms 重试: {}", attempt + 1, delay, e.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ApiException(ResultCode.INTERNAL_ERROR.getCode(), "Embedding interrupted");
                    }
                } else {
                    log.error("【嵌入向量】经过 {} 次尝试后失败，返回原排序", MAX_RETRIES, e);
                    throw new ApiException(ResultCode.INTERNAL_ERROR.getCode(), "Embedding failed: " + e.getMessage());
                }
            }
        }
        throw new ApiException(ResultCode.INTERNAL_ERROR.getCode(), "Embedding failed");
    }

    // ======================== rerank ========================

    @Override
    public List<SearchResult> rerank(String query, List<SearchResult> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<SearchResult> topCandidates = candidates.size() > RERANK_LIMIT
                ? new ArrayList<>(candidates.subList(0, RERANK_LIMIT))
                : new ArrayList<>(candidates);

        try {
            List<String> documents = topCandidates.stream().map(SearchResult::getContent).toList();
            TextReRankParam param = TextReRankParam.builder()
                    .apiKey(apiKey)
                    .model(rerankModel)
                    .query(query)
                    .documents(documents)
                    .build();
            TextReRankResult result = new TextReRank().call(param);
            List<TextReRankOutput.Result> rerankResults = result.getOutput().getResults();
            if (rerankResults != null) {
                for (TextReRankOutput.Result r : rerankResults) {
                    if (r.getIndex() < topCandidates.size()) {
                        topCandidates.get(r.getIndex()).setScore(r.getRelevanceScore().floatValue());
                    }
                }
                topCandidates.sort(Comparator.comparingDouble(SearchResult::getScore).reversed());
            }
            if (candidates.size() > RERANK_LIMIT) {
                topCandidates.addAll(candidates.subList(RERANK_LIMIT, candidates.size()));
            }
            return topCandidates;
        } catch (Exception e) {
            log.error("【重排序】失败，返回原排序", e);
            return candidates;
        }
    }
}
