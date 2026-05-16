# V2 RAG 设计说明

## 一、RAG 是什么

RAG（Retrieval-Augmented Generation，检索增强生成）在本系统中是**统一的证据检索底座**。它把用户上传的 Markdown 资料结构化切分，为每个切片生成向量和全文索引，对外提供 `/document/source/search` 检索接口。

它的核心职责：让下游 Agent（生成、反馈、评分）在调用 LLM 之前先检索资料证据，基于真实资料内容工作，而不是凭空编造。

## 二、用例：用户视角

1. 用户上传一份 Markdown 资料（如 Redis 学习笔记）
2. 系统自动完成：切片 → 向量化 → 中文分词 → 索引（异步，秒级完成）
3. 用户（或 Agent）调用 `/document/source/search`，输入查询文本，拿到按相关性排序的证据片段
4. 结果包含标题路径、正文、来源资料 ID、相似度分数，可追溯到具体资料的哪个章节

## 三、整体架构

```
┌──────────────────────────────────────────────────────────┐
│                   POST /document/source/upload             │
│                      (DocumentController)                  │
└──────────────────────────┬───────────────────────────────┘
                           │
                           ▼
              ┌────────────────────────┐
              │  IDocumentCrudService   │  → MySQL source_document（资料主记录）
              └───────────┬────────────┘
                          │
                          ▼
              ┌────────────────────────┐
              │     IMqUtil.send()     │  → Kafka topic: document.indexing
              │     (MqUtil 实现)       │  → 写入 message_job (UNSOLVED)
              └───────────┬────────────┘
                          │
                          ▼
              ┌────────────────────────┐
              │   IndexingConsumer     │  interfaces/consumer/
              │   @KafkaListener       │  消费消息 → markSuccess/markFail
              └───────────┬────────────┘
                          │
                          ▼
              ┌─────────────────────────────────────┐
              │        IIndexingService.index()     │
              │          (IndexingService)          │
              │                                     │
              │  1. 读 raw_content                  │
              │  2. IMarkdownChunker.chunk()        │
              │  3. IEmbeddingProvider.embed()      │
              │  4. 双写：MySQL + PostgreSQL        │
              └─────────────────────────────────────┘
                          │
          ┌───────────────┴───────────────┐
          ▼                               ▼
┌──────────────────┐          ┌──────────────────────┐
│   MySQL           │          │   PostgreSQL          │
│   document_chunk  │          │   chunk_search        │
│   (业务真数据)     │          │   + embedding vector  │
│                   │          │   + content_tsv       │
└──────────────────┘          └──────────┬───────────┘
                                         │
                                         ▼
                              ┌──────────────────────┐
                              │   POST /source-       │
                              │   document/search     │
                              │   (IRagPipeline)      │
                              │                      │
                              │  1. Embed query       │
                              │  2. Retrieve          │
                              │  3. RRF fuse          │
                              │  4. Rerank            │
                              │  5. Clip by agent     │
                              └──────────────────────┘
```

## 四、索引链路（资料上传 → 可检索）

### 4.1 触发

- `POST /document/source/upload`：Controller 创建资料后，调用 `mqUtil.send("document.indexing", "rag_xxx", payload)`
- `POST /document/source/update`：同上
- `POST /document/source/reindex`：手动触发

### 4.2 消费

`IndexingConsumer`（在 `interfaces/consumer/`）监听 `document.indexing` topic，收到消息后：

1. 解析 JSON 得到 documentId
2. 调用 `indexingService.index(documentId)`
3. 成功 → `mqUtil.markSuccess(jobId)`
4. 失败 → `mqUtil.markFail(jobId)`，由 xxl-job 定时重试

### 4.3 切片（MarkdownChunker）

使用 **flexmark** 解析 Markdown AST：

1. 按 H1-H6 标题层级切分章节，代码块不在内部切分
2. 单个切片 > 2000 字符时，按双换行二次切分，子切片保持相同 titlePath
3. `titlePath` 从根到叶以 ` > ` 连接（如 `Redis > 五大数据结构 > String`）
4. `moduleTags` 从标题路径逐级提取

### 4.4 Embedding（EmbeddingProvider）

包装 **QwenEmbeddingModel**（LangChain4j 社区模块，底层调用阿里云 DashScope text-embedding-v4）：

- 输入：文本列表
- 输出：1024 维 float[] 向量
- 单批最多 25 条，自动拆批并发（使用 `applicationTaskExecutor` 线程池）
- 单批失败重试 3 次（间隔 1s / 2s / 4s）

### 4.5 双写

| 存储 | 表 | 内容 | 用途 |
|------|-----|------|------|
| MySQL | `document_chunk` | 切片正文 + 元数据 | qa_item 引用、数据治理、外键约束 |
| PostgreSQL | `chunk_search` | 切片正文 + embedding(1024) + tsvector | 语义检索 + 关键词检索，不回源 MySQL |

`chunk_search` 是搜索副本，可从 MySQL `document_chunk` 全量重建（reindex 接口即基于此原则）。

## 五、检索链路（查询 → 证据列表）

### 5.1 入口

`POST /document/source/search` → `IRagPipeline.execute(SearchRequest)`

### 5.2 执行流程

```
SearchRequest
  │
  ├─1. Embed query text → queryVector（SEMANTIC / HYBRID 时）
  │     IEmbeddingProvider.embed([queryText])
  │
  ├─2. 根据 strategy 检索（并发到 PostgreSQL chunk_search）:
  │     SEMANTIC → SemanticRetriever  ─→ vector <=> 余弦距离
  │     KEYWORD  → KeywordRetriever   ─→ ts_rank + to_tsquery('zh', ...)
  │     HYBRID   → HybridRetriever    ─→ 两路并发 + RRF 融合
  │
  ├─3. IReranker.rerank(query, top20)
  │     DashScope gte-rerank API（OkHttp 直接调用）
  │
  ├─4. IEvidenceClipper.clip(results, agentType):
  │     GENERATION → Top-10
  │     FEEDBACK   → Top-3
  │     SCORING    → Top-5
  │     null       → Top-10
  │
  └─5. 返回 List<SearchResult>
```

### 5.3 三种检索策略

| 策略 | SQL 实现 | 适用场景 |
|------|---------|---------|
| `SEMANTIC` | `ORDER BY embedding <=> query_vector`（HNSW 索引，余弦距离） | 概念查询、语义相似 |
| `KEYWORD` | `WHERE content_tsv @@ to_tsquery('zh', query)` + `ORDER BY ts_rank`（GIN 索引） | 精确术语匹配 |
| `HYBRID` | 两路并发 → RRF（Reciprocal Rank Fusion，k=60）融合 | 综合召回，推荐默认策略 |

### 5.4 过滤条件

所有检索策略统一支持：

- `filterDocumentIds` — 限定资料范围
- `filterModuleTags` — JSONB contain 过滤（如 `["Redis","数据结构"]`）
- `filterTitlePathPrefix` — 标题路径前缀（如 `Redis > 数据结构`）
- `userId` — 强制用户隔离

## 六、消息可靠性机制

### 6.1 message_job 追踪

每条 Kafka 消息在发送前写入 `message_job` 表记录：

- `job_id = "rag_{documentId}"` — 幂等去重
- `job_status = UNSOLVED` — 初始状态
- `job_retry = 0` — 重试计数

### 6.2 消费确认

`IndexingConsumer`：
- 消费成功 → `mqUtil.markSuccess(jobId)` → `job_status = SUCCESS`
- 消费失败 → `mqUtil.markFail(jobId)` → `job_status = FAIL`

### 6.3 重试与死信

xxl-job 定时任务 `MessageJobRetryHandler`：

1. 扫描 `message_job` 中 `job_status = UNSOLVED` 的记录
2. `job_retry < 3` → 重新 `mqUtil.send()`（retry + 1，更新 latest_sent_at）
3. `job_retry >= 3` → 发送到 DLQ topic（原 topic + `.dlq`），标记 FAIL

### 6.4 死信队列

`DlqConsumer`（在 `interfaces/consumer/`）监听 `document.indexing.dlq`，记录日志。当前不做进一步处理，后续可扩展告警/人工介入。

## 七、代码组织（DDD 分层）

```
domain/document/service/rag/
  chunking/
    IMarkdownChunker.java          ← 切片接口
    MarkdownChunker.java           ← flexmark AST 实现
  embedding/
    IEmbeddingProvider.java        ← Embedding 接口
  indexing/
    IIndexingService.java          ← 索引编排接口
    IndexingService.java           ← chunk → embed → 双写
  retrieval/
    IRetriever.java                ← 检索策略接口
    RetrieveContext.java           ← 检索上下文
    SemanticRetriever.java         ← 语义检索
    KeywordRetriever.java          ← 关键词检索
    HybridRetriever.java           ← 混合检索 + RRF
  reranking/
    IReranker.java                 ← 重排序接口
  pipeline/
    IRagPipeline.java              ← Pipeline 编排接口
    RagPipeline.java               ← 完整链路编排
    IEvidenceClipper.java          ← 证据裁剪接口
    EvidenceClipper.java           ← 按 AgentType 裁剪

infrastructure/
  rag/embedding/
    EmbeddingProvider.java         ← QwenEmbeddingModel 包装
  rag/reranking/
    Reranker.java                  ← DashScope rerank HTTP 调用
  rag/indexing/
    IndexingConsumer.java → 已迁至 interfaces/consumer/

interfaces/consumer/
  IndexingConsumer.java            ← Kafka 消费（成功/失败确认）
  DlqConsumer.java                 ← 死信队列消费

application/job/
  MessageJobRetryHandler.java      ← xxl-job 重试调度
```

### 分层原则

| 层 | 放什么 | 不放什么 |
|----|--------|---------|
| **domain** | 接口（IXxx）+ 业务编排实现 + 值对象 | SQL、HTTP 调用、Kafka 消费、Spring 注解 |
| **infrastructure** | EmbeddingProvider、Reranker、MqUtil、IDocumentRepository 实现 | 业务编排逻辑 |
| **interfaces** | Controller（端点）、Consumer（Kafka Listener）、Job（xxl-job Handler） | 检索/索引业务逻辑 |
| **application** | LangChain4j Bean 配置、XxlJob 配置 | — |

## 八、技术选型

| 组件 | 选型 | 说明 |
|------|------|------|
| Markdown 解析 | flexmark 0.64.8 | AST 解析，标题层级提取 |
| Embedding 模型 | 阿里云 DashScope text-embedding-v4 | 1024 维，中文语义向量化 |
| Re-rank 模型 | 阿里云 DashScope gte-rerank | 精排检索结果 |
| 向量存储 | PostgreSQL + pgvector 0.7.4 | HNSW 索引，余弦距离 |
| 中文分词 | zhparser | 语义分词 → tsvector |
| 混合融合 | RRF（k=60） | 自实现，合并语义和关键词结果 |
| 异步索引 | Kafka 3.7.1 | 资料上传后异步触发切片+向量化 |
| 消息追踪/重试 | MySQL message_job + xxl-job | 定时扫描 UNSOLVED，重试上限 3 次后入 DLQ |
| Agent 框架 | LangChain4j 1.14.0 | QwenEmbeddingModel 包装 |

## 九、与下游 Agent 的关系

RAG 是统一证据底座，生成链路会通过 `/document/source/search` 获取资料证据。V4/V5 的当前实现不直接发起新的 RAG 检索：V4 使用题目已关联的 `source_chunk_ids_json` 回显来源，V5 只基于已落库的单题结果和反馈摘要做整轮评估。

| Agent | 阶段 | 调用参数 | 预期结果 |
|-------|------|---------|---------|
| 生成 Agent（V3） | SEARCH | `queryText=题目主题`, `agentType=GENERATION` | Top-10 证据 → 生成问答 |
| 反馈 Agent（V4） | 单题反馈 | 不调用 `/document/source/search` | 使用题目标准答案、提示和已关联来源生成反馈 |
| 评估 Agent（V5） | 整轮评估 | 不调用 `/document/source/search` | 使用单题结果、分数和反馈摘要生成整轮评估 |

## 十、V2 边界（不做的事）

1. 不做查询改写/同义扩展——queryText 直送 Embedding API 和 tsquery
2. 不做 Redis 检索缓存——当前万级数据量 pgvector < 50ms
3. 不做检索质量自动化评估——人工验证
4. 不做降级机制——组件异常记录日志抛异常
5. 不做前端对接——API 通过 curl/集成测试验证，前端延后到 V3
6. 不修改已有表结构——`document_chunk.embedding_vector`（JSON 字段）V2 不使用
