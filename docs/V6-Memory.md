# V6 Memory 设计说明

本文以当前代码实现为准，核心文件包括：

- `MemoryAgent`
- `MemoryAgentFactory`
- `MemoryResultCleaner`
- `InvestAgent` / `MergeAgent`
- `MemoryConsumer`
- `UserMemoryProvider`
- `AgentRepository`（记忆相关方法）
- `MemoryController`
- `MemoryService` / `IMemoryRepository` / `MemoryRepository`

## 1. 当前目标

MemoryAgent 负责从用户练习记录中异步沉淀长期记忆画像。每次练习会话提交评估完成后，系统通过 Kafka 消息触发记忆沉淀，对本次作答进行分析并生成/更新用户的学习者画像。

核心产出：

1. 三类记忆画像：稳定掌握（MASTER）、理解不稳（UNCLEAR）、严重薄弱（AWFUL）
2. 两类画像目标：知识模块（MODULE_TAG）、回答能力（ANSWER_SKILL）
3. 每条记忆附带支撑证据（具体作答记录）
4. 记忆可被后续题目生成引用，影响 PlanAgent 的模块规划

当前链路不做：

1. 同步执行——记忆沉淀是异步 Kafka 消费
2. SSE / 实时推送
3. RAG 二次检索

## 2. 触发机制

记忆沉淀的触发链：

1. `AssessAgent` 完成整轮评估 → `AssessSaver` 保存结果 → 发送 `qa.memory.ingest` MQ 消息
2. `MemoryConsumer.onMemoryIngest()` 消费消息 → 调用 `MemoryAgent.execute(sessionId, userId)`
3. 消息失败由 `message_job` 表兜底，通过 XXL-Job 定时重试

注意：V5 时期的 `RecordAgent` 已在 V6 移除，记忆提取完全从 Assess 链路中剥离，不再阻塞评估接口。

## 3. 主流程

```text
MemoryAgent.execute(sessionId, userId)
  -> 校验参数
  -> AgentRepository.getInvestContext(sessionId, userId)
     -> 读取 practice_session + practice_session_item + qa_item
     -> 组装 SessionSource（含作答结果、判分、来源切片）
  -> UserLlmModelProvider.getUserLlmModel()
  -> MemoryAgentFactory.build(memoryContext)
  -> invokeWithAgenticScope()
```

异常处理：

1. 练习上下文为空 → 跳过沉淀，不抛异常
2. LLM 响应格式异常 → 最多重试 2 次，最终抛 `AgentException`
3. Kafka 消费异常 → 记录 `message_job` 错误，由重试机制兜底

## 4. DAG 结构

```text
sequence
  -> INVEST
  -> MERGE
```

### 4.1 INVEST

SubAgent：`InvestAgent`

输入：

1. 本轮全部 `SessionSourceItem` 的 JSON（包含题目、用户作答、判分结果、分数、模块标签）

输出模型 `InvestResult`：

```json
{
  "memoryType": "MASTER|UNCLEAR|AWFUL",
  "targetType": "MODULE_TAG|ANSWER_SKILL",
  "targetKey": "string",
  "content": "string",
  "evidenceRefs": ["sessionItemId1", "sessionItemId2"]
}
```

行为：

1. 最多重试 2 次
2. 最终失败返回空列表
3. 成功后由 `MemoryResultCleaner` 清洗（去空、去重、类型归一）

### 4.2 MERGE

MERGE 阶段在 Java 层执行，对每个候选画像：

1. 根据 `evidenceRefs` 匹配 `SessionSourceItem`，无匹配则跳过
2. 按 `(userId, memoryType, targetType, targetKey)` 四元组查重
3. 已隐藏的记忆跳过
4. 新建 or 合并：
   - 新建：直接创建 `user_memory` 记录
   - 合并：调用 `MergeAgent` 做语义合并 → 更新 `content` + 累加 `supportCount`
5. 为每个证据项创建 `user_memory_evidence` 记录（按 `memoryId + sessionItemId` 去重）

`MergeAgent` 输入：

1. `existingContent`（已有记忆内容）
2. `candidateContent`（本轮候选内容）

输出：

```json
{
  "content": "合并后的内容"
}
```

合并策略：去重保留更具体的描述，最多重试 2 次，失败保留已有内容。

## 5. 数据库

### 5.1 user_memory

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `user_id` | 用户 ID |
| `memory_type` | `MASTER` / `UNCLEAR` / `AWFUL` |
| `target_type` | `MODULE_TAG` / `ANSWER_SKILL` |
| `target_key` | 画像目标键（如模块名、技能名） |
| `content` | 画像描述内容 |
| `support_count` | 证据总数（每次合并累加） |
| `status` | `ACTIVE` / `HIDDEN` |
| `first_seen_at` | 首次出现时间 |
| `last_seen_at` | 最近一次出现时间 |
| `latest_session_id` | 最近一次关联的练习会话 |
| `latest_qa_set_id` | 最近一次关联的题集 |

唯一约束：`(user_id, memory_type, target_type, target_key)`

### 5.2 user_memory_evidence

| 字段 | 说明 |
| --- | --- |
| `id` | 主键 |
| `memory_id` | 关联 `user_memory.id` |
| `user_id` | 用户 ID |
| `session_id` | 练习会话 ID |
| `session_item_id` | 练习题目 ID |
| `qa_set_id` | 题集 ID |
| `qa_item_id` | 原题 ID |
| `module_tag` | 模块标签 |
| `question_snapshot` | 题目快照 |
| `result` | 判分结果 |
| `score` | 得分 |
| `source_chunk_ids_json` | 来源切片 |
| `evidence_summary` | 证据摘要（即候选画像内容） |

唯一约束：`(memory_id, session_item_id)`

## 6. 记忆在生成中的引用

`UserMemoryProvider.getGenerationMemory(userId)` 查询用户所有 ACTIVE 状态的记忆，转换为精简结构后序列化为 JSON，注入 `PlanAgent` 的 prompt 上下文。

传给 PlanAgent 的字段：

| 字段 | 说明 |
| --- | --- |
| `memoryType` | 掌握程度 |
| `targetType` | 画像目标类型 |
| `targetKey` | 具体模块或技能 |
| `content` | 画像描述 |
| `supportCount` | 证据支撑次数 |

PlanAgent 仅在 `user_profile.allow_refer_memory = true` 时使用记忆上下文。

## 7. 对外接口

`MemoryController` 提供三个接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| GET | `/memory/list` | 查询用户全部 ACTIVE 记忆（按 supportCount 降序，按类型分组） |
| GET | `/memory/detail` | 查询单条记忆详情（含证据列表） |
| POST | `/memory/hide` | 隐藏记忆（软删除，不物理删除） |

说明：

1. 记忆不支持用户手动创建或修改，全部由 MemoryAgent 异步产出
2. 隐藏后该记忆不再出现在列表和生成引用中，但再次出现时自动跳过

## 8. Assess 链路变更

V6 对 AssessAgent 做了减法：

1. 移除 `RecordAgent`（原 V5 中负责提取记忆线索的 SubAgent）
2. 移除 `AssessAgentFactory`（不再需要复杂的 parallel DAG）
3. 移除 `AssessResultCleaner` 中 Record 相关逻辑
4. `memory_clue_json` 字段不再写入 `practice_session`
5. AssessAgent DAG 简化为：

```text
parallel
  -> DIAGNOSE
  -> ADVISE
```

## 9. 领域层新增

V6 在 `qa-agent-domain` 下新增了独立的 `memory` 领域包：

```text
domain/memory/
  repository/IMemoryRepository.java
  service/IMemoryService.java
  service/MemoryService.java
```

与 Agent 层的 `MemoryAgent` 职责分离：

- `MemoryAgent`（`domain/agent/service/memory/`）：负责 LLM 驱动的画像提取与合并
- `MemoryService`（`domain/memory/service/`）：负责记忆的查询与展示逻辑
- `MemoryRepository`（`infrastructure/repository/`）：负责数据访问

## 10. Prompt 文件

```text
prompt/memory/memory-extract.txt
prompt/memory/memory-merge.txt
```

## 11. 当前代码口径

1. 记忆沉淀是异步 Kafka 消费，失败不影响评估接口返回。
2. `allow_general_knowledge` 已重命名为 `allow_refer_memory`，含义不变。
3. `user_memory` 唯一约束确保同一用户对同一目标只有一条画像记录，后续练习只做内容合并和计数累加。
4. 前端的 `ProfileMemoryPage` 以分组卡片形式展示记忆（稳定掌握 / 需要巩固 / 明显缺口），支持查看证据详情和隐藏操作。
5. `TempChatMemoryProvider` 是 V7 引入的临时对话记忆管理，与 V6 的长期记忆体系完全独立，不共用存储和生命周期。
