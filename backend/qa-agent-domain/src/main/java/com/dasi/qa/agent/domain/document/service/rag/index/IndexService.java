package com.dasi.qa.agent.domain.document.service.rag.index;

import lombok.extern.slf4j.Slf4j;

import com.dasi.qa.agent.domain.document.model.ChunkDraft;
import com.dasi.qa.agent.domain.document.model.ChunkSearchRow;
import com.dasi.qa.agent.domain.document.repository.IDocumentRepository;
import com.dasi.qa.agent.domain.document.service.rag.dashscope.IDashScopeService;
import com.dasi.qa.agent.domain.util.IJsonUtil;
import com.dasi.qa.agent.domain.util.IPromptUtil;
import com.dasi.qa.agent.types.dto.request.document.SourceDocumentRequest;
import com.dasi.qa.agent.types.dto.response.document.SourceDocumentResponse;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 资料索引服务，执行切片、摘要生成、向量化并同步写入 MySQL 和 PostgreSQL 检索引擎。
 */
@Service
@Slf4j
public class IndexService implements IIndexService {

    private final IDocumentRepository documentRepository;
    private final MarkdownChunker markdownChunker;
    private final IDashScopeService IDashScopeService;
    private final ChatModel summarizerModel;
    private final IPromptUtil promptUtil;
    private final IJsonUtil jsonUtil;

    public IndexService(IDocumentRepository documentRepository,
                        MarkdownChunker markdownChunker,
                        IDashScopeService IDashScopeService,
                        @Qualifier("summarizerModel") ChatModel summarizerModel,
                        IPromptUtil promptUtil,
                        IJsonUtil jsonUtil) {
        this.documentRepository = documentRepository;
        this.markdownChunker = markdownChunker;
        this.IDashScopeService = IDashScopeService;
        this.summarizerModel = summarizerModel;
        this.promptUtil = promptUtil;
        this.jsonUtil = jsonUtil;
    }

    @Override
    public void index(String documentId) {
        String userId = documentRepository.getDocumentUserId(documentId);

        SourceDocumentRequest query = new SourceDocumentRequest();
        query.setId(documentId);
        List<SourceDocumentResponse> docs = documentRepository.querySourceDocument(query, userId);
        if (docs.isEmpty()) {
            log.warn("【文本嵌入】资料未找到，跳过索引: documentId={}", documentId);
            return;
        }
        String rawContent = docs.get(0).getRawContent();
        if (rawContent == null || rawContent.isBlank()) {
            log.warn("【文本嵌入】资料无正文内容，跳过索引: documentId={}", documentId);
            return;
        }

        log.info("【文本嵌入】开始索引资料: documentId={}, userId={}", documentId, userId);

        List<ChunkDraft> drafts = markdownChunker.chunk(rawContent);
        log.info("【文本嵌入】资料切片完成: documentId={}, chunkCount={}", documentId, drafts.size());

        for (ChunkDraft draft : drafts) {
            draft.setChunkId(UUID.randomUUID().toString());
        }

        // 批量生成所有切片的摘要
        try {
            List<String> chunkContents = drafts.stream().map(ChunkDraft::getContent).toList();
            String response = summarizerModel.chat(
                    SystemMessage.from(promptUtil.loadPrompt("prompt/chunk-summarize.txt")),
                    UserMessage.from(jsonUtil.toJsonString(chunkContents))
            ).aiMessage().text().trim();
            List<String> summaries = jsonUtil.parseJsonArray(response, String.class);
            for (int i = 0; i < drafts.size() && i < summaries.size(); i++) {
                String summary = summaries.get(i);
                drafts.get(i).setSummary(summary != null && !summary.isBlank() ? summary : fallbackSummary(drafts.get(i).getContent()));
            }
            // 摘要数不足时用 fallback 补齐
            for (int i = summaries.size(); i < drafts.size(); i++) {
                drafts.get(i).setSummary(fallbackSummary(drafts.get(i).getContent()));
            }
        } catch (Exception e) {
            log.warn("【文本嵌入】切片摘要批量生成失败，使用正文截断: documentId={}", documentId, e);
            for (ChunkDraft draft : drafts) {
                draft.setSummary(fallbackSummary(draft.getContent()));
            }
        }

        List<String> contents = drafts.stream().map(ChunkDraft::getContent).toList();
        List<float[]> embeddings = IDashScopeService.embed(contents);

        documentRepository.replaceDocumentChunks(documentId, userId, drafts);

        List<ChunkSearchRow> searchRows = new ArrayList<>();
        for (int i = 0; i < drafts.size(); i++) {
            ChunkDraft draft = drafts.get(i);
            ChunkSearchRow row = new ChunkSearchRow();
            row.setChunkId(draft.getChunkId());
            row.setDocumentId(documentId);
            row.setUserId(userId);
            row.setChunkIndex(draft.getChunkIndex());
            row.setTitlePath(draft.getTitlePath());
            row.setContent(draft.getContent());
            row.setSummary(draft.getSummary());
            row.setModuleTags(draft.getModuleTags());
            row.setEmbedding(embeddings.get(i));
            searchRows.add(row);
        }

        documentRepository.deleteChunkSearchByDocumentId(documentId);
        documentRepository.batchInsertChunkSearch(searchRows);

        log.info("【文本嵌入】资料索引写入完成: documentId={}, chunkCount={}", documentId, searchRows.size());
    }

    private String fallbackSummary(String content) {
        return content.length() > 80 ? content.substring(0, 80) + "..." : content;
    }

    @Override
    public void remove(String documentId) {
        documentRepository.deleteDocumentChunksByDocumentId(documentId);
        documentRepository.deleteChunkSearchByDocumentId(documentId);
        log.info("【文本嵌入】资料索引已删除: documentId={}", documentId);
    }
}
