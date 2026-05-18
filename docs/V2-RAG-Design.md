# V2 RAG 设计说明

本文以当前代码实现为准，核心文件包括：

- `DocumentController`
- `IndexService`
- `MarkdownChunker`
- `RagSearchService`
- `HybridRetriever` / `SemanticRetriever` / `KeywordRetriever`
- `DashScopeService`
- `IndexConsumer` / `DlqConsumer` / `MessageRetryJob`

## 1. 当前目标

V2 RAG 的职责很明确：把用户上传的 Markdown 资料切成可检索切片，生成摘要和向量，并提供一个统一的证据搜索接口给生成链路和人工检索使用。

当前实现**不包含**：

1. 公开的手动 reindex 接口
2. 独立的 pipeline 抽象层
3. 按模块标签、标题路径的公开过滤参数
4. 复杂的 agent-type 裁剪逻辑

## 2. 对外接口

### 2.1 资料上传 / 更新

- `POST /document/source/upload`
- `POST /document/source/update`

这两个接口在写入 `source_document` 后，都会发送 Kafka 消息到 `document.index`。

消息体示例：

```json
{
  "documentId": "doc-id"
}
```

### 2.2 资料删除

- `POST /document/source/delete`

删除时会同时：

1. 删除 MySQL `document_chunk`
2. 删除 PostgreSQL `chunk_search`
3. 保留 `source_document.deleted = true` 的业务语义

### 2.3 搜索接口

- `POST /document/source/search`

请求：

```json
{
  "queryText": "Redis 跳表 应用场景",
  "filterDocumentIds": ["doc-1", "doc-2"]
}
```

当前公开过滤条件只有：

1. `queryText`
2. `filterDocumentIds`

## 3. 索引链路

```text
DocumentController.upload/update
  -> IMqUtil.send(topic=document.index)
  -> IndexConsumer
  -> IndexService.index(documentId)
  -> MarkdownChunker.chunk(rawContent)
  -> summarizerModel + prompt/chunk-summarize.txt
  -> DashScopeService.embed(contents)
  -> MySQL document_chunk
  -> PostgreSQL chunk_search
```

### 3.1 Kafka 与消息重试

配置中的 topic：

- 正常 topic：`document.index`
- 死信 topic：`document.index.dlq`

处理方式：

1. `IndexConsumer` 消费 `document.index`
2. 成功时 `mqUtil.markSuccess(jobId)`
3. 失败时 `mqUtil.markFail(jobId)`
4. `MessageRetryJob` 扫描 `message_job.job_status = UNSOLVED`
5. 重试超过 3 次后发到 `document.index.dlq`
6. `DlqConsumer` 只记录日志，不做补偿

## 4. 切片实现

`MarkdownChunker` 的行为：

1. 用 flexmark 解析 Markdown AST
2. 按 H1 ~ H6 标题层级维护 `titlePath`
3. 段落、围栏代码块、缩进代码块都可进入切片正文
4. 单切片正文超过 `2000` 字符时，按空行二次切分
5. 最终为每个切片补 `chunkIndex`

输出模型 `ChunkDraft`：

| 字段 | 说明 |
| --- | --- |
| `chunkId` | 后续由 `IndexService` 填 UUID |
| `chunkIndex` | 顺序索引 |
| `titlePath` | 标题路径 |
| `content` | 切片正文 |
| `moduleTags` | 当前实现直接复用标题层级 |
| `summary` | 后续补摘要 |

## 5. 摘要与向量化

### 5.1 摘要

`IndexService` 会批量把切片正文转成 JSON 数组，交给 `summarizerModel` 和 `prompt/chunk-summarize.txt`。

失败策略：

1. LLM 正常返回时，逐条写入摘要
2. 返回数量不足时，用 `fallbackSummary(content)` 补齐
3. 整批失败时，全部使用正文前 80 字截断摘要

### 5.2 向量化

当前向量服务是 `DashScopeService.embed()`：

1. 模型配置来自 `qa-agent.dashscope.embedding-model`
2. 每批最多 `10` 条
3. 每批最多重试 `3` 次
4. 指数退避延迟：`1s / 2s / 4s`
5. 向量维度固定为 `1024`

## 6. 双写策略

### 6.1 MySQL `document_chunk`

保存业务真数据：

1. `chunk_id`
2. `title_path`
3. `content`
4. `summary`
5. `module_tags_json`

### 6.2 PostgreSQL `chunk_search`

保存检索副本：

1. `embedding vector(1024)`
2. `content_tsv`
3. 业务元数据镜像

写入 SQL 关键点：

1. `embedding` 用 pgvector 的 `?::vector`
2. `content_tsv` 用 `to_tsvector('zh', content)`
3. `module_tags_json` 用 `JSONB`

## 7. 搜索链路

```text
RagSearchService.execute(request)
  -> rewrite(queryText)
  -> DashScopeService.embed([rewrittenQuery])
  -> HybridRetriever.search(ctx)
      -> SemanticRetriever.search()
      -> KeywordRetriever.search()
      -> RRF 融合
  -> DashScopeService.rerank(query, candidates)
  -> topK = 10 截断
```

### 7.1 查询改写

`RagSearchService.rewrite()` 使用：

- `rewriterModel`
- `prompt/query-rewrite.txt`

失败时直接回退原始查询。

### 7.2 语义检索

`SemanticRetriever` 调用 `DocumentRepository.semanticSearch()`：

- 按 `embedding <=> query_vector` 排序
- 返回 `vector_score = 1 - cosine_distance`
- 支持 `user_id` 隔离
- 支持 `filterDocumentIds`

### 7.3 关键词检索

`KeywordRetriever` 调用 `DocumentRepository.keywordSearch()`：

- 使用 `to_tsquery('zh', query)`
- 通过 `ts_rank` 排序
- 当前的 `toTsquery()` 实现是把空格拆词后用 `&` 连接

### 7.4 混合检索

`HybridRetriever` 当前实现：

1. 串行执行 semantic / keyword 两路
2. 用 RRF 融合，`RRF_K = 60`
3. 按融合分数排序
4. 保留每路原始得分到 `vectorScore` / `keywordScore`

### 7.5 重排序

`DashScopeService.rerank()`：

1. 只对前 `20` 个候选调用 DashScope rerank
2. 将 rerank 分数写入 `SearchResult.score`
3. 再按 `score` 重新排序
4. 失败时返回原排序

## 8. 代码组织

```text
domain/document/service/rag/
  dashscope/
    IDashScopeService.java
    DashScopeService.java
  index/
    IIndexService.java
    IndexService.java
    MarkdownChunker.java
  retriever/
    IRetriever.java
    impl/
      RetrieveContext.java
      SemanticRetriever.java
      KeywordRetriever.java
      HybridRetriever.java
  search/
    IRagSearchService.java
    RagSearchService.java

interfaces/consumer/
  IndexConsumer.java
  DlqConsumer.java

interfaces/job/
  MessageRetryJob.java
```

## 9. 当前代码口径

1. 当前没有 `IRagPipeline`、`IEmbeddingProvider` 这类旧版抽象。
2. 当前没有开放 `/document/source/reindex`。
3. 当前 `search` 公开返回 top10 结果。
4. 当前 filter 只支持 `filterDocumentIds`，文档不要再写 `filterModuleTags`、`filterTitlePathPrefix` 之类未实现字段。
