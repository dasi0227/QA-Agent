package com.dasi.qa.agent.domain.document.service.rag.search;

import com.dasi.qa.agent.domain.document.service.rag.dashscope.IDashScopeService;
import com.dasi.qa.agent.domain.document.model.ChunkSearchRow;
import com.dasi.qa.agent.domain.document.service.rag.retriever.impl.HybridRetriever;
import com.dasi.qa.agent.domain.document.service.rag.retriever.impl.RetrieveContext;
import com.dasi.qa.agent.domain.util.IPromptUtil;
import com.dasi.qa.agent.types.dto.request.document.RagSearchRequest;
import com.dasi.qa.agent.types.dto.response.document.SearchResult;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
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
    private final ChatModel rewriterModel;
    private final IPromptUtil promptUtil;

    public RagSearchService(IDashScopeService IDashScopeService,
                            HybridRetriever hybridRetriever,
                            @Qualifier("rewriterModel") ChatModel rewriterModel,
                            IPromptUtil promptUtil) {
        this.IDashScopeService = IDashScopeService;
        this.hybridRetriever = hybridRetriever;
        this.rewriterModel = rewriterModel;
        this.promptUtil = promptUtil;
    }

    @Override
    public List<SearchResult> execute(RagSearchRequest request) {

        // 1. 对原始问题进行语义改写，增强检索表达，提升召回质量
        String queryText = rewrite(request.getQueryText());

        // 2. 将改写后的问题转换为向量，用于后续向量检索
        float[] queryVector = IDashScopeService.embed(List.of(queryText)).get(0);

        // 3. 执行混合检索，结合向量相似度和关键词匹配结果进行初步召回
        RetrieveContext retrieveContext = RetrieveContext.builder()
                .queryText(queryText)
                .queryVector(queryVector)
                .userId(request.getUserId())
                .filterDocumentIds(request.getFilterDocumentIds())
                .topK(topK * 2)
                .build();
        List<ChunkSearchRow> rows = hybridRetriever.search(retrieveContext);

        // 4. 将数据库检索结果转换为业务层返回对象，屏蔽底层存储结构
        List<SearchResult> results = rows.stream()
                .map(row -> SearchResult.builder()
                        .chunkId(row.getChunkId())
                        .documentId(row.getDocumentId())
                        .titlePath(row.getTitlePath())
                        .content(row.getContent())
                        .summary(row.getSummary())
                        .moduleTags(row.getModuleTags())
                        .vectorScore(row.getVectorScore())
                        .keywordScore(row.getKeywordScore())
                        .build())
                .toList();

        // 5. 对初步召回结果进行语义重排序，使最终结果更贴近用户问题意图
        results = IDashScopeService.rerank(queryText, results);

        // 6. 按最终 topK 截断结果，避免返回过多低相关内容
        if (results.size() > topK) {
            results = results.subList(0, topK);
        }

        return results;
    }

    private String rewrite(String query) {
        try {
            String rewritten = rewriterModel.chat(
                    SystemMessage.from(promptUtil.loadRewriterPrompt()),
                    UserMessage.from(query)
            ).aiMessage().text().trim();
            return rewritten;
        } catch (Exception exception) {
            log.warn("【文本嵌入】查询改写失败，回退原始查询: query={}", query, exception);
            return query;
        }
    }
}
