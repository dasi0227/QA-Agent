# V3 Generate Agent 设计说明

本文以当前代码实现为准，核心文件包括：

- `GenerateAgent`
- `GenerateAgentFactory`
- `GenerateSaver`
- `GenerateSupervisor`
- `RagEvidenceProvider`
- `WebEvidenceProvider`
- `EventPublisher`

## 1. 当前目标

GenerateAgent 负责把用户资料生成结构化问答集，并通过 SSE 实时回传阶段消息。当前链路包含：

1. 入口判定
2. 模块规划
3. 证据检索与题目起草
4. 审校与最小修订
5. 总结、落库和任务收尾

## 2. 执行入口

生成分两步：

1. `POST /qa/set/task` — Controller 生成 `taskId`，调用 `createGenerationTask()` 创建任务记录，同步返回 `{ taskId }`
2. `POST /qa/set/create` — 前端携带 `taskId`，Controller 创建 `SseEmitter`，异步调用 `generationAgent.execute(userId, request, sseEventHandler)` 启动 DAG

`QaController` 的职责：

1. `/qa/set/task`：生成 `taskId` → `createGenerationTask()` → 返回 `taskId`
2. `/qa/set/create`：创建 `SseEmitter` → 取当前用户 ID → 线程池异步执行生成

## 3. 主流程

```text
GenerateAgent.execute()
  -> taskId = request.getTaskId()
  -> 读取 UserProfileInfo / Style / Allow
  -> publish INIT 事件
  -> Thread.sleep(1000)   // 避免 INIT 与下一条事件瞬发
  -> UserLlmModelProvider.getUserLlmModel()
  -> GenerateSupervisor
  -> 预构建各阶段 Context
  -> GenerateAgentFactory.build(generateContext)
  -> invokeWithAgenticScope(initialData)
  -> GenerateSaver.save(scope, taskId, userId, request)
  -> publish COMPLETE 事件
```

异常收口：

1. `GenerateException`：按 `AgentErrorType` 发布失败事件
2. 其他异常：用 `AgentErrorType.fromException()` 映射后发布失败事件

## 4. DAG 结构

### 4.1 顶层结构

```text
sequence
  -> DECIDE
  -> ROUTE
       -> valid = true  : CREATE
       -> valid = false : ABORT
```

### 4.2 CREATE 子链路

```text
sequence
  -> PLAN
  -> WRITE
  -> VALIDATE
  -> SUMMARIZE
```

### 4.3 WRITE 内部结构

`WRITE` 不是纯 LLM 调用，分两段：

1. Java 串行预搜证据  
   - `RagEvidenceProvider.search()`
   - `WebEvidenceProvider.search()`（用户开启 `allow_web_search` 时）
2. `parallelBuilder()` 并发模块起草  
   - 每个模块一个 `agentAction`
   - action 内部只调 `DraftAgent`

### 4.4 VALIDATE 内部结构

`VALIDATE` 按 `BATCH_SIZE = 10` 分批，再用 `CompletableFuture` 并行处理各批。

单批内部逻辑：

```text
EvaluateAgent
  -> PASS 留下
  -> 非 PASS 进入 AmendAgent
AmendAgent
  -> 再次 EvaluateAgent
最多循环 2 次
```

## 5. Scope 数据

当前稳定写入的 scope key：

| key | 类型 | 写入阶段 |
| --- | --- | --- |
| `decideResult` | `DecideResult` | `DECIDE` |
| `planResult` | `PlanResult` | `PLAN` |
| `draftResult` | `List<DraftResult>` | `WRITE` |
| `validateResult` | `List<DraftResult>` | `VALIDATE` |

说明：

1. 静态输入主要通过各阶段 `Context` 闭包传入。
2. scope 主要承担阶段输出和路由状态。

## 6. 阶段说明

### 6.1 DECIDE

SubAgent：`DecideAgent`

输出：

```json
{
  "valid": true,
  "reason": "..."
}
```

行为：

1. 最多重试 2 次
2. 最终失败时走 `fallbackDecide()`
3. 成功后由 `GenerateSupervisor` 生成阶段消息

### 6.2 ABORT

SubAgent：`AbortAgent`

行为：

1. 读取 `DecideResult`
2. 生成拒绝说明
3. `EventPublisher.publishCanceled()`

任务收口结果：

- `status = CANCELED`
- `stage = 💣 任务失败`

### 6.3 PLAN

SubAgent：`PlanAgent`

关键输入：

1. 资料摘要 `documents`
2. 用户画像 `userProfile`
3. `userPrompt`
4. `jobDescription`
5. `questionCount`
6. `retryHint`

输出模型 `PlanResult`：

```json
{
  "title": "string",
  "description": "string",
  "planItems": [
    {
      "module": "string",
      "questionCount": 10,
      "focusTopics": "string",
      "keyConcepts": "string"
    }
  ]
}
```

兜底：

1. 只有 `user_profile.allow_fallback = true` 时，最终失败才会走 `fallbackPlan()`
2. 否则抛 `GenerateException`

### 6.4 WRITE / DRAFT

SubAgent：`DraftAgent`

当前 `DraftResult` 字段：

| 字段 | 说明 |
| --- | --- |
| `question` | 面试官口吻问题 |
| `answer` | 标准回答 |
| `knowledgeNote` | 复习笔记 |
| `tag` | 题目标签，允许 1~2 个逗号分隔 |
| `difficulty` | `EASY` / `MEDIUM` / `HARD` |
| `sourceReliable` | 资料证据是否足以支撑主要答案 |
| `sourceChunkIds` | 来源切片 ID 列表 |

注意：

1. 当前没有旧版文档里的“冲突提示”和 `evidence` 字段。
2. `qa_item.keywords` 不再来自 `DraftResult`，由后置 `AssistAgent` 异步补全。
3. `qa_item.source_reliable` 最终直接来自 `DraftResult`，只有资料证据足以支撑主要答案时才为 `true`。

### 6.5 VALIDATE

SubAgent：

1. `EvaluateAgent`
2. `AmendAgent`

`EvaluateResult`：

```json
{
  "verdict": "PASS|AMEND",
  "reason": "string",
  "suggestion": "string"
}
```

当前实现没有 `REJECT` 分支，只有：

1. `PASS`
2. `AMEND`

### 6.6 SUMMARIZE

SubAgent：`SummarizeAgent`

行为：

1. 读取 `planResult` 和 `validateResult`
2. 生成最终完成说明
3. 这里只负责消息，不直接写库

当前落库已经移到 `GenerateSaver`，不再由 `doSummarize()` 自己保存。

## 7. 保存逻辑

`GenerateSaver.save()` 会：

1. 从 scope 读取 `planResult`
2. 从 scope 读取 `validateResult`
3. 调用 `agentRepository.saveGeneratedQaSet(...)`
4. 调用 `agentRepository.markTaskCompleted(...)`

`saveGeneratedQaSet()` 会：

1. 新建 `qa_set`
2. 遍历 `DraftResult` 写入 `qa_item`
3. 记录 `qa_set_document_ref`
4. 对每份资料 `reference_count + 1`
5. 保存完成后由 `GenerateSaver` 发送 `qa.qaSetEntry.assist` 消息补全 `keywords` 和 `hint`

## 8. SSE 与任务状态

SSE 事件结构：

| 字段 | 说明 |
| --- | --- |
| `taskId` | 任务 ID |
| `stage` | `GeneratePhase.generateStage` 文案 |
| `status` | `PROCESSING` / `SOLVED` / `CANCELED` / `UNSOLVED` |
| `message` | 阶段消息 |
| `currentTokens` | 与上次事件相比新增 token |
| `totalTokens` | 总 token |
| `isCompleted` | 是否终态 |

当前阶段文案：

| 枚举 | 文案 |
| --- | --- |
| `INIT` | `🚀 任务启动` |
| `DECIDE` | `🤔 请求判定` |
| `PLAN` | `🗓️ 规划模块` |
| `WRITE` | `📝 题目编写` |
| `DRAFT` | `✍️ 检索起草` |
| `VALIDATE` | `🧐 审校修订` |
| `EVALUATE` | `🔍 内容审校` |
| `AMEND` | `🔧 修订完善` |
| `SUMMARIZE` | `📈 结果汇总` |
| `COMPLETE` | `🎉 任务完成` |
| `FAIL` | `💣 任务失败` |

## 9. Prompt 与模型

当前 Generate 相关 prompt：

```text
prompt/generate/generation-decide.txt
prompt/generate/generation-abort.txt
prompt/generate/generation-plan.txt
prompt/generate/generation-draft.txt
prompt/generate/generation-evaluate.txt
prompt/generate/generation-amend.txt
prompt/generate/generation-summarize.txt
prompt/generate/supervisor-summary.txt
prompt/generate/web-search.txt
```

模型来源：

1. 用户专属模型：`UserLlmModelProvider`
2. 系统 supervisor 模型：`supervisorModel`
3. 系统 web-search 模型：`webSearchModel`

## 10. 当前代码口径

1. 任务创建时就会落 `qa_generation_task` 主记录。
2. `requestedQuestionCount` 当前校验范围是 `10 ~ 100`。
3. `valid = false` 的拒绝分支最终状态是 `CANCELED`，不是 `UNSOLVED`。
4. 当前没有系统级 LLM 降级；用户未配置模型时直接失败并返回 `40902 LLM_NOT_CONFIGURED`。
