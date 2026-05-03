# QA_Agent 项目现状总结与 V2 规划

## 一、V1 核心资产 CRUD 完成情况

### 1.1 总体判断

**V1 核心资产 CRUD 约完成 85%，主链路基本可跑通，但存在 2 个缺失端点、2 个实现缺陷、3 张表完全无代码覆盖，前端练习/结果页仍为假数据占位。**

---

### 1.2 已完成项（前后端均已接入）

| 模块 | 表 | 后端端点 | 前端对接 |
|------|-----|---------|---------|
| Auth | `user_account` | register / login / refresh / me / send-verify-code | LoginPage / RegisterPage |
| 账号管理 | `user_account` | detail / query / create / update / delete / avatar | ProfilePage（头像上传） |
| 用户画像 | `user_profile` | detail(me) / query / create / update / delete | ProfilePage（完整表单） |
| 资料库 | `source_document` | detail / query / update / delete | RepositoryPage（查看/编辑/删除） |
| 资料切片 | `document_chunk` | detail / query / create / update / delete | 前端暂无直接操作入口 |
| 问答集 | `qa_set` | detail / query / update / delete | RepositoryPage / QuizPage |
| 题目 | `qa_item` | detail / query / create / update / delete | RepositoryPage（题目表编辑） |
| 练习会话 | `practice_session` | detail / query / create / update / delete | 前端未接入 |
| 练习明细 | `practice_session_item` | detail / query / create / update / delete | 前端未接入 |

---

### 1.3 缺失端点（服务层已实现，Controller 未暴露）

| 端点 | 说明 |
|------|------|
| `POST /source-document/create` | `IDocumentCrudService.createSourceDocument()` 已实现，DocumentController 漏暴露 |
| `POST /qa-set/create` | `IQaCrudService.createQaSet()` 已实现，QaController 漏暴露 |

> 影响：前端无法通过 API 新建资料和新建问答集。目前 RepositoryPage 的资料/问答集展示依赖 seed.sql 预置数据。

---

### 1.4 实现缺陷

| 问题 | 位置 | 详述 |
|------|------|------|
| 资料删除为真删而非软删 | `DocumentRepository.deleteSourceDocument()` | 直接调 `mapper.deleteById(id)`，与 TABLE.md（`deleted=true`）和 API.md（软删除）定义不一致 |
| 问答集删除未做级联 | `QaRepository.deleteQaSet()` | 直接调 `mapper.deleteById(id)`，未级联删除 qa_item / practice_session / practice_session_item / qa_set_document_ref。与 API.md 声明不符 |

---

### 1.5 完全未实现的表（有 DDL、无 Java 代码）

| 表 | 用途 | 影响 |
|----|------|------|
| `qa_generation_task` | 问答集生成异步任务 | 创建问答集链路无法走异步任务，CreatePage 提交按钮只能 `alert("接口尚未实现")` |
| `qa_generation_task_message` | 任务阶段消息 | 同上，无法展示生成进度（PLAN → SEARCH → DRAFT → VALIDATE → FINALIZE） |
| `qa_set_document_ref` | 问答集-资料多对多关系 | 问答集无法记录关联的资料，与 PRD 核心资产关系设计存在差距 |

---

### 1.6 前端未完成页面

| 页面 | 当前状态 | 缺失内容 |
|------|---------|---------|
| CreatePage | 表单 UI 完成 | 提交按钮 `alert("接口尚未实现")`；需后端 `qa_generation_task` 链路 |
| QAPage (`/practice/:sessionId`) | 完全假数据 | 所有交互按钮均为 `alert("接口尚未实现")`；未调用任何练习相关 API |
| ResultPage (`/result/:sessionId`) | 完全假数据 | 未调用 practice_session detail API；注有"评分链路尚未接入" |
| QuizPage | 浏览已接入 | "开始练习"/"继续测试" 按钮 `alert("接口尚未实现")`；需创建 practice_session |

---

### 1.7 V1 收尾建议（按优先级排序）

1. **补充缺失端点**：DocumentController 加 `create`，QaController 加 `create`（约 5 行代码/个）
2. **修复软删除**：DocumentRepository.deleteSourceDocument 改为 update `deleted=true`
3. **实现 qa_set_document_ref CRUD**：新增 Entity/Mapper/Repository/Service/Controller，支撑问答集-资料关联
4. **对接前端练习链路**：QAPage / ResultPage / QuizPage 接入已有 practice_session API
5. **实现 qa_generation_task**（可放 V2 初期或 V1 收尾）：打通 CreatePage 的完整创建流程
6. **修复级联删除**：QaRepository.deleteQaSet 加入对 qa_item / practice_session / qa_set_document_ref 的级联处理（或依赖 DB 外键 CASCADE）

---

## 二、V2 目标与方案

### 2.1 V2 目标

根据 PRD.md 第 8.2 节，**V2 的核心目标是完成真正可用的 RAG 设计与落地**，具体包括：

1. 资料结构化解析（Markdown → 标题层级 → 切片）
2. 切片与向量化（`document_chunk` + `embedding_vector`）
3. 混合召回（语义检索 + 关键词检索）
4. 结构感知检索（标题路径过滤、模块标签过滤）
5. 召回后重排序
6. 基于 Agent 任务目标的证据裁剪

V2 的产出是**后续生成/反馈/评分 Agent 可复用的统一证据底座**。

同时结合当前实际缺口，V2 还应包含：
- `qa_generation_task` 异步任务链路（V3 生成 Agent 的前置依赖）
- `qa_set_document_ref` 多对多关系 CRUD（资产关系底座）

---

### 2.2 技术选型：LangChain4J vs Spring AI

**结论：选用 LangChain4J。**

原因：

| 维度 | LangChain4J | Spring AI |
|------|------------|-----------|
| 社区成熟度 | 更早发布，社区沉淀更深 | 较新，Spring 生态原生集成 |
| Agent DAG 支持 | 原生支持 Chain/Agent/Tool 编排，适合 PLAN→SEARCH→DRAFT→VALIDATE→FINALIZE 的链路式 Agent | 更偏 Chat/Embedding 单步调用，DAG 编排能力较弱 |
| RAG 支持 | 内置 `EasyRag`、`AdvancedRag`、`DocumentSplitter`、`EmbeddingStore` 等多级 RAG 组件 | 有 `SimpleVectorStore`，但混合召回/重排序需较多手动组装 |
| 中文场景 | 社区有较多中文切分/检索实践 | 中文场景案例较少 |
| 与现有架构契合 | PRD 5.3 节已明确选择 LangChain4J | - |
| 异步链路 | 天然支持 `CompletableFuture` + 阶段回调 | 需更多手动编排 |
| Postgres pgvector | `PgVectorEmbeddingStore` 开箱即用 | 需通过 `JdbcTemplate` 手动集成 |

Spring AI 的优势在于与 Spring Boot 配置体系无缝集成，但在多阶段 Agent DAG（V3-V5 核心需求）和混合 RAG 召回（V2 核心需求）两个关键能力上，LangChain4J 更匹配 PRD 设计的链路形态。

---

### 2.3 核心链路

```
用户上传 Markdown 资料
       │
       ▼
  source_document（资料主表，存 raw_content）
       │
       ▼
  Markdown 结构化解析（标题层级 → 切片）
       │
       ▼
  document_chunk（切片表，含 title_path / content / summary / module_tags_json）
       │
       ▼
  向量化（LangChain4J Embedding → PostgreSQL pgvector embedding_vector 字段）
       │
       ▼
  混合检索引擎
  ├── 语义检索（pgvector cosine similarity）
  ├── 关键词检索（MySQL/PostgreSQL full-text search）
  ├── 结构过滤（title_path / module_tag 过滤）
  └── 重排序（Re-ranking）
       │
       ▼
  证据裁剪（按 Agent 任务目标裁剪召回结果）
       │
       ▼
  服务下游 Agent（V3 生成 / V4 反馈 / V5 评分）
```

---

### 2.4 需要实现的功能清单

#### 2.4.1 资料结构化解析（Markdown → 切片）

- Markdown 解析器：按标题层级（H1-H6）拆分章节
- 切片策略：按章节为最小单元，过长的内容段按段落二次切分
- `title_path` 生成：如 `Redis > 数据结构 > 跳表`
- `module_tags_json`：从标题和内容中提取模块标签（初始可手动，后续可由 Agent 自动生成）
- 切片摘要生成（可选，先留空或由 V3 生成 Agent 填充）

#### 2.4.2 向量化

- 接入 LangChain4J 的 `EmbeddingModel`（可选本地模型或 OpenAI Embedding API）
- 将 `document_chunk.content` 向量化写入 `embedding_vector`（JSON 字段，存 float 数组）
- PostgreSQL pgvector 索引（IVFFlat 或 HNSW）

#### 2.4.3 混合检索

- 语义检索：pgvector `<=>` 余弦距离排序
- 关键词检索：MySQL `MATCH...AGAINST` 或 PostgreSQL `tsvector`
- 混合融合：RRF（Reciprocal Rank Fusion）合并两种检索结果
- 结构过滤：在检索前按 `title_path` / `module_tag` 过滤切片范围

#### 2.4.4 qa_generation_task 异步任务链路

- 新增 Entity/Mapper/Repository/Service/Controller
- 创建任务 → Kafka 异步消息 → 阶段推进（PLAN → SEARCH → DRAFT → VALIDATE → FINALIZE）
- 阶段消息写入 `qa_generation_task_message`
- 完成后产出 `qa_set` + `qa_item` + `qa_set_document_ref`
- 前端 CreatePage 接入：显示实时进度、各阶段状态

#### 2.4.5 前端练习链路完善

- QuizPage：创建 practice_session 替换 alert
- QAPage：接入 practice_session_item API，实现提交答案/标记不会/下一题
- ResultPage：接入 practice_session detail API，展示真实结果

#### 2.4.6 qa_set_document_ref CRUD

- 新增 Entity/Mapper/Repository/Service/Controller
- 支撑创建/查看问答集时关联资料

---

### 2.5 V2 实施顺序建议

1. **补 V1 欠账**（缺失端点 + 软删除修复 + 级联删除修复）
2. **qa_set_document_ref CRUD**（资产关系底座，代码量小）
3. **资料结构化解析**（Markdown 切分器）
4. **向量化 + pgvector 检索**（LangChain4J 集成）
5. **混合检索引擎**（语义 + 关键词 + 结构过滤 + RRF）
6. **qa_generation_task 异步链路**（Kafka + 阶段推进）
7. **前端 CreatePage / QuizPage / QAPage / ResultPage 对接**

其中第 3-5 步是 RAG 核心，第 6 步是连接 V2（RAG）和 V3（生成 Agent）的桥梁。
