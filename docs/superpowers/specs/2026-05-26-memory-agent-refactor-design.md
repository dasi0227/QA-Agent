# V6 MemoryAgent 重构设计

日期：2026-05-26

## 1. 背景

当前 V6 Memory 已经有 `user_memory`、`user_memory_evidence`、`MemoryAgent`、`MemoryService` 和 Profile Memory 页面，但实现形态仍存在几个边界问题：

1. `IMemoryService` 同时承担前端查询能力和异步沉淀能力，`ingest(sessionId, userId)` 与 `list/detail/hide` 职责混杂。
2. `MemoryConsumer` 通过 `IMemoryService.ingest()` 触发沉淀，不符合现有 `CompleteAgent.execute()`、`AssistAgent.execute()` 的 Agent 执行风格。
3. `existingMemories` 被传给 LLM，导致“是否合并已有记忆”的判断被模型参与；实际合并应由 Java 按稳定 key 决定。
4. `title / summary / detail` 三段文案边界模糊。Memory 的核心是客观画像内容，不需要标题和摘要分层。
5. 前端维护 Memory 枚举中文映射，业务语义分散在前端，不利于后续统一调整。
6. prompt 仍包含训练建议、已有记忆增强、`relatedMemoryId` 等旧语义，需要收敛。

本设计用于指导下一步重构，不在本文档阶段改代码。

## 2. 目标

Memory 的定位是：基于真实练习和评估结果，沉淀用户长期学习画像。

一条 Memory 只描述客观表现：

```text
用户在什么对象上，表现出什么稳定特征。
```

Memory 不承担：

1. 训练建议。
2. 鼓励或安慰话术。
3. 单轮练习总结。
4. 知识百科。
5. 用户可编辑笔记。

后续训练策略由 GenerateAgent / PlanAgent 读取 Memory 后自行决定，不写入 Memory 正文。

## 3. 核心取舍

1. `MemoryConsumer` 直接调用 `IMemoryAgent.execute(sessionId, userId)`。
2. 删除 `IMemoryService.ingest()`；`IMemoryService` 只服务前端查询和隐藏。
3. `MemoryAgent` 是完整执行入口，负责读取上下文、调用 SubAgent、校验、合并和落库。
4. `InvestAgent` 负责从本轮作答中提取候选画像。
5. `MergeAgent` 负责合并已有画像正文和新候选画像正文。
6. 不再向 LLM 传入 `existingMemories`。
7. 不再使用 `relatedMemoryId`。
8. `user_memory` 正文收敛为 `content`。
9. 不再使用 `title / summary / detail / confidence`。
10. 每轮最多输出 5 条候选画像，强调“最能体现用户发挥、最值得用户注意”的强信号。
11. 枚举中文展示文案由后端枚举属性提供，前端不维护业务枚举映射。

## 4. 数据模型

### 4.1 `user_memory`

`user_memory` 保存长期画像结论。

建议字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `CHAR(36)` | 主键 |
| `user_id` | `CHAR(36)` | 用户 ID |
| `memory_type` | `VARCHAR(32)` | `AWFUL` / `UNCLEAR` / `MASTER` |
| `target_type` | `VARCHAR(32)` | `MODULE` / `BEHAVIOR` / `GENERAL` |
| `target_key` | `VARCHAR(120)` | 模块 tag、行为枚举或 `GENERAL` |
| `content` | `TEXT` | 客观画像正文 |
| `support_count` | `INT` | 支撑证据数量 |
| `status` | `VARCHAR(32)` | `ACTIVE` / `HIDDEN` |
| `first_seen_at` | `DATETIME` | 首次形成时间 |
| `last_seen_at` | `DATETIME` | 最近被证据增强时间 |
| `hidden_at` | `DATETIME` | 隐藏时间 |
| `latest_session_id` | `CHAR(36)` | 最近支撑该画像的练习 |
| `latest_qa_set_id` | `CHAR(36)` | 最近支撑该画像的题集 |
| `created_at` | `DATETIME` | 创建时间 |
| `updated_at` | `DATETIME` | 更新时间 |

唯一合并键：

```text
user_id + memory_type + target_type + target_key
```

这个 key 决定一条候选画像是新建 Memory，还是增强已有 Memory。

### 4.2 `user_memory_evidence`

`user_memory_evidence` 保存 Memory 的证据链，不是另一种记忆。

它说明一条 Memory 为什么可信，来自哪次练习、哪道题、当时表现如何。

建议字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `CHAR(36)` | 主键 |
| `memory_id` | `CHAR(36)` | 对应 `user_memory.id` |
| `user_id` | `CHAR(36)` | 用户 ID |
| `session_id` | `CHAR(36)` | 练习 ID |
| `session_item_id` | `CHAR(36)` | 练习题记录 ID |
| `qa_set_id` | `CHAR(36)` | 题集 ID |
| `qa_item_id` | `CHAR(36)` | 题目 ID |
| `module_tag` | `VARCHAR(120)` | 题目模块快照 |
| `question_snapshot` | `LONGTEXT` | 题目快照 |
| `result` | `VARCHAR(32)` | 评估结果 |
| `score` | `INT` | 得分 |
| `source_chunk_ids_json` | `JSON` | 来源切片快照 |
| `evidence_summary` | `VARCHAR(500)` | 本次证据对画像的支撑说明 |
| `created_at` | `DATETIME` | 创建时间 |

写入规则：

1. `candidate.evidenceRefs` 只能引用本轮真实 `practice_session_item.id`。
2. Java 根据 `evidenceRefs` 找真实 item，找不到的 ref 忽略。
3. 候选画像没有任何真实 evidence 时，不写 `user_memory`，也不写 `user_memory_evidence`。
4. 同一个 `memory_id + session_item_id` 不重复写。

## 5. 枚举

### 5.1 Memory 类型

`MemoryProficientType`：

| code | 文案 | 含义 |
| --- | --- | --- |
| `AWFUL` | 严重薄弱 | 不会、空答、基础缺口明显，或者明显错误模式、概念混淆 |
| `UNCLEAR` | 理解不稳 | 概念或机制理解不稳定，边界不清或漏关键点 |
| `MASTER` | 稳定掌握 | 在相关对象上表现稳定，回答准确完整 |

枚举应定义展示属性，例如：

```java
AWFUL("严重薄弱")
```

### 5.2 目标类型

`MemoryTargetType`：

| code | 文案 | 含义 |
| --- | --- | --- |
| `MODULE` | 模块 | 模块级画像 |
| `BEHAVIOR` | 行为 | 回答行为画像 |
| `GENERAL` | 整体 | 整体学习画像 |

### 5.3 行为枚举

`MemoryBehaviorKey`：

| code | 文案 |
| --- | --- |
| `MISSING_TRADEOFF` | 缺少取舍边界 |
| `DEFINITION_ONLY` | 只背定义 |
| `UNSTRUCTURED_ANSWER` | 回答结构松散 |
| `SCENARIO_WEAK` | 场景迁移弱 |
| `CAUSE_ANALYSIS_WEAK` | 原因分析不足 |
| `TERMINOLOGY_INACCURATE` | 术语不准确 |

`MODULE` 的 `target_key` 必须命中 `ModuleTag` 固定池。

`GENERAL` 的 `target_key` 固定为 `GENERAL`。

## 6. 后端响应

前端需要同时拿到稳定 code 和中文展示值。

`UserMemoryResponse` 建议字段：

```text
id
memoryType
memoryTypeText
targetType
targetTypeText
targetKey
targetKeyText
content
supportCount
status
firstSeenAt
lastSeenAt
hiddenAt
latestSessionId
latestQaSetId
createdAt
updatedAt
```

说明：

1. `memoryType / targetType / targetKey` 仍返回 code，给程序判断、样式和筛选使用。
2. `memoryTypeText / targetTypeText / targetKeyText` 返回中文展示值。
3. `targetKeyText` 的规则：
   - `MODULE`：直接返回模块 tag。
   - `BEHAVIOR`：返回行为枚举中文。
   - `GENERAL`：返回 `整体`。

前端标题可由响应拼接：

```text
targetKeyText + " · " + memoryTypeText
```

示例：

```text
Redis · 理解不稳
缺少取舍边界 · 严重薄弱
整体 · 稳定掌握
```

## 7. Agent 结构

### 7.1 总体链路

```text
Assess 保存完成
  -> MQ: qa.memory.ingest(sessionId, userId)
  -> MemoryConsumer
  -> IMemoryAgent.execute(sessionId, userId)

MemoryAgent.execute
  -> 读取本轮 practice_session / practice_session_item / qa_item 快照
  -> 组装 itemsJson
  -> InvestAgent 提取候选画像
  -> MemoryResultCleaner 校验候选画像
  -> 对每条 candidate：
       根据 evidenceRefs 找真实证据 item
       根据 user_id + memory_type + target_type + target_key 查 user_memory
       HIDDEN -> 跳过
       不存在 -> 新建 user_memory
       ACTIVE -> MergeAgent 合并 content 后更新 user_memory
       写 user_memory_evidence
```

### 7.2 `IMemoryAgent`

接口收敛为：

```java
void execute(String sessionId, String userId);
```

不再暴露：

```java
List<MemoryCandidateResult> extract(MemoryContext context, String userId);
```

`extract` 是内部方法或私有编排细节，不作为对外接口。

### 7.3 `InvestAgent`

职责：从本轮真实评估数据中提取候选画像。

输入：

```text
itemsJson
retryHint
```

不输入：

```text
existingMemoriesJson
statsJson
qaSetTitle
```

输出最多 5 条候选画像：

```json
[
  {
    "memoryType": "AWFUL|UNCLEAR|MASTER",
    "targetType": "MODULE|BEHAVIOR|GENERAL",
    "targetKey": "Redis",
    "content": "用户在 Redis 持久化相关回答中能说出 RDB 和 AOF 的基本概念，但对恢复速度、数据完整性和写入开销的取舍不稳定。",
    "evidenceRefs": ["practiceSessionItemId"]
  }
]
```

prompt 必须强调：

1. 最多输出 5 条候选画像。
2. 只输出本轮最能体现用户发挥、最值得用户注意、且有明确证据支撑的画像。
3. 宁可少输出，不要凑满。
4. 不要覆盖所有模块。
5. 不要一题一条。
6. 同类问题必须合并。
7. `content` 只描述用户学习表现或回答表现，不给训练建议。
8. `content` 不写知识百科，不解释概念本身。
9. `content` 不写鼓励、安慰、评价人格。
10. `evidenceRefs` 只能来自输入 item 的 `sessionItemId`。

### 7.4 `MergeAgent`

职责：当已有 ACTIVE Memory 命中同一个合并 key 时，合并旧正文和新候选正文。

输入：

```text
existingContent
candidateContent
retryHint
```

输出：

```json
{
  "content": "合并后的客观画像内容"
}
```

prompt 必须强调：

1. 只做语义合并，不引入新事实。
2. 去除重复含义。
3. 保留更具体、更稳定、更客观的表现判断。
4. 不给训练建议。
5. 不写知识百科。
6. 不安慰用户，不评价人格。
7. 输出 1-3 段。

示例：

旧内容：

```text
用户在 Redis 持久化相关回答中能说出 RDB 和 AOF 的基本概念，但对恢复速度和数据完整性的取舍不稳定。
```

新内容：

```text
用户在 Redis 相关题目中多次混淆 AOF rewrite、fsync 策略和 RDB 快照触发条件，回答倾向于罗列机制，缺少对生产场景边界的判断。
```

合并后：

```text
用户在 Redis 持久化相关回答中能够说出 RDB 和 AOF 的基本概念，但对恢复速度、数据完整性、写入开销和生产选型边界的取舍不稳定。

用户还容易混淆 AOF rewrite、fsync 策略和 RDB 快照触发条件，回答倾向于罗列机制，缺少对不同持久化策略适用场景的稳定判断。
```

## 8. Cleaner 和 Java 校验

`MemoryResultCleaner` 只做合法性清洗，不做文案优化。

保留：

1. `memoryType` 必须合法。
2. `targetType` 必须合法。
3. `targetKey` 必须合法。
4. `content` 非空。
5. `evidenceRefs` 非空、去重、去空字符串。
6. 最多保留 5 条候选画像。

不再做强文本裁剪。

原因：

1. `content` 使用 `TEXT`。
2. 长度主要由 prompt 约束。
3. 过度裁剪可能截断语义，影响画像质量。

如果后续发现 LLM 输出异常超长，再补充防御性上限。

## 9. 合并规则

合并键：

```text
user_id + memory_type + target_type + target_key
```

处理逻辑：

```text
没有已有 memory
  -> 新建 user_memory，content = candidate.content

已有 ACTIVE memory
  -> 调 MergeAgent 合并 existing.content + candidate.content
  -> 更新 user_memory.content
  -> support_count += 新 evidence 数量
  -> 更新 last_seen_at / latest_session_id / latest_qa_set_id

已有 HIDDEN memory
  -> 跳过，不恢复，不追加 evidence
```

合并不依赖 LLM 输出 `relatedMemoryId`。

LLM 只负责：

1. 从本轮表现提取候选画像。
2. 合并两段客观画像正文。

Java 负责：

1. 枚举合法性。
2. 目标对象合法性。
3. 证据合法性。
4. 新建或更新判断。
5. 数据落库。

## 10. Service 与 Repository 边界

### 10.1 `IMemoryService`

只保留前端使用能力：

```java
List<UserMemoryResponse> list();

UserMemoryDetailResponse detail(String memoryId);

void hide(MemoryHideRequest request);
```

删除：

```java
void ingest(String sessionId, String userId);
```

### 10.2 `MemoryConsumer`

改为依赖 `IMemoryAgent`：

```text
MemoryConsumer
  -> memoryAgent.execute(sessionId, userId)
```

### 10.3 Repository 边界

`IAgentRepository` 负责 MemoryAgent 执行所需的数据能力：

1. 读取本轮评估原始上下文。
2. 按 key 查询 Memory。
3. 创建或更新 `user_memory`。
4. 创建 `user_memory_evidence`。
5. 判断 evidence 是否已存在。

`IMemoryRepository` 只负责前端 Memory 管理能力：

1. 查询 ACTIVE Memory 列表。
2. 查询 Memory 详情和 evidence 列表。
3. 隐藏 Memory。

上下文对象可命名为 `IngestContext`，作为 `MemoryAgent` 的输入上下文模型。

## 11. 前端适配

Profile Memory 页面不维护枚举中文映射。

API 类型改为：

```ts
type UserMemory = {
  id: string;
  memoryType: string;
  memoryTypeText: string;
  targetType: string;
  targetTypeText: string;
  targetKey: string;
  targetKeyText: string;
  content: string;
  supportCount: number;
  status: string;
  firstSeenAt: string;
  lastSeenAt: string;
  hiddenAt: string;
  latestSessionId: string;
  latestQaSetId: string;
};
```

展示规则：

1. 列表标题：`${targetKeyText} · ${memoryTypeText}`。
2. 列表正文：展示 `content` 的前几行。
3. 详情正文：完整展示 `content`。
4. 元信息：展示 `targetTypeText`、`supportCount`、`lastSeenAt`。
5. 隐藏操作保持不变。
6. evidence 列表保持当前结构，但不把 `evidenceSummary` 当正式画像正文。

视觉风格延续当前 Profile 设置页：左侧目录不变，右侧 Memory 页面使用当前大卡片布局。不要修改顶部导航。

## 12. 风险与边界

1. `content` 不裁剪后，异常长输出可能影响页面展示；第一版靠 prompt 控制，必要时再加软上限。
2. MergeAgent 可能过度概括，导致细节丢失；prompt 必须要求保留具体表现。
3. 每轮最多 5 条可能漏掉弱信号；这是有意取舍，Memory 应优先沉淀强信号。
4. 删除 `existingMemories` 后，LLM 不再知道长期上下文；长期融合由 MergeAgent 和 Java key 合并保证。
5. 字段从 `title/summary/detail` 迁移到 `content`，需要同步 SQL、实体、DTO、前端类型和文档。
6. 如果已有库中已经有旧字段数据，需要考虑迁移脚本；开发期可直接更新建表 SQL。

## 13. 需要更新的文件范围

后端：

```text
backend/qa-agent-application/src/main/resources/sql/table.sql
backend/qa-agent-application/src/main/resources/prompt/memory/memory-extract.txt
backend/qa-agent-application/src/main/resources/prompt/memory/memory-merge.txt
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/memory/IMemoryAgent.java
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/memory/MemoryAgent.java
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/memory/subagent/InvestAgent.java
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/memory/subagent/MergeAgent.java
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/memory/model/result/MemoryCandidateResult.java
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/memory/model/result/MemoryMergeResult.java
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/memory/support/MemoryResultCleaner.java
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/memory/service/IMemoryService.java
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/memory/service/MemoryService.java
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/memory/model/dto/Memory.java
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/memory/model/enumeration/*.java
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/memory/repository/IMemoryRepository.java
backend/qa-agent-infrastructure/src/main/java/com/dasi/qa/agent/infrastructure/persistent/entity/UserMemory.java
backend/qa-agent-infrastructure/src/main/java/com/dasi/qa/agent/infrastructure/repository/MemoryRepository.java
backend/qa-agent-interfaces/src/main/java/com/dasi/qa/agent/interfaces/consumer/MemoryConsumer.java
backend/qa-agent-types/src/main/java/com/dasi/qa/agent/types/dto/response/memory/UserMemoryResponse.java
```

前端：

```text
frontend/src/lib/api/types.ts
frontend/src/lib/api/hooks.ts
frontend/src/pages/ProfilePage.tsx
```

文档：

```text
docs/API.md
docs/TABLE.md
docs/V6-Design.md
```

## 14. 验证范围

实现完成后至少确认：

1. 后端编译通过。
2. 前端 typecheck 通过。
3. 前端 build 通过。
4. `MemoryConsumer` 不再依赖 `IMemoryService.ingest()`。
5. `InvestAgent` prompt 输出字段与 Java DTO 一致。
6. `MergeAgent` 只在命中 ACTIVE 旧 Memory 时调用。
7. HIDDEN Memory 不恢复、不追加 evidence。
8. 前端不再维护 Memory 枚举中文映射。
