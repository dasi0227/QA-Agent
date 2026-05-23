# QA_Agent API 文档

## 1. 通用约定

- 基础路径：`/qa-agent/api/v1`
- 返回结构：除 SSE 外，统一返回 `Result<T>`
- 成功响应：`code = 0`，`msg = "success"`
- 时间格式：`yyyy-MM-dd HH:mm:ss`
- 鉴权方式：除 `/auth/*` 和 `/actuator/health` 外，其余接口都要求 `Authorization: Bearer <accessToken>`
- 当前后端没有开放 Swagger，本文以 `interfaces/controller` 中的实际接口为准

## 2. Auth

### 2.1 接口列表

| 方法 | 路径 | 鉴权 | 请求 |
| --- | --- | --- | --- |
| POST | `/auth/register` | 否 | `username`, `email`, `password`, `verifyCode` |
| POST | `/auth/login` | 否 | `username`, `password` |
| POST | `/auth/refresh` | 否 | `refreshToken` |
| POST | `/auth/send-verify-code` | 否 | `email` |
| GET | `/auth/me` | 是 | 无 |

### 2.2 响应说明

`register`、`login`、`refresh` 返回 `AuthResponse`：

| 字段 | 说明 |
| --- | --- |
| `userId` | 用户 ID |
| `username` | 用户名 |
| `email` | 邮箱 |
| `status` | 账号状态，当前主要为 `ACTIVE` / `DISABLED` |
| `profileCompleted` | 当前实现中只校验是否存在 profile，不校验字段完整性 |
| `avatar` | OSS 公开 URL |
| `accessToken` | 访问令牌 |
| `refreshToken` | 刷新令牌 |

`/auth/me` 也返回 `AuthResponse`，但 `accessToken` / `refreshToken` 为 `null`。

## 3. Identity

### 3.1 账号接口

| 方法 | 路径 | 鉴权 | 请求 |
| --- | --- | --- | --- |
| POST | `/identity/account/update` | 是 | `id`, `username?`, `email?`, `password?`, `status?`, `avatar?` |
| POST | `/identity/account/delete` | 是 | `id` |
| POST | `/identity/account/avatar` | 是 | `file`，`multipart/form-data` |

说明：

1. `/identity/account/delete` 不是物理删除，底层是将账号状态置为 `DISABLED`。
2. 头像上传会先删除旧 OSS 对象，再写入新的 object key。

### 3.2 Profile 接口

| 方法 | 路径 | 鉴权 | 请求 |
| --- | --- | --- | --- |
| GET | `/identity/profile/me` | 是 | 无 |
| POST | `/identity/profile/create` | 是 | `targetRole?`, `targetDomain?`, `targetCompany?`, `allowGeneralKnowledge?`, `allowWebSearch?`, `answerStyle?`, `feedbackStyle?`, `grade?`, `major?`, `stage?`, `llmBaseUrl?`, `llmApiKey?`, `llmModelName?` |
| POST | `/identity/profile/update` | 是 | 同上 |

说明：

1. `allowFallback` 当前只存在于 `user_profile` 表和内部 VO 中，不在公开请求/响应 DTO 中暴露。
2. `llmBaseUrl`、`llmApiKey`、`llmModelName` 是 Generate / Feedback / Assess 三条 Agent 链路运行前置条件；缺失时会返回 `40902 LLM_NOT_CONFIGURED`。

## 4. Document

### 4.1 资料接口

| 方法 | 路径 | 鉴权 | 请求 |
| --- | --- | --- | --- |
| GET | `/document/source/detail?id=...` | 是 | `id` |
| POST | `/document/source/query` | 是 | `id?`, `fileName?`, `fileType?`, `filePath?`, `rawContent?` |
| POST | `/document/source/upload` | 是 | `fileName`, `fileType`, `filePath?`, `rawContent` |
| POST | `/document/source/update` | 是 | `id`, `fileName?` |
| POST | `/document/source/delete` | 是 | `id` |

说明：

1. `upload` 成功后发送 Kafka 消息到 `document.index`，异步触发 RAG 索引。
2. `update` 只允许修改 `fileName`，不触发重索引，不修改资料正文。
3. `delete` 执行软删除（`deleted = true`）。删除前校验 userId 归属，若 `referenceCount > 0` 则返回 `40903` 并提示引用该资料的问答集名称列表。
4. 当前没有开放 `/document/source/reindex` 接口。

响应元素 `SourceDocumentResponse`：

| 字段 | 说明 |
| --- | --- |
| `id` | 资料 ID |
| `fileName` | 文件名 |
| `fileType` | 文件类型 |
| `filePath` | 文件路径 |
| `rawContent` | 原始正文 |
| `referenceCount` | 被问答集引用的次数 |
| `deleted` | 是否已逻辑删除 |
| `createdAt` | 创建时间 |
| `updatedAt` | 最后修改时间 |

### 4.2 切片查询接口

| 方法 | 路径 | 鉴权 | 请求 |
| --- | --- | --- | --- |
| POST | `/document/chunk/query` | 是 | `["chunk-id-1", "chunk-id-2", ...]` |

说明：RAG 搜索能力不再作为公开 HTTP 接口暴露，内部由生成链路按需调用。

请求示例：

```json
["chunk-001", "chunk-002", "chunk-003"]
```

响应元素 `DocumentChunkResponse`：

| 字段 | 说明 |
| --- | --- |
| `id` | 切片 ID |
| `documentId` | 所属资料 ID |
| `fileName` | 所属资料文件名 |
| `chunkIndex` | 在资料内的顺序号 |
| `titlePath` | 标题路径，如 `Redis > 持久化 > RDB` |
| `content` | 切片正文 |
| `summary` | AI 摘要（≤80 字） |
| `moduleTagsJson` | 模块标签 JSON 数组字符串 |

## 5. QA

### 5.1 题集接口

| 方法 | 路径 | 鉴权 | 请求 |
| --- | --- | --- | --- |
| GET | `/qa/set/detail?id=...` | 是 | `id` |
| POST | `/qa/set/query` | 是 | `id?`, `taskId?`, `title?`, `description?`, `moduleTagsJson?`, `questionCount?`, `practiceCount?`, `averageScore?`, `bestScore?`, `averageAccuracy?`, `bestAccuracy?`, `lastPracticedAt?` |
| POST | `/qa/set/update` | 是 | 同上，`id` 必填 |
| POST | `/qa/set/delete` | 是 | `id` |
| GET | `/qa/set/export?id=...` | 是 | `id` |
| POST | `/qa/set/import` | 是 | `file`，`multipart/form-data`，仅接受 `.dasi` |
| POST | `/qa/set/create` | 是 | `title?`, `userPrompt`, `jobDescription?`, `documentIds`, `requestedQuestionCount` |
| GET | `/qa/set/task-status?taskId=...` | 是 | `taskId` |
| GET | `/qa/set/task-messages?taskId=...` | 是 | `taskId` |
| GET | `/qa/set/task-list` | 是 | 无 |

说明：

1. `/qa/set/create` 返回 `text/event-stream`，不包 `Result<T>`。
2. `requestedQuestionCount` 当前限制为 `10 ~ 100`。
3. 删除 `qa_set` 时会级联删除 `qa_item`、`practice_session`、`practice_session_item`、`qa_set_document_ref`。
4. `/qa/set/export` 和 `/qa/set/import` 只处理题集资产，不导出练习历史、生成任务历史、资料引用和 RAG 切片 ID。
5. 当前没有 `/qa/set/create/test` 调试接口。

响应元素 `QaSetResponse`：

| 字段 | 说明 |
| --- | --- |
| `id` | 题集 ID |
| `taskId` | 生成任务 ID |
| `title` | 题集标题 |
| `description` | 题集描述 |
| `moduleTagsJson` | 模块标签 JSON 数组字符串 |
| `documentCount` | 引用资料数量 |
| `questionCount` | 题目数量 |
| `practiceCount` | 练习次数 |
| `averageScore` | 平均分 |
| `bestScore` | 最高分 |
| `averageAccuracy` | 平均达标率 |
| `bestAccuracy` | 最高达标率 |
| `lastPracticedAt` | 最近练习时间 |
| `createdAt` | 创建时间 |
| `updatedAt` | 最后修改时间 |

### 5.2 `.dasi` 题集文件

`.dasi` 文件是 UTF-8 JSON，第一版 schema 固定如下：

```json
{
  "schemaVersion": 1,
  "app": "QA_Agent",
  "exportedAt": "2026-05-22 22:10:00",
  "qaSet": {
    "title": "SpringBoot 核心题集",
    "description": "题集描述",
    "moduleTags": ["SpringBoot"]
  },
  "items": [
    {
      "question": "问题",
      "answer": "标准回答",
      "knowledgeNote": "知识笔记",
      "moduleTag": "SpringBoot",
      "difficulty": "EASY",
      "keywords": "关键词1,关键词2",
      "hint": "答前提示",
      "sourceReliable": true,
      "sortOrder": 1
    }
  ]
}
```

导入规则：

1. 文件名必须以 `.dasi` 结尾。
2. `app` 必须是 `QA_Agent`，`schemaVersion` 必须是 `1`。
3. `qaSet.title` 必填，`items` 至少包含 1 道题。
4. 每道题的 `question` 必填，`difficulty` 只能是 `EASY` / `MEDIUM` / `HARD` 或空。
5. 导入后生成新的题集和题目 ID；练习统计归零；`completeStatus=SOLVED`；`sourceChunkIdsJson=[]`。
6. 导入失败返回业务错误，不创建残缺题集。

### 5.3 `/qa/set/create` SSE 事件

事件结构：

| 字段 | 说明 |
| --- | --- |
| `taskId` | 生成任务 ID |
| `stage` | 当前阶段展示文案，取自 `GeneratePhase.generateStage` |
| `status` | `PENDING` / `PROCESSING` / `SOLVED` / `CANCELED` / `UNSOLVED` |
| `message` | 阶段消息 |
| `timestamp` | 事件时间戳（毫秒） |
| `currentTokens` | 本次事件新增 token 数 |
| `totalTokens` | 累计 token 数 |
| `isCompleted` | 是否终态事件 |

当前阶段文案：

| 枚举 | 对外 `stage` |
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

说明：

1. `publishEvent()` 主要写 `INIT` / `DECIDE` / `PLAN` / `VALIDATE` / `SUMMARIZE` / `COMPLETE` / `FAIL`。
2. `publishProgress()` 也会写自由文本阶段，例如证据检索过程中的阶段消息。

### 5.4 任务查询响应

`/qa/set/task-status` 返回 `TaskStatusResponse`：

| 字段 | 说明 |
| --- | --- |
| `taskId` | 任务 ID |
| `userId` | 用户 ID |
| `title` | 请求标题 |
| `userPrompt` | 用户原始生成要求 |
| `documentIdsJson` | 资料 ID JSON 数组字符串 |
| `documentNamesJson` | 资料文件名 JSON 数组字符串 |
| `qaSetId` | 生成成功后的题集 ID |
| `status` | `PENDING` / `PROCESSING` / `SOLVED` / `CANCELED` / `UNSOLVED` |
| `stage` | 当前阶段展示文案 |
| `errorCode` | `AgentErrorType` 名称 |
| `errorMessage` | 错误信息 |
| `requestedQuestionCount` | 请求题数 |
| `createdAt` | 创建时间 |
| `startedAt` | 开始时间 |
| `completedAt` | 完成时间 |

`/qa/set/task-list` 返回最近任务列表，字段是 `taskId`、`title`、`status`、`stage`、`qaSetId`、`createdAt`。  
`/qa/set/task-messages` 返回阶段消息列表，字段是 `id`、`taskId`、`stage`、`message`、`content`、`createdAt`。

### 5.5 题目接口

| 方法 | 路径 | 鉴权 | 请求 |
| --- | --- | --- | --- |
| GET | `/qa/qaSetEntry/detail?id=...` | 是 | `id` |
| POST | `/qa/qaSetEntry/query` | 是 | `id?`, `qaSetId?`, `question?`, `knowledgeNote?`, `answer?`, `moduleTag?`, `difficulty?`, `keywords?`, `hint?`, `sourceReliable?`, `sourceChunkIdsJson?`, `completeStatus?`, `sortOrder?` |
| POST | `/qa/qaSetEntry/update` | 是 | 同上，`id` 必填 |
| POST | `/qa/qaSetEntry/create` | 是 | `qaSetId`, `question` |
| POST | `/qa/qaSetEntry/complete` | 是 | `id` |
| POST | `/qa/qaSetEntry/delete` | 是 | `id` |

说明：

1. 手动新增题目统一使用 `/qa/qaSetEntry/create`。
2. `/qa/qaSetEntry/create` 会立即创建题目并返回 `completeStatus=PROCESSING`，后端用本地线程池异步执行 CompleteAgent 补全核心字段。
3. `/qa/qaSetEntry/complete` 用于把 `UNSOLVED` 或需要重跑的题目重新置为 `PROCESSING` 并触发 CompleteAgent。
4. `keywords` 和 `hint` 由 AssistAgent 异步补全；前端不查询 `message_job`。

## 6. Practice

### 6.1 领域化刷题流程接口

刷题页只使用领域化练习接口，不直接拼 CRUD。

| 方法 | 路径 | 鉴权 | 请求 |
| --- | --- | --- | --- |
| POST | `/practice/session/init` | 是 | `qaSetId`, `mode`, `feedbackMode`, `selectedModule?` |
| GET | `/practice/session/exist?qaSetId=...` | 是 | `qaSetId` |
| GET | `/practice/session/history?qaSetId=...` | 是 | `qaSetId` |
| GET | `/practice/session/detail?sessionId=...` | 是 | `sessionId` |
| POST | `/practice/item/save` | 是 | `sessionId`, `sessionItemId`, `userAnswer?`, `currentIndex`, `durationSeconds?` |
| POST | `/practice/item/unknown` | 是 | 同 `/practice/item/save` |
| POST | `/practice/item/answer` | 是 | `sessionId`, `sessionItemId`, `userAnswer?`, `currentIndex`, `durationSeconds?` |
| POST | `/practice/session/submit` | 是 | `sessionId`, `durationSeconds?` |
| POST | `/practice/session/restart` | 是 | `qaSetId`, `mode`, `feedbackMode`, `selectedModule?`, `sessionId?` |
| POST | `/practice/session/abandon` | 是 | `sessionId`, `durationSeconds?` |

响应 `PracticeSessionDetailResponse`：

| 字段 | 说明 |
| --- | --- |
| `session` | 会话摘要，包含题集、模式、状态、当前题号、统计和整轮评估 |
| `items` | 本轮题目与作答记录，按 `sortOrder` 排序 |

响应 `PracticeFlowItemResponse` 关键字段：

| 字段 | 说明 |
| --- | --- |
| `sessionItemId` | 本轮单题 ID |
| `qaItemId` | 原题 ID |
| `question` / `standardAnswer` / `knowledgeNote` | 题目快照优先，快照为空时回退题库 |
| `userAnswer` | 用户草稿或提交答案 |
| `status` | `UNANSWERED` / `DRAFT` / `UNKNOWN` / `SUBMITTED` |
| `unknown` | 是否标记不会 |
| `result` | 提交后判题结果，提交前为空 |
| `score` | 单题分数 |
| `feedbackSummary` / `judgeDetail` / `hintDetail` | 结构化单题反馈 |

说明：

1. `/practice/item/save` 只保存草稿、当前题号和累计用时，不触发 Agent。
2. `ITEM_BY_ITEM` 模式下 `/practice/item/answer` 调用 FeedbackAgent，并由 `FeedbackSaver` 写入 `practice_session_item`。
3. `AFTER_ALL` 模式做题阶段不调用 `/practice/item/answer`；提交本轮时 `/practice/session/submit` 逐题调用 FeedbackAgent，再调用 AssessAgent。
4. `/practice/session/submit` 由 `AssessSaver` 写入 `practice_session`，并将 session 标记为 `FINISHED`。
5. `/practice/session/history` 只返回当前用户当前题集的 `FINISHED` 会话。
6. `/practice/session/restart` 会把同题集未完成会话标记为 `ABANDONED` 后创建新会话。
7. 进度恢复以服务端 `detail` 为准，前端 localStorage 只保存最近 session 快照。

### 6.2 练习会话查询接口

| 方法 | 路径 | 鉴权 | 请求 |
| --- | --- | --- | --- |
| POST | `/practice/session/query` | 是 | `id?`, `qaSetId?`, `mode?`, `feedbackMode?`, `status?`, `selectedModule?`, `totalQuestions?`, `answeredCount?`, `score?`, `accuracy?`, `summary?`, `startedAt?`, `finishedAt?` |

说明：练习会话的创建、提交、放弃和重开统一走 `/practice/session/*`，不再暴露旧的 session 写入型 CRUD 接口。

### 6.3 练习题接口说明

练习题明细不再暴露独立 session item 查询接口。刷题页统一通过 `GET /practice/session/detail?sessionId=...` 获取 session、题目快照、作答状态、反馈和结果。

练习题的草稿保存、不会标记和单题提交统一走 `/practice/item/*`。
