# QA_Agent V2 RAG 实施规划（TODO）

## 一、RAG 在本系统中的定位

RAG 不是独立功能模块，而是**统一证据底座**。它在系统中的角色是：

1. 接收上游 `source_document`（资料正文），产出 `document_chunk`（可检索切片 + 向量）
2. 对外暴露检索 API，供下游三个 Agent 调用：
   - **生成 Agent（V3）**：检索资料证据 → 生成贴合资料的问答题目
   - **反馈 Agent（V4）**：检索相关证据 → 判定用户回答是否正确并给出改进建议
   - **评分 Agent（V5）**：检索薄弱模块相关证据 → 输出复习建议和整体总结
3. 检索结果需包含：切片内容、标题路径、来源文档、模块标签、相似度分数

**核心原则**：让 Agent 回答有据可依，能明确引用"哪份资料、哪个章节、哪段内容"。

---

## 二、技术栈选型

### 2.1 核心依赖

| 组件 | 选型 | 角色 |
|------|------|------|
| Agent 编排框架 | **LangChain4j 1.14.0** | DAG 编排、RAG 组件栈、Embedding 模型调用 |
| RAG 搜索引擎 | **PostgreSQL 16 + pgvector 0.7.4 + zhparser** | 已部署（docker-compose），RAG 检索唯一查询目标，存完整切片 + 向量 + tsvector |
| Embedding 模型 | **阿里云 DashScope `text-embedding-v3`** | 中文语义向量化，1024 维，LangChain4j 内置适配器 |
| 关键词分词 | **zhparser（PostgreSQL 中文分词扩展）** | 将切片内容分词为 tsvector，支持 `to_tsquery('zh', ...)` 中文全文检索 |
| 混合融合 | **RRF（Reciprocal Rank Fusion）** 自实现 | 合并语义检索和关键词检索结果 |
| 重排序 | **阿里云 DashScope Re-rank API**（`gte-rerank`） | 对融合结果精排，取 Top-K |
| 异步任务 | **Kafka 3.7.1**（已部署） | 资料上传后的异步切片+向量化 |
| 缓存 | **Redis 8.0**（已部署） | 高频查询缓存、向量检索结果缓存 |
| Markdown 解析 | **flexmark-java**（纯 Java，MIT License） | Markdown AST 解析，提取标题层级和结构化内容 |

### 2.2 选型理由

- **pgvector 而非 Milvus/Weaviate**：当前数据量（百~千级资料、万级切片）pgvector 完全胜任，避免引入额外基础设施。docker-compose 已有 `pgvector/pgvector:0.7.4-pg16`。
- **PostgreSQL 作为 RAG 唯一检索引擎**：在 PostgreSQL 中建 `chunk_search` 表，同时存完整切片正文 + JSONB 元数据 + `embedding vector(1024)` + `tsvector`。语义检索和关键词检索都只查 PostgreSQL，结果直接返回，**不回源 MySQL**。MySQL 的 `document_chunk` 作为业务真数据源（qa_item 引用、数据治理），PostgreSQL 的 `chunk_search` 作为搜索副本（只读，可随时从 MySQL 重建）。
- **阿里云 DashScope 而非本地部署 Embedding 模型**：项目已接入阿里云 OSS（头像上传），同一套 AK/SK 零额外接入成本；LangChain4j 内置 `DashscopeEmbeddingModel`，一行配置即可集成，无需自建 Docker 服务和维护 GPU 资源；`text-embedding-v3` 中文 MTEB 基准领先，免费额度（100 万 tokens/月）对当前规模完全够用。
- **zhparser 而非 MySQL ngram**：MySQL ngram 是盲切（固定 N 字符），查询"跳表"会召回"表结构"等无关片段，准确率低。zhparser 基于中文语义分词，"跳表"不会被切碎，召回精度明显更高。zhparser 需编译安装，但 docker-compose 中可在 pgvector 镜像基础上打一层 `zhparser` 扩展，一次配置长期可用。
- **flexmark 而非 commonmark-java**：flexmark 支持 AST 遍历、扩展丰富，便于后续生成 HTML 预览、自定义标题提取。

---

## 三、涉及的库表

### 3.1 已有表（MySQL 侧）

| 库 | 表 | RAG 角色 | 关键字段 |
|----|----|---------|---------|
| MySQL | `source_document` | 资料来源 | `id`, `user_id`, `raw_content`, `normalized_content`, `module_tags_json`, `deleted` |
| MySQL | `document_chunk` | **切片业务真数据**（qa_item 引用源、数据治理源） | `id`, `document_id`, `user_id`, `chunk_index`, `title_path`, `content`, `summary`, `module_tags_json` |
| MySQL | `qa_item` | 证据引用目标 | `id`, `qa_set_id`, `source_chunk_ids_json`（存储 RAG 召回的 chunk ID 列表） |
| MySQL | `qa_generation_task` | 触发 RAG 的入口 | `id`, `user_id`, `document_ids_json`, `status`, `stage` |
| MySQL | `user_profile` | 检索上下文 | `target_role`, `target_domain`, `answer_style` 等 |

### 3.1b PostgreSQL 侧（新建）—— RAG 检索引擎

| 库 | 表 | RAG 角色 | 关键字段 |
|----|----|---------|---------|
| PostgreSQL | `chunk_search` | **RAG 检索唯一查询目标** | `chunk_id`, `document_id`, `user_id`, `title_path`, `content`, `module_tags_json`, `embedding vector(1024)`, `content_tsv tsvector` |

### 3.2 需要新建的表

#### 3.2.1 `rag_search_log`（RAG 检索日志表）

```sql
CREATE TABLE `rag_search_log` (
    `id` CHAR(36) NOT NULL,
    `user_id` CHAR(36) NOT NULL,
    `query_text` TEXT NOT NULL,
    `query_type` VARCHAR(32) NOT NULL,        -- SEMANTIC / HYBRID / KEYWORD
    `filter_module_tags` JSON NULL,
    `filter_document_ids` JSON NULL,
    `result_chunk_ids` JSON NOT NULL,          -- 召回的 chunk ID 列表
    `top_score` DECIMAL(10,6) NULL,
    `total_hits` INT NOT NULL DEFAULT 0,
    `latency_ms` INT NOT NULL DEFAULT 0,
    `agent_type` VARCHAR(32) NULL,            -- GENERATION / FEEDBACK / SCORING
    `task_id` CHAR(36) NULL,
    `created_at` DATETIME NOT NULL,
    PRIMARY KEY (`id`),
    KEY `idx_rag_log_user` (`user_id`),
    KEY `idx_rag_log_task` (`task_id`),
    KEY `idx_rag_log_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

用途：监控检索质量、追踪 Agent 使用了哪些证据、成本核算（Embedding / Re-rank 调用次数）。

### 3.3 PostgreSQL `chunk_search` 表（RAG 搜索引擎核心）

```sql
-- PostgreSQL 侧：完整搜索表，检索不依赖 MySQL
CREATE TABLE chunk_search (
    chunk_id        VARCHAR(36) PRIMARY KEY,           -- 与 MySQL document_chunk.id 对应
    document_id     VARCHAR(36) NOT NULL,
    user_id         VARCHAR(36) NOT NULL,
    chunk_index     INT NOT NULL,
    title_path      VARCHAR(500),
    content         TEXT NOT NULL,
    summary         TEXT,
    module_tags_json JSONB,
    embedding       vector(1024),                      -- DashScope text-embedding-v3
    content_tsv     TSVECTOR,                          -- zhparser 中文分词向量
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 向量索引（语义检索 ANN）
CREATE INDEX idx_cs_embedding ON chunk_search
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);              -- HNSW 参数，质量优先

-- 全文检索索引（关键词检索）
CREATE INDEX idx_cs_tsv ON chunk_search USING GIN (content_tsv);

-- 业务过滤索引
CREATE INDEX idx_cs_user ON chunk_search (user_id);
CREATE INDEX idx_cs_document ON chunk_search (document_id);
CREATE INDEX idx_cs_module_tags ON chunk_search USING GIN (module_tags_json);
CREATE INDEX idx_cs_title_path ON chunk_search (title_path);
```

**关键设计点**：

1. **不回源 MySQL**：`chunk_search` 存完整的切片正文、元数据和向量。检索时一条 SQL 查到所有内容，结果直接返回。
2. **双检索引擎合一**：语义检索走 HNSW 向量索引，关键词检索走 GIN tsvector 索引，过滤走 JSONB + B-tree，全部在 PostgreSQL 单库完成。
3. **可重建**：`chunk_search` 是搜索副本，可从 MySQL `document_chunk` 全量重建。数据一致性由 IndexingService 保证（先写 MySQL 真数据，再同步写 PostgreSQL）。
4. **搜索副本延迟容忍**：MySQL → PostgreSQL 同步是近实时的（Kafka 异步写入），允许百毫秒级延迟。如果 PostgreSQL 写入失败，Kafka Consumer 重试，不影响 MySQL 主链路。

### 3.4 双库数据流

```
用户上传/编辑资料
       │
       ▼
  IndexingService
       │
       ├─→ MySQL document_chunk (业务真数据，同步写入)
       │     └─ qa_item.source_chunk_ids_json 引用此表 chunk_id
       │
       └─→ PostgreSQL chunk_search (搜索副本，Kafka 异步写入)
             ├─ embedding vector(1024)   ← DashScope API
             ├─ content_tsv              ← zhparser(to_tsvector('zh', content))
             └─ module_tags_json / title_path / content  ← 与 MySQL 同源
```

---

## 四、需要实现的功能模块

### 4.1 模块总览

```
qa-agent-rag/                              ← 新增模块（也可放在 domain + infrastructure 中）
  ├── chunking/          # Markdown 解析 + 切片
  ├── embedding/         # Embedding 模型调用
  ├── indexing/          # 向量化 + 写入 pgvector
  ├── retrieval/         # 检索策略（语义/关键词/混合）
  ├── reranking/         # 重排序
  ├── pipeline/          # 完整 RAG Pipeline 编排
  └── api/               # 检索 API 暴露
```

### 4.2 功能清单

#### F1：Markdown 结构化解析器（`chunking`）

**输入**：`source_document.raw_content`（Markdown 文本）

**输出**：`List<ChunkDraft>`（切片草稿列表）

每个 `ChunkDraft` 包含：

| 字段 | 来源 | 说明 |
|------|------|------|
| `chunkIndex` | 自增 | 整篇文档内的序号 |
| `titlePath` | Markdown 标题层级 | 如 `Redis > 五大数据结构 > String` |
| `content` | 正文段落 | 该章节下的完整文本 |
| `moduleTags` | 标题提取 + 人工 | 如 `["Redis", "数据结构"]` |
| `summary` | 初始留空 | 后续由生成 Agent 填充 |

**实现要点**：

1. 使用 flexmark 解析 Markdown AST
2. 按 `H1-H6` 标题层级切分章节
3. 章节过长时（>2000 字符），按段落（双换行）二次切分，保持同一 `titlePath`
4. 保留代码块不切割（代码块内部不插入切分点）
5. 从标题和首段中提取 `moduleTags` 候选词（可用关键词匹配规则）

**关键类**：
- `MarkdownChunker`（接口，domain 层）
- `FlexmarkMarkdownChunker`（flexmark 实现，infrastructure 层）

#### F2：Embedding 服务（`embedding`）

**选型**：阿里云 DashScope `text-embedding-v3`，1024 维。

**为何不本地部署 BGE**：本地部署需要 GPU 或至少 4GB 内存的模型服务容器，增加运维负担；项目已用阿里云 OSS，同账号接入 DashScope 零额外运维。

**LangChain4j 内置适配器**：

LangChain4j 1.14.0 提供了 `DashscopeEmbeddingModel`，无需手写 HTTP 调用和 batch 拆分逻辑。配置示例：

```java
// application 层 Configuration
@Bean
public DashscopeEmbeddingModel dashscopeEmbeddingModel() {
    return DashscopeEmbeddingModel.builder()
        .apiKey(dashscopeApiKey)       // 与 OSS 同 AK/SK
        .modelName("text-embedding-v3")
        .build();
}
```

**封装**：在 domain 层定义 `EmbeddingService` 接口（输入 `List<String>`，输出 `List<float[]>`），infrastructure 层用 `DashscopeEmbeddingModel` 实现。加一层薄封装的好处：未来如需切模型，只换 infrastructure 实现，domain 和调用方不改。

**关键实现细节**：

1. batch 拆分：DashScope API 单次最多 25 条文本，超过自动拆批，每批并发发送（用 `CompletableFuture` + `ThreadPoolTaskExecutor`）
2. 超时重试：单批失败重试 3 次（间隔 1s / 2s / 4s），全量失败抛异常交由上层降级
3. 成本保护：索引之前估算 token 数，单次超过 10 万 token 打印 warn 日志

**关键类**：
- `EmbeddingService`（接口，domain 层）
- `DashScopeEmbeddingService`（infrastructure 层，包装 `DashscopeEmbeddingModel`）

#### F3：索引服务（`indexing`）

**职责**：切片后同时写入 MySQL（业务真数据）和 PostgreSQL（搜索副本）。

**触发时机**：
1. 资料上传后（通过 Kafka 异步触发）
2. 资料内容更新后（重建该资料的所有切片和向量）
3. 资料删除后（MySQL 软删除，PostgreSQL 真删除对应 chunk_search 记录）

**实现要点**：

1. `IndexingService.chunkAndIndex(documentId)`：
   - 读取 `source_document.raw_content`
   - 调用 `MarkdownChunker` 产出 `List<ChunkDraft>`
   - **先写 MySQL** `document_chunk`（删除旧切片 + 插入新切片），这是业务真数据
   - 批量调用 `EmbeddingService.embed(contents)` 产出向量
   - **再写 PostgreSQL** `chunk_search`（DELETE + INSERT），含完整字段 + embedding + `to_tsvector('zh', content)` 生成 `content_tsv`
   - MySQL 写入成功但 PostgreSQL 失败 → Kafka 重试，不影响 MySQL 主链路
2. Kafka 消费者监听 `document.indexing` topic，异步执行
3. 幂等性：按 `document_id` 先清旧数据再重建（两侧都做 DELETE + INSERT）
4. 阶段状态写入 `qa_generation_task_message`（如果关联到任务）

**关键类**：
- `IndexingService`（domain 层）
- `DocumentChunkMapper`（MyBatis-Plus，MySQL 侧，已有）
- `ChunkSearchJdbcRepository`（JDBC Template，PostgreSQL 侧，新建）

#### F4：混合检索引擎（`retrieval`）

这是 RAG 最核心的模块。**所有检索只查 PostgreSQL `chunk_search` 表，不回源 MySQL。**

**检索策略枚举**：

| 策略 | 说明 |
|------|------|
| `SEMANTIC` | 纯向量检索，`embedding <=> :queryVec` |
| `KEYWORD` | 纯关键词检索，`content_tsv @@ to_tsquery('zh', :query)` |
| `HYBRID` | 混合检索（语义 + 关键词），RRF 融合 |

**统一检索请求**：

```java
public class SearchRequest {
    String queryText;                    // 检索查询文本
    SearchStrategy strategy;             // SEMANTIC / KEYWORD / HYBRID
    String userId;                       // 按用户隔离
    List<String> filterDocumentIds;      // 限定资料范围（可选）
    List<String> filterModuleTags;       // 按模块标签过滤（可选，JSONB contain）
    List<String> filterTitlePathPrefix;  // 按标题路径前缀过滤（可选）
    int topK;                            // 返回 Top-K 结果（默认 10）
}
```

**统一检索结果**：

```java
public class SearchResult {
    String chunkId;
    String documentId;
    String titlePath;
    String content;
    String summary;
    List<String> moduleTags;
    float score;              // 融合后分数
    float vectorScore;        // 向量相似度（如适用）
    float keywordScore;       // 关键词分数（如适用）
    SearchStrategy source;    // 来源策略
}
```

> 注：`documentName` 不在 `chunk_search` 表中。如前端需要展示文档名，检索完成后按 `document_id` 批量查一次 MySQL `source_document`（轻量 KV 查询，1 次 SQL IN 查询即可，不影响检索主性能）。

**各检索策略实现**：

1. **语义检索**（单条 PostgreSQL SQL）：
   ```sql
   SELECT chunk_id, document_id, title_path, content, summary,
          module_tags_json,
          1 - (embedding <=> :queryVector) AS vector_score
   FROM chunk_search
   WHERE user_id = :userId
     AND (:docIds IS NULL OR document_id = ANY(:docIds))
     AND (:tags IS NULL OR module_tags_json @> :tags::jsonb)
     AND (:pathPrefix IS NULL OR title_path LIKE :pathPrefix || '%')
   ORDER BY embedding <=> :queryVector
   LIMIT :topK * 2;
   ```

2. **关键词检索**（单条 PostgreSQL SQL）：
   ```sql
   SELECT chunk_id, document_id, title_path, content, summary,
          module_tags_json,
          ts_rank(content_tsv, to_tsquery('zh', :queryText)) AS keyword_score
   FROM chunk_search
   WHERE user_id = :userId
     AND content_tsv @@ to_tsquery('zh', :queryText)
     AND (:docIds IS NULL OR document_id = ANY(:docIds))
     AND (:tags IS NULL OR module_tags_json @> :tags::jsonb)
     AND (:pathPrefix IS NULL OR title_path LIKE :pathPrefix || '%')
   ORDER BY keyword_score DESC
   LIMIT :topK * 2;
   ```

3. **混合检索（HYBRID）**：两条 SQL 分别执行语义检索和关键词检索（可用 PostgreSQL 同一连接并发），拿到双方 Top-N 结果后在应用层做 RRF 融合。

4. **RRF 融合**：
   ```
   RRF_score(doc) = Σ (1 / (k + rank_i(doc)))
   其中 k=60，rank_i 是文档在语义或关键词单路检索中的排名。
   按 RRF_score 降序 → 取 topK。
   ```

**关键类**：
- `SearchRequest` / `SearchResult`（types 模块）
- `RetrievalStrategy`（接口，domain 层）
- `SemanticRetriever`（pgvector ANN，infrastructure 层）
- `KeywordRetriever`（tsquery 全文检索，infrastructure 层）
- `HybridRetriever`（RRF 融合，domain 层）
- `RetrievalRouter`（策略分发，domain 层）

> 注意：`SemanticRetriever` 和 `KeywordRetriever` 的实现类都在 infrastructure 层，但都**只依赖 PostgreSQL DataSource**，不依赖 MySQL。过滤条件（documentIds / moduleTags / titlePathPrefix）在 SQL 层直接过滤，不拿到应用层再过滤。

#### F5：重排序服务（`reranking`）

**职责**：对混合检索的 Top-N 结果重新精排，确保最相关的片段排在前面。

**选型**：阿里云 DashScope `gte-rerank` 模型。LangChain4j 1.14.0 提供 `DashscopeScoringModel`。

**实现要点**：

1. 接口抽象 `Reranker`（domain 层）
2. 主实现：`DashScopeReranker`（infrastructure 层），包装 `DashscopeScoringModel`
3. 降级实现：`RuleBasedReranker`（标题路径命中加分、模块标签匹配加分、向量相似度 Boost），DashScope API 不可用时自动切换
4. 只对检索 Top-20 做重排序，不全集计算
5. 返回附带新 `score` 的结果列表

**关键类**：
- `Reranker`（接口，domain 层）
- `DashScopeReranker`（infrastructure 层，包装 `DashscopeScoringModel`）
- `RuleBasedReranker`（轻量规则降级实现，infrastructure 层）

#### F6：RAG Pipeline 编排（`pipeline`）

**职责**：将上述模块串联为可复用的 Pipeline。

**Pipeline 流程**：

```
输入: SearchRequest
  │
  ├─1. 查询预处理: queryText 清洗 + 同义扩展（可选）
  │
  ├─2. 检索阶段:
  │   ├─ 语义检索 → candidateSet_A
  │   └─ 关键词检索 → candidateSet_B
  │
  ├─3. 融合阶段:
  │   └─ RRF 融合 → mergedCandidates
  │
  ├─4. 过滤阶段:
  │   └─ 按 filterDocumentIds / filterModuleTags / filterTitlePathPrefix 过滤
  │
  ├─5. 重排序阶段:
  │   └─ DashScope Re-rank 精排 → rankedResults
  │
  ├─6. 证据裁剪（Evidence Clipping）:
  │   └─ 根据 Agent 类型裁剪结果:
  │       - GENERATION: 保留 Top-10，附 titlePath + documentName
  │       - FEEDBACK: 保留 Top-3，附 conflict_tip 相关片段
  │       - SCORING: 保留 Top-5，附每段的 weakness/strength 标记
  │
  └─7. 日志记录: 写入 rag_search_log
  │
输出: List<SearchResult>
```

**关键类**：
- `RagPipeline`（domain 层，编排入口）
- `EvidenceClipper`（domain 层，证据裁剪）

#### F7：RAG API 暴露

所有 API 挂载在 `/qa-agent/api/v1/rag/` 下。

| 方法 | 路径 | 鉴权 | 请求字段 | 说明 |
|------|------|------|---------|------|
| POST | `/rag/search` | 是 | `SearchRequest` | 通用检索接口，三个 Agent 均调用此接口 |
| POST | `/rag/document/reindex` | 是 | `documentId` | 触发资料重切片+重向量化 |
| GET | `/rag/document/chunks` | 是 | `documentId` | 查看某资料的切片列表（调试用） |
| POST | `/rag/chunk/test-embed` | 是 | `text` | 测试 Embedding 可用性（运维用） |

**通用检索接口详解**（`POST /rag/search`）：

```json
// 请求
{
  "queryText": "Redis 跳表的数据结构和应用场景",
  "strategy": "HYBRID",
  "filterDocumentIds": ["uuid-1", "uuid-2"],     // 可选：限定资料范围
  "filterModuleTags": ["Redis"],                   // 可选：模块过滤
  "filterTitlePathPrefix": "Redis > 数据结构",     // 可选：标题路径前缀
  "topK": 10,
  "agentType": "GENERATION"                        // 触发证据裁剪策略
}

// 响应
{
  "code": 0,
  "data": [
    {
      "chunkId": "chunk-uuid-1",
      "documentId": "doc-uuid-1",
      "documentName": "Redis面试准备笔记.md",
      "titlePath": "Redis > 五大数据结构 > 跳表",
      "content": "跳表（Skip List）是一种随机化的数据结构...",
      "moduleTags": ["Redis", "数据结构", "跳表"],
      "score": 0.8912,
      "vectorScore": 0.8765,
      "keywordScore": 0.7234
    }
  ]
}
```

---

## 五、RAG 与各 Agent 的关联方式

### 5.1 生成 Agent（V3）调用 RAG

`PLAN → SEARCH → DRAFT → VALIDATE → FINALIZE`

- **SEARCH 阶段**：对每个计划生成的题目主题，调用 `POST /rag/search`
  - `queryText` = 题目主题（如"Redis 跳表原理"）
  - `filterDocumentIds` = 任务关联的资料列表
  - `agentType` = `GENERATION`
  - 返回 Top-10 证据块
- **DRAFT 阶段**：将 `searchResults` 作为 LLM Prompt 的 evidence 上下文，生成题目
- **VALIDATE 阶段**：用检索结果交叉校验生成的题目是否有据可依
- 生成的 `qa_item.source_chunk_ids_json` 写入实际引用的 chunk ID

### 5.2 反馈 Agent（V4）调用 RAG

- 用户提交答案后，调用 `POST /rag/search`
  - `queryText` = `qa_item.question`（当前题目的问题文本）
  - `agentType` = `FEEDBACK`
  - 返回 Top-3 证据块
- LLM 同时拿到：用户答案 + 标准答案 + 证据块 → 输出反馈
- 反馈中可引用证据块的 `titlePath`："根据您在《Redis面试准备笔记 > 跳表》中的笔记..."

### 5.3 评分 Agent（V5）调用 RAG

- 练习会话结束后，对薄弱模块调用 `POST /rag/search`
  - `queryText` = 该模块标签（如"Redis 集群"）
  - `agentType` = `SCORING`
  - 返回 Top-5 证据块
- LLM 根据薄弱项 + 证据块 + 会话结果输出复习建议

---

## 六、实施规划（分阶段推进）

### Phase 0：基础设施准备（预计 1 天）

| 任务 | 内容 |
|------|------|
| P0.1 | PostgreSQL 安装 zhparser 扩展 + 创建 `chunk_search` 表及全部索引（hnsw 向量索引、GIN tsvector 索引、JSONB 索引） |
| P0.2 | 修改 docker-compose 的 PostgreSQL 镜像：基于 `pgvector/pgvector:0.7.4-pg16` 写 Dockerfile，编译安装 zhparser |
| P0.3 | MySQL 创建 `rag_search_log` 日志表 |
| P0.4 | 确认 DashScope API Key 可用（与 OSS 同 AK/SK），在 `application-dev.yml` 中配置 |
| P0.5 | 项目中引入 LangChain4j BOM + 所需 starters |

**P0.2 的 PostgreSQL 自定义镜像**（`postgresql/Dockerfile`）：

```dockerfile
FROM pgvector/pgvector:0.7.4-pg16

# 编译安装 zhparser 中文分词扩展
RUN apt-get update && apt-get install -y \
    postgresql-server-dev-16 \
    build-essential \
    git \
    && git clone https://github.com/amutu/zhparser.git \
    && cd zhparser \
    && make && make install \
    && cd .. && rm -rf zhparser \
    && apt-get remove -y build-essential git \
    && apt-get autoremove -y \
    && rm -rf /var/lib/apt/lists/*
```

**P0.4 配置**（`application-dev.yml` 新增）：

```yaml
qa-agent:
  dashscope:
    api-key: ${DASHSCOPE_API_KEY}   # 与 OSS 同 AK/SK
    embedding-model: text-embedding-v3
    rerank-model: gte-rerank
```

**P0.5 的 Maven 依赖**（`pom.xml` 新增）：

```xml
<!-- LangChain4j BOM -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-bom</artifactId>
    <version>1.14.0</version>
    <type>pom</type>
    <scope>import</scope>
</dependency>
<!-- 核心依赖 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
</dependency>
<!-- DashScope 适配器（Embedding + Re-rank 一体化） -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-dashscope</artifactId>
</dependency>
<!-- pgvector 集成 -->
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-pgvector</artifactId>
</dependency>
<!-- Markdown 解析 -->
<dependency>
    <groupId>com.vladsch.flexmark</groupId>
    <artifactId>flexmark-all</artifactId>
    <version>0.64.8</version>
</dependency>
```

### Phase 1：切片 + 向量化（预计 2 天）

| 任务 | 内容 |
|------|------|
| P1.1 | 实现 `MarkdownChunker` + `FlexmarkMarkdownChunker` |
| P1.2 | 实现 `EmbeddingService` + `DashScopeEmbeddingService`（包装 LangChain4j `DashscopeEmbeddingModel`） |
| P1.3 | 实现 `IndexingService`：串联 chunk → embed → 写入 MySQL + PostgreSQL |
| P1.4 | 实现 `POST /rag/document/reindex` 和 `GET /rag/document/chunks` API |
| P1.5 | 实现 Kafka Producer/Consumer：`document.uploaded` → 异步触发索引 |

### Phase 2：检索引擎（预计 2-3 天）

| 任务 | 内容 |
|------|------|
| P2.1 | 实现 `SemanticRetriever`（PostgreSQL pgvector HNSW，单 SQL 含过滤条件） |
| P2.2 | 实现 `KeywordRetriever`（PostgreSQL tsquery + zhparser，单 SQL 含过滤条件） |
| P2.3 | 实现 `HybridRetriever`（RRF 融合，两路结果合并排序） |
| P2.4 | 实现 `RetrievalRouter`（策略分发） |
| P2.5 | 实现 `Reranker`（`DashScopeReranker` 封装 `DashscopeScoringModel` + `RuleBasedReranker` 降级） |
| P2.6 | 实现 `RagPipeline` 编排层（串联检索 → 融合 → 过滤 → 重排 → 裁剪全链路） |
| P2.7 | 实现 `EvidenceClipper`（按 Agent 类型裁剪结果） |
| P2.8 | 实现 `POST /rag/search` API |

### Phase 3：缓存 + 日志 + 测试（预计 1-2 天）

| 任务 | 内容 |
|------|------|
| P3.1 | Redis 检索结果缓存（`queryText + filterHash → SearchResult[]`，TTL 10min） |
| P3.2 | `rag_search_log` 写入逻辑 |
| P3.3 | 集成测试：用 seed.sql 中已有的 2 篇示例文档，验证全链路（切片 → 向量 → 检索 → 融合 → 重排） |
| P3.4 | Embedding 调用失败降级测试、超时重试测试 |

### Phase 4：前端对接 + 调试 UI（预计 1 天）

| 任务 | 内容 |
|------|------|
| P4.1 | 在 RepositoryPage 资料详情中增加"重建索引"按钮 |
| P4.2 | 在 CreatePage 中增加检索预览（输入问题 → 展示相关片段） |
| P4.3 | 调试 UI：检索结果高亮 + 来源标注 + 相似度分数展示 |

### Phase 5：文档与交付（预计 0.5 天）

| 任务 | 内容 |
|------|------|
| P5.1 | 补充 `docs/RAG.md`（RAG 使用说明 + 接口文档） |
| P5.2 | 更新 `docs/API.md`（新增 RAG 接口） |
| P5.3 | 更新 `docs/PRD.md` V2 标记为完成 |

---

## 七、RAG 与现有业务关联总览

```
                          ┌─────────────────────┐
                          │   source_document    │
                          │   (用户上传的资料)    │
                          └────────┬────────────┘
                                   │
                    ┌──────────────┼──────────────┐
                    │ Kafka: document.indexing    │
                    └──────────────┼──────────────┘
                                   │
                    ┌──────────────▼──────────────┐
                    │     IndexingService         │
                    │  MarkdownChunker → Chunks   │
                    │  EmbeddingService → Vectors │
                    └──────┬───────────┬──────────┘
                           │           │
              ┌────────────▼──┐   ┌───▼───────────────┐
              │  MySQL        │   │  PostgreSQL        │
              │ document_chunk│   │  chunk_search      │
              │ (业务真数据)   │   │  (搜索副本: 完整切片│
              │ qa_item 引用   │   │  + vector + tsvector│
              │ 数据治理源     │   │  RAG 检索唯一目标)  │
              └───────────────┘   └───┬───────────────┘
                                      │
                    ┌─────────────────┼──────────────┐
                    │                 │              │
                    │    ┌────────────▼──────────┐   │
                    │    │  Redis 检索缓存        │   │
                    │    │  (query→results, 10min)│   │
                    │    └────────────┬──────────┘   │
                    │                 │              │
                    └─────────────────┼──────────────┘
                                      │
                    ┌─────────────────▼──────────────┐
                    │      RagPipeline               │
                    │  ① SemanticRetriever(pgvector)  │
                    │  ② KeywordRetriever(tsquery)    │
                    │  ③ RRF 融合                     │
                    │  ④ Re-rank(DashScope)           │
                    │  ⑤ Evidence Clip                │
                    └─────────────────┬──────────────┘
                                      │
           ┌──────────────────────────┼──────────────────────┐
           │                          │                      │
    ┌──────▼──────┐  ┌───────────────▼──┐  ┌────────────────▼──┐
    │ 生成 Agent   │  │ 反馈 Agent       │  │ 评分 Agent         │
    │ (V3)        │  │ (V4)             │  │ (V5)               │
    │ SEARCH 阶段  │  │ 判定正确性+改进   │  │ 薄弱项复习+总结     │
    │ 调用 RAG     │  │ 调用 RAG         │  │ 调用 RAG           │
    └─────────────┘  └──────────────────┘  └───────────────────┘
                                      │
                    ┌─────────────────▼──────────────┐
                    │      rag_search_log (MySQL)     │
                    │   (检索审计 + 质量监控)          │
                    └────────────────────────────────┘
```

---

## 八、边界与降级策略

### 8.1 当前不做

1. 不做多租户向量索引（当前只需 `user_id` 隔离）
2. 不做实时 Embedding 模型更新（固定模型版本）
3. 不做跨资料的大规模语义聚类
4. 不做 RAG 的 A/B 评测框架（当前阶段够用即可，后续优化）

### 8.2 降级策略

| 故障场景 | 降级方案 |
|---------|---------|
| DashScope Embedding API 不可用 | 降级为纯关键词检索（KEYWORD 策略），不返回语义分数 |
| DashScope Re-rank API 不可用 | 降级为 `RuleBasedReranker`（标题路径命中 + 模块标签匹配 + 向量分数 Boost） |
| PostgreSQL 不可用 / pgvector 查询超时 | 无法降级——PostgreSQL 是 RAG 唯一检索引擎，需运维告警恢复后重试 |
| zhparser 分词异常（返回空 tsvector） | 降级为 SQL `LIKE '%keyword%'` 模糊匹配（精度下降但可用） |
| Kafka 不可用 | 同步执行资料索引（HTTP 请求内完成），提示用户"索引中请稍后" |
| Redis 不可用 | 跳过缓存，直接查 PostgreSQL（性能下降但功能可用） |

> 注意：PostgreSQL 作为 RAG 唯一检索入口，无 MySQL 回源路径可降级。生产环境需保证 PostgreSQL 高可用（至少主从）。

---

## 九、验收标准

RAG 模块满足以下条件即为 V2 完成：

1. 上传一篇 Markdown 资料后，5 秒内完成切片和向量化，`document_chunk` 表有对应记录
2. `POST /rag/search` 返回结果按相似度降序排列，Top-1 结果内容与查询高度相关
3. 检索结果明确包含 `titlePath`、`documentName`、`score`，可追溯到源资料
4. 同一查询，两次调用返回的 Top-3 结果一致（稳定性）
5. Embedding 服务不可用时，自动降级为关键词检索，返回 HTTP 200（而非 500）
6. 检索日志 `rag_search_log` 有完整记录，可复盘
7. 按用户隔离检索：用户 A 的检索无法返回用户 B 的切片
