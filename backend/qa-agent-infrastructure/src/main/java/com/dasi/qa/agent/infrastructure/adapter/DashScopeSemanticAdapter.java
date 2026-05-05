package com.dasi.qa.agent.infrastructure.adapter;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.dasi.qa.agent.domain.document.adapter.ISemanticAdapter;
import com.dasi.qa.agent.infrastructure.properties.DashScopeProperties;
import com.dasi.qa.agent.types.dto.response.document.SearchResult;
import com.dasi.qa.agent.types.exception.ApiException;
import com.dasi.qa.agent.types.result.ResultCode;
import dev.langchain4j.community.model.dashscope.QwenEmbeddingModel;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
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
    private final String llmModel;

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
        this.llmModel = properties.getLlmModel();
    }

    // ======================== rewrite ========================

    private static final String REWRITE_PROMPT =
            "你是一个检索查询优化器。将用户问题改写为更适合向量检索和关键词检索的查询文本。\n" +
            "\n" +
            "规则：\n" +
            "- 剥离口语词（\"请问\"、\"怎么\"、\"说说\"、\"是什么\"）\n" +
            "- 保留并前置核心概念和专业术语\n" +
            "- 对比类问题保留双方关键词；因果类保留因和果\n" +
            "- 补充1-2个关键同义词或相关概念，空格分隔\n" +
            "- 只输出改写文本，不加任何前缀、引号或解释\n" +
            "\n" +
            "用户问题：";

    @Override
    public String rewriteQuery(String query) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", llmModel);
            JSONArray messages = new JSONArray();
            JSONObject systemMsg = new JSONObject();
            systemMsg.put("role", "system");
            systemMsg.put("content", "你是一个检索查询优化器。");
            messages.add(systemMsg);
            JSONObject userMsg = new JSONObject();
            userMsg.put("role", "user");
            userMsg.put("content", REWRITE_PROMPT + query);
            messages.add(userMsg);
            body.put("messages", messages);
            body.put("temperature", 0.1);
            body.put("max_tokens", 200);

            Request request = new Request.Builder()
                    .url("https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(body.toJSONString(), JSON_MEDIA))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("Query rewrite API error, falling back to original query: {}", response.code());
                    return query;
                }
                String responseBody = response.body().string();
                JSONObject respJson = JSON.parseObject(responseBody);
                String rewritten = respJson.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content")
                        .trim();
                log.debug("Query rewritten: {} -> {}", query, rewritten);
                return rewritten;
            }
        } catch (Exception e) {
            log.warn("Query rewrite failed, falling back to original query: {}", e.getMessage());
            return query;
        }
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
        docs.addAll(documents);
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
