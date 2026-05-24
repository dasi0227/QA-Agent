# V6 Memory 用户画像设计

日期：2026-05-24

## 1. 背景与目标

`QA_Agent` 当前已经完成资料资产、RAG、生成、反馈和评估链路。V5 Assess 已经在 `practice_session.memory_clue_json` 中保存内部记忆线索，但这些线索还不是正式 Memory。

V6 Memory 的目标是：基于用户真实练习历史，沉淀长期可查看、可追溯、可用于训练策略的用户学习画像。

Memory 不是聊天上下文记忆，也不是用户手写笔记。它描述的是用户在持续做题过程中的稳定表现，例如：

1. 哪些模块已经掌握。
2. 哪些模块理解不清。
3. 是否存在明显不会、空答、概念混淆。
4. 是否存在表达结构、完整度、组织方式上的稳定问题。

本次设计同时预留 GenerateAgent 使用 Memory 的入口，但第一阶段不让 Memory 实际影响生成结果。

## 2. 非目标

1. 不把 Memory 做成通用聊天记忆。
2. 不允许用户编辑 Memory 内容。
3. 不支持物理删除 Memory。
4. 不让 AssessAgent 直接写入 Memory 表。
5. 不让 LLM 直接决定数据库最终写入、合并和置信度。
6. 不让 Memory 参与答案事实生成、`sourceChunkIds` 归因或 `sourceReliable` 判断。
7. 第一版不做 `CONCEPT` 粒度目标对象，避免知识点归一化漂移。

## 3. 核心原则

### 3.1 Memory 是学习画像

一条 Memory 表达一个结构化画像：

```text
用户在某个对象上表现出某类学习状态，具体表现为某段画像内容，证据来自若干真实练习记录。
```

例如：

```text
用户在 Redis 模块上表现出 UNCLEAR，具体表现为多次混淆 RDB 和 AOF 的恢复速度、数据完整性和写入成本取舍，证据来自最近 3 道 Redis 题。
```

### 3.2 LLM 只生成候选画像

LLM 适合从一轮练习中归纳“可能值得长期记住的用户画像”，但不能直接写库。

```text
LLM 负责提出候选画像
Java 负责验证候选画像是否有真实证据
Repository 负责持久化当前画像和证据链
```

### 3.3 Memory 必须可追溯

正式 Memory 必须有证据。没有真实 `practice_session_item` 支撑的候选画像，不写入正式 Memory。

### 3.4 Memory 只影响训练策略

Memory 后续可以传给 PlanAgent，用于影响模块分配、题型倾向和难度起点。

Memory 不传给 DraftAgent，避免污染答案内容、资料证据和 `sourceChunkIds`。

## 4. 已确认取舍

1. Memory 是 `domain` 下的新领域，后端新增 `IMemoryRepository`。
2. Assess 保存完成后通过 MQ 异步触发 Memory 沉淀。
3. MemoryConsumer 调用 Memory 领域服务，不在 Assess 内部直接落 Memory。
4. MemoryAgent 第一版是单节点 Agent，类似 AssistAgent / CompleteAgent。
5. MemoryAgent 只输出候选画像数组。
6. Java 做 evidence 校验、合并、置信度计算和落库。
7. Memory 状态只做 `ACTIVE` / `HIDDEN`。
8. 用户可隐藏 Memory，不可编辑，不做物理删除。
9. `targetType` 只做 `MODULE` / `BEHAVIOR` / `GENERAL`。
10. `MODULE` 的 `targetKey` 必须使用既有固定模块 tag 池。
11. `BEHAVIOR` 的 `targetKey` 必须使用固定行为枚举。
12. `GENERAL` 的 `targetKey` 固定为 `GENERAL`。
13. Memory 类型收敛为 `EXPRESSION` / `AWFUL` / `UNCLEAR` / `MASTER`。
14. GenerateAgent 先预留 `UserMemoryProvider`，当前总是返回空，不实际启用。

## 5. Memory 类型

`memory_type` 描述这条画像的性质。

```text
EXPRESSION
表达结构、完整度、组织方式有问题。

AWFUL
不会、空答、基础缺口明显，或者明显错误模式、概念混淆。

UNCLEAR
概念或机制理解不稳定。不是完全不会，但理解不牢、边界不清、容易漏关键点。

MASTER
稳定掌握的能力点。
```

与现有 V5 `MemoryClueType` 的兼容映射：

```text
CONCEPT_WEAKNESS     -> UNCLEAR
EXPRESSION_WEAKNESS  -> EXPRESSION
MISTAKE_PATTERN      -> AWFUL
UNKNOWN_PATTERN      -> AWFUL
STABLE_STRENGTH      -> MASTER
```

## 6. 目标对象

`target_type` 和 `target_key` 表示这条 Memory 挂载在哪里。

```text
target_type = 对象类型
target_key  = 系统稳定匹配 key
```

第一版不保存 `target_label`。展示名称由枚举或模块 tag 映射得到，真正面向用户的内容放在 `title`、`summary`、`detail`。

### 6.1 MODULE

用于模块级画像。

```text
target_type = MODULE
target_key = 固定模块 tag
```

`target_key` 必须命中既有模块池：

```text
JavaSE,OOP,JVM,IO,JUC,JCF,MCP,SKILL,AGENT,Harness,SpringAI,LangChain4J,
SpringFramework,SpringMVC,SpringBoot,SpringCloud,MyBatis,MySQL,PostgreSQL,
Redis,MQ,Linux,Docker,Maven,Git,Zookeeper,Elasticsearch,K8s,Grafana,
分布式,高并发,微服务,设计模式,数据结构与算法,计算机网络,操作系统,测试,运维,安全
```

示例：

```text
memory_type = UNCLEAR
target_type = MODULE
target_key = Redis
```

含义：用户在 Redis 模块理解不稳定。

### 6.2 BEHAVIOR

用于回答行为画像。

```text
target_type = BEHAVIOR
target_key = 固定行为枚举
```

第一版行为枚举建议：

```text
MISSING_TRADEOFF        缺少取舍边界
DEFINITION_ONLY         只背定义
UNSTRUCTURED_ANSWER     回答结构松散
SCENARIO_WEAK           场景迁移弱
CAUSE_ANALYSIS_WEAK     原因分析不足
TERMINOLOGY_INACCURATE  术语不准确
```

示例：

```text
memory_type = EXPRESSION
target_type = BEHAVIOR
target_key = MISSING_TRADEOFF
```

含义：用户表达上经常缺少取舍边界。

### 6.3 GENERAL

用于整体学习画像。

```text
target_type = GENERAL
target_key = GENERAL
```

适用于无法稳定归入模块或行为枚举，但确实有长期价值的整体画像。

## 7. 画像内容

画像内容是展示给用户看的长期学习判断，不是单题反馈，也不是单轮评估摘要。

建议字段：

```text
title
summary
detail
```

示例：

```text
title = Redis 持久化取舍不稳定

summary = 你在 Redis 持久化相关题目中多次漏掉恢复速度、数据完整性和写入成本的取舍。

detail = 本轮回答能覆盖 RDB 和 AOF 的基本定义，但在 AOF rewrite、fsync 策略和生产选型边界上缺失较多。后续应增加场景选型和机制对比题。
```

## 8. 数据库设计

### 8.1 `user_memory`

用途：保存当前生效或被隐藏的长期画像结论。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `CHAR(36)` | 主键 |
| `user_id` | `CHAR(36)` | 用户隔离字段 |
| `memory_type` | `VARCHAR(32)` | `EXPRESSION` / `AWFUL` / `UNCLEAR` / `MASTER` |
| `target_type` | `VARCHAR(32)` | `MODULE` / `BEHAVIOR` / `GENERAL` |
| `target_key` | `VARCHAR(120)` | 模块 tag、行为枚举或 `GENERAL` |
| `title` | `VARCHAR(160)` | 画像短标题 |
| `summary` | `VARCHAR(500)` | 一句话画像结论 |
| `detail` | `TEXT` | 画像详情 |
| `confidence` | `INT` | 0-100，Java 规则计算 |
| `support_count` | `INT` | 支撑证据数量 |
| `status` | `VARCHAR(32)` | `ACTIVE` / `HIDDEN` |
| `first_seen_at` | `DATETIME` | 首次形成时间 |
| `last_seen_at` | `DATETIME` | 最近被证据增强时间 |
| `hidden_at` | `DATETIME` | 用户隐藏时间 |
| `latest_session_id` | `CHAR(36)` | 最近支撑该画像的 session |
| `latest_qa_set_id` | `CHAR(36)` | 最近支撑该画像的题集 |
| `created_at` | `DATETIME` | 创建时间 |
| `updated_at` | `DATETIME` | 更新时间 |

建议唯一约束：

```text
user_id + memory_type + target_type + target_key
```

同一用户同一画像对象不重复建 Memory，而是增强已有记录。

建议索引：

```text
idx_user_memory_user_status_updated(user_id, status, updated_at)
idx_user_memory_user_type_target(user_id, memory_type, target_type, target_key)
```

### 8.2 `user_memory_evidence`

用途：保存支撑某条 Memory 的真实练习证据。

`user_memory` 与 `user_memory_evidence` 是一对多关系。

```text
一条 user_memory
  -> 多条 user_memory_evidence
```

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `CHAR(36)` | 主键 |
| `memory_id` | `CHAR(36)` | 对应 `user_memory.id` |
| `user_id` | `CHAR(36)` | 用户隔离字段 |
| `session_id` | `CHAR(36)` | 练习会话 |
| `session_item_id` | `CHAR(36)` | 练习单题记录 |
| `qa_set_id` | `CHAR(36)` | 题集 |
| `qa_item_id` | `CHAR(36)` | 题目 |
| `module_tag` | `VARCHAR(120)` | 题目模块快照 |
| `question_snapshot` | `LONGTEXT` | 题干快照 |
| `result` | `VARCHAR(32)` | 单题结果 |
| `score` | `INT` | 单题分数 |
| `source_chunk_ids_json` | `JSON` | 题目来源 chunk 快照 |
| `memory_clue_json` | `JSON` | 本轮被使用的 clue |
| `evidence_summary` | `VARCHAR(500)` | 证据摘要 |
| `created_at` | `DATETIME` | 创建时间 |

建议唯一约束：

```text
memory_id + session_item_id
```

避免同一题重复作为同一 Memory 的证据。

建议索引：

```text
idx_memory_evidence_memory_created(memory_id, created_at)
idx_memory_evidence_user_session(user_id, session_id)
```

### 8.3 数据来源

`user_memory_evidence` 来自已完成 Assess 的真实练习数据：

```text
practice_session
practice_session_item
qa_item
practice_session.memory_clue_json
practice_session.assessment_detail_json
```

如果 LLM 输出的候选画像找不到任何真实 `session_item_id` 证据，则不写 `user_memory`，也不写 `user_memory_evidence`。

## 9. 异步沉淀链路

完整链路：

```text
用户完成一轮练习
  -> AssessAgent 同步生成整轮评估
  -> saveAssessResult 保存 assessment_detail_json / memory_clue_json
  -> 发布 MQ: memory.ingest
  -> MemoryConsumer 消费
  -> MemoryService.ingestAssessSession(sessionId, userId)
  -> 读取完整 session / item / qaItem / clue / existing memories
  -> MemoryAgent 生成候选画像
  -> MemoryResultCleaner 清洗字段
  -> MemoryService 校验证据、合并、计算 confidence
  -> IMemoryRepository 写 user_memory / user_memory_evidence
```

MQ 消息建议：

```json
{
  "sessionId": "practice-session-id",
  "userId": "user-id"
}
```

任务 ID 建议：

```text
memory_{sessionId}
```

如果同一个 session 重发消息，Memory 领域服务必须依靠 `memory_id + session_item_id` 唯一约束和 upsert 逻辑保持幂等。

## 10. MemoryAgent 设计

第一版 MemoryAgent 是单节点 Agent。

职责：

```text
输入真实练习证据和已有 ACTIVE Memory 摘要。
输出候选画像数组。
```

它不做：

```text
不写库
不合并
不计算最终 confidence
不恢复 HIDDEN Memory
不决定最终是否入库
```

### 10.1 输入

建议上下文命名为 `MemoryContext`。

字段：

```text
sessionId
qaSetTitle
statsJson
itemsJson
memoryCluesJson
existingMemoriesJson
retryHint
```

`statsJson` 来自 Java 统计：

```json
{
  "totalQuestions": 10,
  "score": 72,
  "accuracy": 70.00,
  "perfectCount": 1,
  "correctCount": 3,
  "deficientCount": 4,
  "wrongCount": 1,
  "unknownCount": 1
}
```

`itemsJson` 每题建议包含：

```json
{
  "sessionItemId": "xxx",
  "qaItemId": "xxx",
  "question": "Redis 的 RDB 和 AOF 有什么区别？",
  "moduleTag": "Redis",
  "difficulty": "MEDIUM",
  "standardAnswer": "xxx",
  "userAnswer": "xxx",
  "result": "DEFICIENT",
  "score": 65,
  "feedbackSummary": "能说出定义，但缺少恢复速度和数据安全取舍。",
  "missingPoints": ["AOF rewrite", "fsync 策略"],
  "wrongPoints": [],
  "sourceChunkIds": ["chunk1", "chunk2"]
}
```

`memoryCluesJson` 来自 V5 RecordAgent：

```json
[
  {
    "type": "CONCEPT_WEAKNESS",
    "observation": "对 Redis 持久化机制的恢复速度和数据完整性取舍理解不稳定。",
    "importance": "HIGH"
  }
]
```

`existingMemoriesJson` 只包含当前用户 ACTIVE Memory 摘要：

```json
[
  {
    "memoryId": "xxx",
    "memoryType": "UNCLEAR",
    "targetType": "MODULE",
    "targetKey": "Redis",
    "title": "Redis 持久化取舍不稳定",
    "summary": "多次混淆 RDB、AOF 和 AOF rewrite 的机制取舍。",
    "confidence": 78,
    "supportCount": 2
  }
]
```

HIDDEN Memory 不传给 LLM。

### 10.2 输出

根节点输出 JSON 数组，最多 5 条。

```json
[
  {
    "memoryType": "UNCLEAR",
    "targetType": "MODULE",
    "targetKey": "Redis",
    "title": "Redis 持久化取舍不稳定",
    "summary": "你在 Redis 持久化相关题目中多次漏掉恢复速度、数据完整性和写入成本的取舍。",
    "detail": "本轮回答能覆盖 RDB 和 AOF 的基本定义，但在 AOF rewrite、fsync 策略和生产选型边界上缺失较多。",
    "evidenceRefs": ["sessionItemId-1", "sessionItemId-2"],
    "relatedMemoryId": "",
    "confidenceHint": "HIGH"
  }
]
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `memoryType` | `EXPRESSION` / `AWFUL` / `UNCLEAR` / `MASTER` |
| `targetType` | `MODULE` / `BEHAVIOR` / `GENERAL` |
| `targetKey` | 模块 tag、行为枚举或 `GENERAL` |
| `title` | 候选画像标题 |
| `summary` | 候选画像摘要 |
| `detail` | 候选画像详情 |
| `evidenceRefs` | 支撑该画像的 `sessionItemId` 数组 |
| `relatedMemoryId` | 如果认为是在增强已有 ACTIVE Memory，填已有 memoryId，否则空字符串 |
| `confidenceHint` | `LOW` / `MEDIUM` / `HIGH`，只作为 Java 计算参考 |

### 10.3 Prompt 边界

MemoryAgent prompt 必须明确：

1. 只输出候选画像，不输出正式 Memory。
2. `evidenceRefs` 只能从输入 `itemsJson.sessionItemId` 中选择。
3. 没有真实题目支撑时不要输出。
4. 不要一题一条，必须合并同类问题。
5. 不要输出安慰话术。
6. 不要生成知识事实结论，只描述用户学习表现。
7. 已有 ACTIVE Memory 可通过 `relatedMemoryId` 复用，避免重复画像。
8. 不会提供 HIDDEN Memory，不要推测或恢复隐藏记忆。
9. `targetType=MODULE` 时，`targetKey` 必须来自固定模块池。
10. `targetType=BEHAVIOR` 时，`targetKey` 必须来自固定行为枚举。
11. `targetType=GENERAL` 时，`targetKey` 必须是 `GENERAL`。

## 11. Java 后置规则

`MemoryResultCleaner` 和 `MemoryService` 必须做以下控制：

1. 过滤非法 `memoryType`。
2. 过滤非法 `targetType`。
3. `MODULE` 的 `targetKey` 必须命中模块池。
4. `BEHAVIOR` 的 `targetKey` 必须命中行为枚举。
5. `GENERAL` 的 `targetKey` 必须等于 `GENERAL`。
6. `title`、`summary`、`detail` 做长度裁剪。
7. `evidenceRefs` 必须存在于本轮 session items。
8. `evidenceRefs` 不能为空，否则丢弃候选画像。
9. `relatedMemoryId` 必须属于当前用户且为 ACTIVE，否则忽略。
10. 最多保留 5 条候选画像。
11. 同一候选画像内的重复 evidence 去重。
12. 同一 `user_id + memory_type + target_type + target_key` 合并为同一 Memory。
13. HIDDEN Memory 不自动恢复，也不参与 GenerateAgent 输入。

## 12. 合并与置信度

合并键：

```text
user_id + memory_type + target_type + target_key
```

如果已有 ACTIVE Memory：

```text
更新 title / summary / detail
support_count 增加新增 evidence 数量
last_seen_at 更新为当前时间
latest_session_id / latest_qa_set_id 更新
confidence 重新计算
插入新增 user_memory_evidence
```

如果已有 HIDDEN Memory：

```text
不恢复为 ACTIVE
不展示
不参与 GenerateAgent
第一版跳过更新，不追加 evidence
```

推荐第一版：HIDDEN 直接跳过更新，避免用户隐藏后又被系统持续增强。

如果不存在 Memory：

```text
新建 ACTIVE Memory
support_count = evidence 数量
first_seen_at = now
last_seen_at = now
confidence = 初始置信度
插入 user_memory_evidence
```

置信度建议 Java 规则计算：

```text
confidence = baseByConfidenceHint + evidenceScore + supportBonus
```

简单起步：

```text
LOW    -> 45
MEDIUM -> 60
HIGH   -> 75

每条新增 evidence +5
最高 95
最低 0
```

后续可结合 `result`、`score`、是否多轮出现、是否跨题集出现继续优化。

## 13. 后端分层设计

### 13.1 Domain

新增：

```text
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/memory/
```

建议结构：

```text
memory/model/entity/UserMemory.java
memory/model/entity/UserMemoryEvidence.java
memory/model/enumeration/MemoryType.java
memory/model/enumeration/MemoryTargetType.java
memory/model/enumeration/MemoryStatus.java
memory/model/enumeration/MemoryBehaviorKey.java
memory/repository/IMemoryRepository.java
memory/service/IMemoryService.java
memory/service/MemoryService.java
```

MemoryService 负责：

```text
读取沉淀上下文
调用 MemoryAgent
清洗候选画像
校验证据
合并 Memory
调用 IMemoryRepository 落库
隐藏 Memory
查询 Memory 列表和详情
```

### 13.2 Agent

MemoryAgent 属于 agent 能力，但服务 Memory 领域。

建议结构：

```text
domain/agent/service/memory/MemoryAgent.java
domain/agent/service/memory/IMemoryAgent.java
domain/agent/service/memory/subagent/ExtractMemoryAgent.java
domain/agent/service/memory/model/context/MemoryContext.java
domain/agent/service/memory/model/result/MemoryCandidateResult.java
domain/agent/service/memory/support/MemoryResultCleaner.java
```

第一版只有单节点 `ExtractMemoryAgent`，不引入复杂 DAG。

### 13.3 Infrastructure

新增：

```text
UserMemory
UserMemoryEvidence
UserMemoryMapper
UserMemoryEvidenceMapper
MemoryRepository
```

`MemoryRepository` 实现 `IMemoryRepository`。

### 13.4 Interfaces

新增 MQ Consumer：

```text
MemoryConsumer
```

监听 topic：

```text
qa.memory.ingest
```

新增 Memory 查询接口建议：

```text
GET /memory/list
GET /memory/detail/{memoryId}
POST /memory/hide/{memoryId}
```

查询只返回当前用户自己的 Memory。

隐藏接口只把 `status` 改为 `HIDDEN`，不物理删除。

## 14. GenerateAgent 预留设计

新增：

```text
UserMemoryProvider
```

位置：

```text
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/shared/UserMemoryProvider.java
```

第一阶段行为：

```text
getGenerationMemory(userId) 永远返回空字符串或空数组 JSON。
```

GenerateAgent 只做结构预埋：

```text
读取 UserMemoryProvider
传入 PlanContext
PlanAgent prompt 预留 memoryProfile 输入
当前为空，不影响生成
```

后续真实启用时：

```text
只查询 ACTIVE Memory
只给 PlanAgent
不传给 DraftAgent
不参与 sourceChunkIds
不参与 sourceReliable
```

PlanAgent 可使用 Memory 调整：

```text
模块题量分配
retrievalQueries 方向
题型倾向
难度起点
```

示例：

```text
AWFUL + MODULE + Redis
  -> 增加 Redis 基础题和纠错题，降低难度起点

UNCLEAR + MODULE + Redis
  -> 增加机制题、对比题、边界题、场景题

EXPRESSION + BEHAVIOR + MISSING_TRADEOFF
  -> 增加方案对比和取舍题

MASTER + MODULE + JVM
  -> 降低 JVM 重复训练权重
```

## 15. 前端设计

Memory 页面在 Profile 下：

```text
/profile/memory
```

第一版展示：

```text
长期画像列表
按 memoryType 或 targetType 分组
显示 title / summary / targetKey / confidence / supportCount / lastSeenAt
支持查看详情
支持隐藏
```

详情展示：

```text
title
summary
detail
memoryType
targetType
targetKey
confidence
supportCount
证据列表
```

证据列表展示：

```text
题集
题目
结果
分数
证据摘要
创建时间
```

用户操作：

```text
隐藏这条记忆
```

不提供编辑，不提供物理删除。

HIDDEN Memory 默认不展示。后续如果需要，可增加“已隐藏记忆”入口，但第一版不做。

## 16. 风险与边界

### 16.1 LLM 画像不准

风险：LLM 把偶发错误总结成长期画像。

控制：

```text
必须有 evidenceRefs
必须引用真实 session item
置信度由 Java 计算
前端展示 support_count
用户可隐藏
```

### 16.2 模块名漂移

风险：LLM 输出不在模块池中的模块名。

控制：

```text
MODULE targetKey 必须命中固定模块池
不命中则丢弃候选画像
```

### 16.3 行为枚举不足

风险：第一版行为池覆盖不全。

控制：

```text
BEHAVIOR 第一版保持小集合
无法归类时可使用 GENERAL
后续再扩展行为枚举
```

### 16.4 HIDDEN 语义

风险：用户隐藏后，系统又重新生成同类 Memory。

控制：

```text
HIDDEN 不传给 LLM
合并时如果命中 HIDDEN，第一版跳过更新
不自动恢复 ACTIVE
```

### 16.5 GenerateAgent 污染

风险：Memory 被用于答案事实生成。

控制：

```text
Memory 只传 PlanAgent
不传 DraftAgent
不参与答案、sourceChunkIds、sourceReliable
UserMemoryProvider 第一阶段返回空
```

## 17. 分阶段实施建议

### 阶段一：预埋 Generate 接入点

1. 新增 `UserMemoryProvider`。
2. 当前总是返回空。
3. GenerateAgent / PlanContext / PlanAgent prompt 预留 memory 输入。
4. 不改变生成实际行为。

### 阶段二：Memory 表与查询

1. 新增 `user_memory`。
2. 新增 `user_memory_evidence`。
3. 新增 Memory 领域、仓储和查询接口。
4. Profile Memory 页接入真实列表、详情和隐藏。

### 阶段三：异步沉淀

1. Assess 保存后发送 `qa.memory.ingest`。
2. 新增 MemoryConsumer。
3. 新增 MemoryAgent 单节点。
4. MemoryService 完成候选画像校验、合并和落库。

### 阶段四：策略启用

1. `UserMemoryProvider` 查询 ACTIVE Memory。
2. PlanAgent 使用 Memory 调整题集规划。
3. 控制 Memory 只影响训练策略，不影响答案事实。
