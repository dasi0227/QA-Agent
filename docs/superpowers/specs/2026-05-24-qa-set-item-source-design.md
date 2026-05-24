# QA Set Item Source Design

日期：2026-05-24

## 1. 目标

本次设计解决三个已确认问题：

1. 手动新增题目只能一次创建一道，前端需要发起多次请求并维护多条补全状态轮询。
2. 当前没有“空题集”创建入口，用户无法先创建题集框架再逐步手动加题。
3. GenerateAgent 把模块级 RAG 候选 chunk 全量挂给模块下每一道题，导致 `sourceChunkIdsJson` 失去题目级溯源意义。

最终形态：

- 题目创建接口拆成单题和批量两个入口。
- 空题集通过独立接口创建，不复用 AI 生成接口。
- 空题集手动题补全时不扩大 RAG 范围到用户全部资料。
- `PlanItem.focusTopics` 改为 `retrievalQueries` 数组，专门服务 RAG 检索。
- `DraftAgent` 为每道题输出题目级 `sourceChunkIds`，Java 做白名单校验、去重、截断和兜底。
- `AssistAgent` 继续只负责 `keywords` 和 `hint`，不承担来源 chunk 精筛。

## 2. 已确认取舍

1. 题目新增不把原 `/qa/item/create` 直接改成批量语义，而是拆成 `/qa/item/create/single` 和 `/qa/item/create/batch`。
2. 批量创建只优化题目落库和题数更新；CompleteAgent 仍按单题异步执行。
3. 空题集创建不放开 `/qa/set/task` 和 `/qa/set/create` 的 `documentIds` 必填约束。
4. 题集无关联资料时，CompleteAgent 不执行 RAG 检索，避免搜索用户全部资料。
5. `sourceReliable` 只是资料支撑强弱标记，不驱动 `sourceChunkIds` 清空。
6. AmendAgent 不再修改 `sourceReliable`，也不重新选择 `sourceChunkIds`。
7. DraftAgent 负责首次题目级来源 chunk 选择；Java 负责防止幻觉 ID、控制数量和兜底。
8. 如果 DraftAgent 没有返回合法 chunkId，Java 使用当前 PlanItem evidence 的 top2 chunkId 兜底。
9. Markdown chunk 粒度本次不调整，避免引入资料重索引和历史 chunkId 失效问题。

## 3. 题目创建接口

### 3.1 单题创建

新增接口：

```text
POST /qa/item/create/single
```

请求：

```json
{
  "qaSetId": "qa-set-id",
  "question": "一道面试题"
}
```

响应：单个 `QaItemResponse`。

行为：

```text
校验题集归属
  -> 插入 1 道 qa_item
  -> completeStatus=PROCESSING
  -> sourceChunkIdsJson=[]
  -> qa_set.question_count + 1
  -> 本地线程池异步触发 CompleteAgent
```

### 3.2 批量创建

新增接口：

```text
POST /qa/item/create/batch
```

请求：

```json
{
  "qaSetId": "qa-set-id",
  "questions": [
    "第一道面试题",
    "第二道面试题"
  ]
}
```

响应：`List<QaItemResponse>`。

行为：

```text
校验题集归属
  -> 过滤空问题并保持输入顺序
  -> 查询当前最大 sortOrder
  -> 批量插入 N 道 qa_item
  -> 每道题 completeStatus=PROCESSING
  -> sortOrder 连续递增
  -> qa_set.question_count 一次性 + N
  -> 事务提交后逐题异步触发 CompleteAgent
```

校验规则：

- `qaSetId` 必填。
- `questions` 至少 1 个有效问题。
- 单批最多 50 道题。
- 每个问题 trim 后不能为空。

失败策略：

- 落库事务失败时整批失败，不创建残缺题目。
- 落库成功后，个别 CompleteAgent 失败只影响对应题目的 `completeStatus=UNSOLVED`，不回滚整批。

## 4. 空题集创建

新增接口：

```text
POST /qa/set/empty
```

请求：

```json
{
  "title": "操作系统面试题",
  "description": "可选描述"
}
```

响应：`QaSetResponse`。

落库行为：

```text
创建 qa_set
  -> task_id=null
  -> title=请求标题
  -> description=请求描述或空字符串
  -> module_tags_json=[]
  -> question_count=0
  -> practice_count=0
  -> average_score=0
  -> best_score=0
  -> average_accuracy=0
  -> best_accuracy=0
  -> 不创建 qa_set_document_ref
  -> 不递增 source_document.reference_count
```

保留现有 AI 生成路径：

```text
POST /qa/set/task
POST /qa/set/create
```

这两个接口继续使用 `CreateQaSetRequest`，`documentIds` 仍然必填。

## 5. CompleteAgent 空资料行为

当前问题：

```text
空题集
  -> qa_set_document_ref 为空
  -> CompleteContext.documentIds=[]
  -> RagEvidenceProvider 传空 filterDocumentIds
  -> RAG SQL 不加 document_id 条件
  -> 检索范围扩大到当前用户全部资料
```

调整后：

```text
CompleteAgent.execute()
  -> AgentRepository.getCompleteContext()
  -> 如果 documentIds 为空：
       evidence=[]
       不调用 RagEvidenceProvider.searchByQuestion()
  -> 如果 documentIds 非空：
       只在题集关联资料内检索
```

输出语义：

- 空题集手动题可以继续由 CompleteAgent 用通用知识补全。
- 这类题默认没有资料 chunk 来源，`sourceChunkIds=[]`。
- `sourceReliable` 由 CompleteAgent 按 prompt 判断，不强制绑定其他逻辑。

## 6. PlanItem 与 RAG 检索

### 6.1 字段调整

`PlanItem.focusTopics` 改为 `retrievalQueries`，并从字符串改为数组。

调整前：

```json
{
  "module": "Redis",
  "questionCount": 5,
  "focusTopics": "RDB,AOF,持久化恢复",
  "keyConcepts": "..."
}
```

调整后：

```json
{
  "module": "Redis 持久化",
  "questionCount": 5,
  "retrievalQueries": [
    "RDB 快照生成与恢复机制",
    "AOF 刷盘策略与数据安全取舍",
    "AOF rewrite 文件压缩机制"
  ],
  "keyConcepts": "本模块围绕 Redis 持久化展开，覆盖 RDB、AOF、rewrite、恢复优先级和生产取舍。"
}
```

字段职责：

| 字段 | 职责 |
| --- | --- |
| `module` | 模块名，也作为 RAG query 的上下文前缀 |
| `questionCount` | 当前模块生成题数 |
| `retrievalQueries` | 专门用于 RAG 检索的具体查询主题 |
| `keyConcepts` | 专门用于 DraftAgent 控制出题范围和生成边界 |

### 6.2 Plan prompt 规则

`generate-plan.txt` 需要明确：

1. `retrievalQueries` 是专门用于 RAG 检索的数组，不是展示给用户的话题。
2. 每个 query 必须是具体、可检索、带技术上下文的知识点短语。
3. 禁止输出“基础知识”“核心概念”“应用场景”这类空泛词。
4. 每个 PlanItem 的 `retrievalQueries` 建议 3 到 6 个，避免检索过散。
5. `keyConcepts` 才是给 DraftAgent 的出题范围说明。

### 6.3 RagEvidenceProvider

调整后链路：

```text
PlanItem
  -> 读取 retrievalQueries
  -> 如果 retrievalQueries 为空，回退 module
  -> 每个 query 自动拼接 module：
       module + " " + retrievalQuery
  -> 每个 query 执行一次 RAG search
  -> 合并结果
  -> 按 chunkId 去重
  -> 保持结果顺序用于 top2 兜底
```

当前 RAG 单次查询最终 topK 是 10。`retrievalQueries` 有多个元素时，一个 PlanItem 合并后的 evidence 可能超过 10。

## 7. GenerateAgent 来源 chunk 精准化

### 7.1 当前链路

```text
PlanItem 模块
  -> RagEvidenceProvider 搜一批 evidence
  -> 提取该模块全部 chunkId
  -> DraftAgent 生成多道题，但不输出 chunkId
  -> Java 把模块全部 chunkId 赋给每道题
  -> AmendAgent 修订后继续沿用原 chunkId
  -> saveGeneratedQaSet 原样落库
```

### 7.2 调整后链路

```text
PlanItem 模块
  -> RagEvidenceProvider 搜一批 evidence
  -> evidence 包含 chunkId / headingPath / summary / content
  -> Java 提取该模块全部 chunkId，作为候选白名单
  -> DraftAgent 基于 evidence 生成多道题
       每道题输出 sourceChunkIds
       sourceChunkIds 按相关性降序排列
       数量由 AI 自己判断
  -> Java 校验每道题 sourceChunkIds：
       只保留白名单内存在的 chunkId
       去重
       最多保留前 5 个
  -> 如果 AI 返回空或全非法：
       使用当前 PlanItem evidence 的 top2 chunkId 兜底
  -> AmendAgent 只修订题目内容
       不修改 sourceReliable
       不重新选择 sourceChunkIds
  -> Java 保留修订前已校验过的 sourceChunkIds
  -> saveGeneratedQaSet 落库前再做一次去重和最多 5 个兜底
  -> qa_item.source_chunk_ids_json 保存题目级精选后的 chunkId
```

### 7.3 DraftAgent 输出

`generate-draft.txt` 需要增加 `sourceChunkIds`。

输出结构：

```json
[
  {
    "question": "string",
    "knowledgeNote": "string",
    "answer": "string",
    "tag": "string",
    "difficulty": "EASY|MEDIUM|HARD",
    "sourceReliable": true,
    "sourceChunkIds": ["chunk-id"]
  }
]
```

规则：

1. `sourceChunkIds` 只填写直接支撑当前题目 `question / answer / knowledgeNote` 的 chunkId。
2. 必须从输入 evidence 中选择，禁止编造。
3. 按相关性从高到低排序。
4. 不要为了凑数量添加弱相关 chunkId。
5. 无法判断时输出空数组，由 Java 使用 top2 兜底。

### 7.4 AmendAgent 输出

`generate-amend.txt` 需要收缩职责：

- 不再允许修改 `sourceReliable`。
- 不输出 `sourceChunkIds`。
- 只修订 `question / knowledgeNote / answer / tag / difficulty`。

GenerateAgent 在 Amend 后保留修订前已经校验过的 `sourceReliable` 和 `sourceChunkIds`。

### 7.5 Java 校验规则

新增或抽取来源 chunk 清洗逻辑：

```text
输入：
  rawSourceChunkIds
  allowedChunkIds
  fallbackTopChunkIds

输出：
  cleanedSourceChunkIds
```

规则：

1. 只保留 `allowedChunkIds` 中存在的 ID。
2. 去重并保持 AI 输出顺序。
3. 最多保留 5 个。
4. 清洗后为空时，使用 `fallbackTopChunkIds` 的前 2 个。
5. 兜底仍然需要去重和存在性判断。

保存层 `AgentRepository.saveGeneratedQaSet()` 也要做防御性截断，避免未来调用方绕过 GenerateAgent。

## 8. AssistAgent 边界

本次不把 `sourceChunkIds` 精筛移动到 AssistAgent。

原因：

1. `sourceChunkIdsJson` 是核心溯源字段，应在生成落库前完成。
2. AssistAgent 走 Kafka 异步链路，失败或延迟会导致来源字段先脏后净。
3. AssistAgent 当前职责是补 `keywords` 和 `hint`，不应混入来源归因。
4. 当前 `AssistContext.sourceChunks` 已经被组装，但没有传入 `AssistSubAgent` prompt，本次可以暂不处理。

后续如果要使用 `AssistContext.sourceChunks`，更适合用于提升 `keywords / hint` 的资料贴合度，而不是回写核心来源字段。

## 9. 前端适配

### 9.1 空题集入口

问答集列表页或创建弹窗新增“创建空题集”路径：

```text
输入 title
可选输入 description
调用 POST /qa/set/empty
成功后进入题集详情页
```

展示要求：

- `documentCount=0` 时展示“使用资料 0 篇”。
- 空题集详情页要允许继续新增题目。
- 不提示用户选择资料，因为该入口不依赖资料。

### 9.2 题目新增交互

题集详情页新增两种新增方式：

1. 单题新增：输入一个问题，调用 `/qa/item/create/single`。
2. 批量新增：多行文本或列表输入，每行一道题，调用 `/qa/item/create/batch`。

批量新增前端处理：

```text
用户输入多行题目
  -> 前端 trim
  -> 过滤空行
  -> 展示待创建数量
  -> 提交 batch
  -> 根据返回 itemIds 标记本批新增题目
  -> 轮询 /qa/item/query?qaSetId 或现有 query body
  -> 只关注本批 itemIds 的 completeStatus
```

状态展示：

| 状态 | 展示 |
| --- | --- |
| `PROCESSING` | 补全中 |
| `SOLVED` | 已补全 |
| `UNSOLVED` | 补全失败，可重试 |

批量轮询结束条件：

- 本批所有题目都不再是 `PROCESSING`。
- 或达到最大轮询次数后停止，并保留页面状态。

### 9.3 来源 chunk 展示

题目详情或练习来源展示不需要改接口字段，但前端应按 `sourceChunkIdsJson` 顺序展示来源：

```text
第 1 个 chunk = 最相关来源
后续 chunk 按相关性递减
最多 5 个
```

如果 `sourceChunkIdsJson=[]`：

- 不展示来源列表。
- 不把空来源视为错误。

### 9.4 AI 生成任务页

AI 生成流程的请求字段不变：

- `/qa/set/task`
- `/qa/set/create`

前端不需要知道 `focusTopics -> retrievalQueries` 的内部变化，因为 PlanItem 不作为公开生成请求字段。

如果任务状态或阶段消息未来展示 PlanResult 原文，需要兼容旧任务中仍可能存在 `focusTopics` 的历史内容。

## 10. 影响范围

### 10.1 后端文件

预计涉及：

- `backend/qa-agent-types/src/main/java/com/dasi/qa/agent/types/dto/request/qa/CreateQaItemRequest.java`
- 新增批量创建请求 DTO
- 新增空题集创建请求 DTO
- `backend/qa-agent-interfaces/src/main/java/com/dasi/qa/agent/interfaces/controller/QaController.java`
- `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/qa/service/item/IQaItemService.java`
- `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/qa/service/item/QaItemService.java`
- `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/qa/service/set/IQaSetService.java`
- `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/qa/service/set/QaSetService.java`
- `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/qa/repository/IQaRepository.java`
- `backend/qa-agent-infrastructure/src/main/java/com/dasi/qa/agent/infrastructure/repository/QaRepository.java`
- `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/complete/CompleteAgent.java`
- `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/shared/RagEvidenceProvider.java`
- `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/generate/GenerateAgent.java`
- `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/generate/model/result/PlanResult.java`
- `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/generate/model/result/DraftResult.java`
- `backend/qa-agent-application/src/main/resources/prompt/generate/generate-plan.txt`
- `backend/qa-agent-application/src/main/resources/prompt/generate/generate-draft.txt`
- `backend/qa-agent-application/src/main/resources/prompt/generate/generate-amend.txt`
- `backend/qa-agent-infrastructure/src/main/java/com/dasi/qa/agent/infrastructure/repository/AgentRepository.java`

### 10.2 前端文件

预计涉及：

- 问答集列表或创建弹窗组件。
- 题集详情页。
- 题目新增表单组件。
- 题目列表轮询逻辑。
- API client 中 QA set / QA item 方法。
- 如果有 TypeScript 类型，需要新增 batch request 和 empty set request 类型。

### 10.3 文档

需要更新：

- `docs/API.md`
- `docs/V3-Generate-Design.md`

可选更新：

- `docs/TABLE.md`：只更新字段语义说明，不改表结构。
- `docs/PRD.md`：如果要把空题集作为正式资产创建路径写入产品文档。

## 11. 测试与验证

后端验证：

1. 单题创建成功，返回 `PROCESSING`，题集 `questionCount + 1`。
2. 批量创建成功，返回 N 条题目，题集 `questionCount + N`。
3. 批量创建有空行时过滤或拒绝行为符合接口定义。
4. 空题集创建成功，`documentCount=0`，`questionCount=0`。
5. 空题集手动加题时 CompleteAgent 不调用 RAG 全库检索。
6. 有资料题集手动加题时 CompleteAgent 只检索关联资料。
7. Generate 链路中 PlanItem 使用 `retrievalQueries` 数组。
8. DraftAgent 返回合法 `sourceChunkIds` 时按顺序保存，最多 5 个。
9. DraftAgent 返回空或非法 `sourceChunkIds` 时使用 evidence top2 兜底。
10. Amend 后 `sourceReliable` 和 `sourceChunkIds` 保持 GenerateAgent 清洗后的结果。

前端验证：

1. 可以创建空题集并进入详情页。
2. 空题集详情页显示 0 篇资料并可新增题目。
3. 单题新增后出现补全中状态，完成后刷新为已补全。
4. 批量新增后只轮询本批题目状态。
5. 批量中部分题 `UNSOLVED` 时页面允许重试。
6. 来源 chunk 按后端返回顺序展示，空来源不报错。

## 12. 风险

1. `focusTopics` 改名为 `retrievalQueries` 会影响历史 PlanResult 解析；如果历史任务详情展示原始 plan，需要兼容旧字段。
2. 批量创建会一次触发多个 CompleteAgent，可能放大模型调用压力，需要限制单批数量。
3. `sortOrder` 在并发批量创建时可能冲突，批量创建应在事务中基于当前最大值连续分配。
4. DraftAgent 输出 `sourceChunkIds` 增加 JSON 结构复杂度，可能提升格式失败概率，需要保留重试和 fallback。
5. 使用 evidence top2 兜底可能仍不是完全题目级精准来源，但明显优于当前全量挂载。
6. Markdown chunk 暂不调整，若单个 chunk 内容过大，前端来源展示仍可能偏粗。

## 13. 不做范围

本次不做：

1. Markdown chunk 粒度调整和资料重索引。
2. 题集资料关联管理接口。
3. AssistAgent 回写 `sourceChunkIds`。
4. Memory 读写策略调整。
5. 数据表结构变更。
