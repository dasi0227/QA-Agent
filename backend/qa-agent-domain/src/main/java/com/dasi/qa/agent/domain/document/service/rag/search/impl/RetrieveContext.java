package com.dasi.qa.agent.domain.document.service.rag.search.impl;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * RAG 检索请求上下文，封装查询文本、向量、用户隔离和过滤条件。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RetrieveContext {

    private String queryText;
    private float[] queryVector;
    private String userId;
    private List<String> filterDocumentIds;
    private int topK;

}