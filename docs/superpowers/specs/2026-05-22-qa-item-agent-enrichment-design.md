# QA Item Agent Enrichment Design

日期：2026-05-22

## 1. 目标

本次设计解决两个问题：

1. 收缩 Generate 主链路，减少 DraftAgent 输出字段，降低 JSON 结构不稳定和重试失败概率。
2. 支持用户手动新增题目：用户只填写问题，后端用新 Agent 异步补全题目核心信息。

最终形态：

- `DraftAgent` 不再生成 `keywords`。
- `AssistAgent` 负责补全 `keywords + hint`。
- `CompleteAgent` 负责处理用户手动新增的问题，补全题目核心字段。
- `qa_item` 只新增必要字段，不引入来源字段，不为 Assist 增加业务状态。

## 2. 已确认取舍

1. 题目核心资产先落库，辅助字段后补全。
2. `AssistAgent` 走 Kafka + `message_job`，由定时重试任务处理失败重发。
3. `CompleteAgent` 不走 Kafka，用户创建题目后用本地线程池异步执行。
4. 手动新增题默认使用题集关联资料做 RAG，证据弱时允许通用知识兜底，并写 `sourceReliable=false`。
5. 前端不查询 `message_job`。
6. Assist 失败不影响题目可用，`keywords/hint` 为空就按空态展示。
7. Complete 需要业务状态，因为它影响手动题核心字段是否可用。
8. 用户不需要看到 Complete 错误原因，所以 `qa_item` 不保存 `complete_error_message`。

## 3. 数据模型

### 3.1 `qa_item`

新增字段：

```sql
`hint` LONGTEXT NULL,
`complete_status` VARCHAR(32) NOT NULL DEFAULT 'SOLVED'
```

保留既有字段：

- `keywords`：由 `AssistAgent` 回填，不再由 `DraftAgent` 生成。
- `source_reliable`：继续表达资料证据是否足以支撑主要答案。
- `source_chunk_ids_json`：继续保存题目引用的资料切片。

不新增：

- `created_source`
- `assist_status`
- `assist_error_message`
- `complete_error_message`

`complete_status` 语义：

| 状态 | 含义 |
| --- | --- |
| `PROCESSING` | 手动题核心字段正在由 CompleteAgent 补全 |
| `SOLVED` | 核心字段可用 |
| `UNSOLVED` | CompleteAgent 执行失败，可重试或手动编辑 |

自动生成题创建时直接为 `SOLVED`。手动创建题创建时为 `PROCESSING`。

### 3.2 `practice_session_item`

新增快照字段：

```sql
`hint_snapshot` LONGTEXT NULL
```

创建练习 session item 时写入题目当时的 `hint`。Practice detail 优先读取快照，避免题目后续 hint 变化影响历史练习复现。

### 3.3 `message_job`

新增字段：

```sql
`error_message` LONGTEXT NULL
```

该字段只用于后端排查 Kafka 异步任务最近一次失败原因，不暴露给前端。

## 4. Generate 链路调整

`DraftAgent` 输出收缩：

- 移除 `keywords`
- 保留 `sourceReliable`

`DraftResult` 移除 `keywords` 字段。

相关 prompt 必须同步调整：

- `prompt/generate/generate-draft.txt` 删除 keywords 生成规则、示例字段和输出结构字段。
- `prompt/generate/generate-amend.txt` 删除 keywords 修订规则和输出结构字段。

相关 Java 逻辑同步调整：

- `GenerateAgent.fallbackDraft(...)` 不再写 `keywords`。
- `GenerateSaver` 不再从 `DraftResult` 读取 `keywords`。
- `AgentRepository.saveGeneratedQaSet(...)` 插入 `qa_item` 时 `keywords` 为空，`complete_status=SOLVED`。
- 每个 `qa_item` 落库后发送 `qa.item.assist` 消息。

## 5. Feedback / Assess 链路调整

`keywords` 变为异步辅助字段后，不应继续参与 Feedback 或 Assess 推理上下文，避免字段为空、延迟补全或重新生成后干扰判题一致性。

Feedback 需要移除：

- `PracticeVO.keywords`
- `HintContext.keywords`
- `JudgeContext.keywords`
- `FeedbackAgent` 构建 Hint/Judge context 时的 keywords 读取
- `HintAgent` 方法参数 `keywords`
- `JudgeAgent` 方法参数 `keywords`
- `prompt/feedback/feedback-hint.txt` 中 “答题要点 / keywords” 说明
- `prompt/feedback/feedback-judge.txt` 中 “答题要点 / keywords” 说明，以及 “keywords 和 knowledgeNote 只作为辅助” 这类表述

Assess 当前没有直接使用 `keywords`。实施时仍需用 `rg keywords backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/assess backend/qa-agent-application/src/main/resources/prompt/assess` 复核，确保无残留干扰。

调整后：

- Feedback 以 `question + standardAnswer + knowledgeNote + sourceReliable + userAnswer` 为核心上下文。
- Assess 继续基于 session、item、单题反馈和统计数据进行整轮评估。
- `keywords` 只用于题目详情展示和后续可能的学习辅助功能。

## 6. AssistAgent

### 6.1 职责

`AssistAgent` 只负责给已有完整题目补全辅助字段：

- `keywords`
- `hint`

不改：

- `question`
- `answer`
- `knowledgeNote`
- `moduleTag`
- `difficulty`
- `sourceReliable`
- `sourceChunkIdsJson`

### 6.2 放置

```text
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/assist
  IAssistAgent
  AssistAgent
  subagent/AssistSubAgent
  model/context/AssistContext
  model/result/AssistResult
  model/exception/AssistException
```

不需要：

- `AssistAgentFactory`
- `AssistPhase`

原因：第一版是单 Agent 调用，不是多阶段 DAG，无 SSE 阶段和 Scope key。

### 6.3 输入

`AssistAgent.execute(qaItemId, userId)` 从 DB 读取：

- `question`
- `answer`
- `knowledgeNote`
- `moduleTag`
- `difficulty`
- `sourceReliable`
- source chunks 摘要
- 用户回答风格

### 6.4 输出

```json
{
  "keywords": "短语1,短语2,短语3",
  "hint": "一句不泄露答案的答前提示"
}
```

`hint` 要求：

- 一句话；
- 不直接泄露标准答案；
- 用于答题前唤醒记忆或引导思路；
- 不生成鼓励语，不做解析。

### 6.5 触发

Kafka topic：

```text
qa.item.assist
```

消息内容：

```json
{
  "qaItemId": "xxx",
  "userId": "xxx"
}
```

jobId：

```text
qa_item_assist:{qaItemId}
```

Consumer 成功：

- 调用 `mqUtil.markSuccess(jobId)`。

Consumer 失败：

- 调用 `mqUtil.recordError(jobId, errorMessage)`。
- 保持 `message_job.job_status=UNSOLVED`。
- 由 `MessageRetryJob` 重发，超过最大次数后进入 `FAIL + DLQ`，DLQ 消息不再写入普通 `message_job` 重试队列。

## 7. CompleteAgent

### 7.1 职责

`CompleteAgent` 只负责把用户手动新增的问题补成可用题目核心资产。

补全字段：

- `answer`
- `knowledgeNote`
- `moduleTag`
- `difficulty`
- `sourceReliable`
- `sourceChunkIdsJson`

不生成：

- `keywords`
- `hint`

Complete 成功后再发送 `qa.item.assist`。

### 7.2 放置

```text
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/complete
  ICompleteAgent
  CompleteAgent
  subagent/CompleteSubAgent
  model/context/CompleteContext
  model/result/CompleteResult
  model/exception/CompleteException
```

不需要：

- `CompleteAgentFactory`
- `CompletePhase`

### 7.3 输入

`CompleteAgent.execute(qaItemId, userId)` 从 DB 读取：

- `question`
- `qaSetId`
- 题集关联资料 ID
- RAG 检索证据
- 用户画像
- 用户回答风格

默认行为：

1. 用 question 在题集关联资料中做 RAG。
2. 命中证据时基于证据生成答案和知识点。
3. 证据弱或无证据时允许通用知识兜底，并写 `sourceReliable=false`。

### 7.4 输出

```json
{
  "answer": "string",
  "knowledgeNote": "string",
  "moduleTag": "string",
  "difficulty": "EASY|MEDIUM|HARD",
  "sourceReliable": true,
  "sourceChunkIds": ["chunk-id"]
}
```

### 7.5 保存规则

保存前重新读取 `qa_item`，只填空字段，防止覆盖用户在等待期间的手动编辑：

- `answer` 为空才写；
- `knowledge_note` 为空才写；
- `module_tag` 为空才写；
- `difficulty` 为空才写；
- `source_chunk_ids_json` 为空才写；
- `source_reliable` 已有人为值时不覆盖；
- `question` 永不覆盖。

状态：

- 执行前：`complete_status=PROCESSING`
- 成功：`complete_status=SOLVED`
- 失败：`complete_status=UNSOLVED`

失败只写状态和日志，不保存错误消息到 `qa_item`。

## 8. Shared RAG Provider

现有 `generate/support/RagEvidenceProvider` 可提取为共享能力：

```text
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/shared/RagEvidenceProvider
```

建议能力：

```text
searchByPlanItem(userId, documentIds, planItem)
searchByQuestion(userId, documentIds, question)
```

使用方：

- `GenerateAgent` 调 `searchByPlanItem(...)`
- `CompleteAgent` 调 `searchByQuestion(...)`

如果实施时迁移 Generate 影响过大，可以第一版新增 shared provider 给 Complete 使用，旧 provider 后续再合并。但最终目标是共享 RAG 检索封装，不让 Complete 直接依赖 `IRagSearchService`。

## 9. Repository 与 MQ

复用现有：

```text
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/repository/IAgentRepository.java
```

新增方法建议：

```java
AssistContext getAssistContext(String qaItemId, String userId);

void saveAssistResult(String qaItemId, String userId, AssistResult result);

CompleteContext getCompleteContext(String qaItemId, String userId);

void saveCompleteResult(String qaItemId, String userId, CompleteResult result);

void markQaItemCompleteProcessing(String qaItemId, String userId);

void markQaItemCompleteFailed(String qaItemId, String userId);
```

`IMqUtil` 新增：

```java
void recordError(String jobId, String errorMessage);
```

`MqUtil.send(...)`：

- 首次发送写 `message_job`；
- 重发时 `job_retry + 1`，`job_status=UNSOLVED`；
- 可清空旧 `error_message`。

`markSuccess(...)`：

- 写 `SUCCESS`；
- 清空 `error_message`。

`recordError(...)`：

- 写最近一次错误消息；
- 保持 `UNSOLVED`。

`MessageRetryJob`：

- 继续扫描 `UNSOLVED`；
- 未超过上限重发；
- 超过上限发送 DLQ 并 `markFail` 原 job；DLQ 消息不写入普通 `message_job` 重试队列。

## 10. HTTP 接口

新增：

### 10.1 `POST /qa/item/create`

用途：手动新增题目，只填问题，立即创建题目并后台执行 CompleteAgent。

请求：

```json
{
  "qaSetId": "xxx",
  "question": "Redis 的 RDB 和 AOF 有什么区别？"
}
```

响应：`QaItemResponse`

后端行为：

1. 校验题集属于当前用户。
2. 创建 `qa_item`。
3. `complete_status=PROCESSING`。
4. 用 `applicationTaskExecutor` 异步执行 `CompleteAgent`。
5. 立即返回新题。

### 10.2 `POST /qa/item/complete`

用途：重新触发 CompleteAgent。

请求：

```json
{
  "id": "qaItemId"
}
```

响应：`QaItemResponse`

后端行为：

1. 校验题目属于当前用户。
2. 写 `complete_status=PROCESSING`。
3. 用本地线程池异步执行 `CompleteAgent`。
4. 立即返回最新题目。

## 11. 前端设计

题目新增弹窗：

- 极简弹窗，只保留问题输入框。
- 问题输入框使用现有浅色输入样式，不使用黑色边框。
- 主按钮文案：`创建并智能补全`。
- 主按钮使用当前主题色。
- 取消按钮使用弱化样式。

创建后：

1. 弹窗关闭；
2. 新题立即出现在题目列表；
3. 自动选中新题；
4. 详情区显示 `智能补全中`；
5. 当前题详情短轮询 `/qa/item/detail?id=...`。

轮询规则：

- `completeStatus=PROCESSING` 时每 2 秒 refetch 当前题；
- `SOLVED` 或 `UNSOLVED` 停止；
- 超时停止，但仍以服务端状态为准；
- `UNSOLVED` 显示 `重新智能补全` 和 `手动编辑`。

字段展示：

- `hint` 为空时不展示答前提示；
- `keywords` 为空时展示空态；
- 核心字段为空且 `completeStatus=PROCESSING` 时展示生成中占位；
- 核心字段为空且 `completeStatus=UNSOLVED` 时展示失败空态。

## 12. 实施顺序

1. 改 SQL、Entity、DTO、前端类型，补 `hint / completeStatus / hintSnapshot / errorMessage`。
2. 收缩 Generate：删 `DraftResult.keywords`，改 generate prompt、fallback、保存逻辑。
3. 清理 Feedback keywords：改 context、subagent 参数、prompt、PracticeVO。
4. 复核 Assess 无 keywords 残留。
5. 改 MQ：`message_job.error_message`、`IMqUtil.recordError(...)`、Consumer 失败语义。
6. 新增 `AssistAgent` 和 `AssistConsumer`。
7. 新增 `CompleteAgent` 和本地线程池触发 service。
8. 新增 `/qa/item/create` 和 `/qa/item/complete`。
9. 前端接入新增题弹窗、详情轮询、状态展示。
10. 更新 `docs/API.md`、`docs/TABLE.md`、`docs/V3-Generate-Design.md`。

## 13. 验证计划

后端：

- `cd backend && mvn test`
- `rg keywords backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/generate backend/qa-agent-application/src/main/resources/prompt/generate`
- `rg keywords backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/feedback backend/qa-agent-application/src/main/resources/prompt/feedback`
- 生成题集后 `qa_item.keywords` 初始为空，随后 Assist 写入。
- 手动创建题后 `complete_status=PROCESSING`。
- Complete 成功只填空字段，并转为 `SOLVED`。
- Complete 失败转为 `UNSOLVED`。
- Assist 失败写 `message_job.error_message`，保持 `UNSOLVED` 等待重试。
- Practice session item 写入 `hint_snapshot`。

前端：

- `cd frontend && npm run typecheck`
- `cd frontend && npm run build`
- 新增题弹窗只要求问题。
- 创建后自动选中新题并显示补全中。
- `SOLVED` 后停止轮询并展示完整内容。
- `UNSOLVED` 后停止轮询并显示重试/编辑。
- 输入框和主按钮符合现有主题样式。

## 14. 风险与边界

1. Complete 走本地线程池，没有 message_job 自动重试；通过前端重试按钮补足。
2. Assist 走 Kafka，用户不感知失败；后端需保证 `MessageRetryJob` 语义正确。
3. `keywords` 从 Feedback 中移除后，判题更依赖标准答案和知识笔记；这是有意为之，避免异步辅助字段干扰核心判定。
4. 如果未来希望用户查看 Assist 失败状态，再考虑暴露 message job 查询接口或增加 assist 状态字段。
