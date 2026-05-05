package com.dasi.qa.agent.infrastructure.adapter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.qa.agent.domain.document.adapter.ISemanticAdapter;
import com.dasi.qa.agent.infrastructure.properties.DashScopeProperties;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.model.response.document.SearchResult;
import com.dasi.qa.agent.types.result.ResultCode;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
@Slf4j
public class DashScopeSemanticAdapter implements ISemanticAdapter {

    private static final MediaType JSON_MEDIA = MediaType.get("application/json; charset=utf-8");
    private static final int BATCH_SIZE = 25;
    private static final int MAX_RETRIES = 3;
    private static final long[] RETRY_DELAYS_MS = {1000, 2000, 4000};
    private static final int RERANK_LIMIT = 20;

    private final QwenEmbeddingModel embeddingModel;
    private final ThreadPoolTaskExecutor executor;
    private final OkHttpClient httpClient;
    private final String apiKey;
    private final String rerankModel;

    public DashScopeSemanticAdapter(QwenEmbeddingModel embeddingModel,
                                    @Qualifier("applicationTaskExecutor") ThreadPoolTaskExecutor executor,
                                    DashScopeProperties properties) {
        this.embeddingModel = embeddingModel;
        this.executor = executor;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(java.time.Duration.ofSeconds(10))
                .readTimeout(java.time.Duration.ofSeconds(60))
                .build();
        this.apiKey = properties.getApiKey();
        this.rerankModel = properties.getRerankModel();
    }

    // ======================== embed ========================

    @Override
    public List<float[]> embed(List<String> texts) {
        if (texts == null || texts.isEmpty()) {
            return List.of();
        }

        List<List<String>> batches = new ArrayList<>();
        for (int i = 0; i < texts.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, texts.size());
            batches.add(texts.subList(i, end));
        }

        List<CompletableFuture<List<float[]>>> futures = batches.stream()
                .map(batch -> CompletableFuture.supplyAsync(
                        () -> embedBatchWithRetry(batch), executor))
                .toList();

        List<float[]> results = new ArrayList<>();
        for (CompletableFuture<List<float[]>> future : futures) {
            try {
                results.addAll(future.get(120, TimeUnit.SECONDS));
            } catch (Exception e) {
                log.error("Embedding batch failed", e);
                throw new ApiException(ResultCode.INTERNAL_ERROR.getCode(), "Embedding failed: " + e.getMessage());
            }
        }
        return results;
    }

    private List<float[]> embedBatchWithRetry(List<String> texts) {
        List<TextSegment> segments = texts.stream().map(TextSegment::from).toList();

        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
                List<float[]> vectors = new ArrayList<>();
                for (Embedding embedding : embeddings) {
                    vectors.add(embedding.vector());
                }
                return vectors;
            } catch (Exception e) {
                if (attempt < MAX_RETRIES - 1) {
                    long delay = RETRY_DELAYS_MS[attempt];
                    log.warn("Embedding API attempt {} failed, retrying in {}ms: {}",
                            attempt + 1, delay, e.getMessage());
                    try {
                        Thread.sleep(delay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new ApiException(ResultCode.INTERNAL_ERROR.getCode(), "Embedding interrupted");
                    }
                } else {
                    log.error("Embedding API failed after {} attempts", MAX_RETRIES, e);
                    throw new ApiException(ResultCode.INTERNAL_ERROR.getCode(),
                            "Embedding failed after " + MAX_RETRIES + " attempts: " + e.getMessage());
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
            List<String> documents = topCandidates.stream()
                    .map(SearchResult::getContent).toList();
            List<Double> scores = callRerankApi(query, documents);

            for (int i = 0; i < topCandidates.size() && i < scores.size(); i++) {
                topCandidates.get(i).setScore(scores.get(i).floatValue());
            }
            topCandidates.sort(Comparator.comparingDouble(SearchResult::getScore).reversed());

            if (candidates.size() > RERANK_LIMIT) {
                topCandidates.addAll(candidates.subList(RERANK_LIMIT, candidates.size()));
            }
            return topCandidates;
        } catch (Exception e) {
            log.error("Rerank failed, returning original order", e);
            return candidates;
        }
    }

    private List<Double> callRerankApi(String query, List<String> documents) throws IOException {
        JSONObject body = new JSONObject();
        body.put("model", rerankModel);
        JSONObject input = new JSONObject();
        input.put("query", query);
        JSONArray docs = new JSONArray();
        for (String doc : documents) {
            docs.add(doc);
        }
        input.put("documents", docs);
        body.put("input", input);

        Request request = new Request.Builder()
                .url("https://dashscope.aliyuncs.com/api/v1/services/rerank/text-rerank/text-rerank")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(body.toJSONString(), JSON_MEDIA))
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "";
                throw new IOException("Rerank API error " + response.code() + ": " + errorBody);
            }
            String responseBody = response.body().string();
            JSONObject respJson = JSON.parseObject(responseBody);
            JSONArray results = respJson.getJSONObject("output").getJSONArray("results");
            List<Double> scores = new ArrayList<>();
            for (int i = 0; i < results.size(); i++) {
                scores.add(results.getJSONObject(i).getDouble("relevance_score"));
            }
            return scores;
        }
    }
}
