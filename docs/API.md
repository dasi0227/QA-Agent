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

### 4.2 RAG 搜索接口

| 方法 | 路径 | 鉴权 | 请求 |
| --- | --- | --- | --- |
| POST | `/document/source/search` | 是 | `queryText`, `filterDocumentIds?` |

请求示例：

```json
{
  "queryText": "Redis 跳表 数据结构 应用场景",
  "filterDocumentIds": ["doc-1", "doc-2"]
}
```

响应元素 `SearchResult`：

| 字段 | 说明 |
| --- | --- |
| `chunkId` | 切片 ID |
| `documentId` | 资料 ID |
| `titlePath` | 标题路径，如 `Redis > 数据结构 > 跳表` |
| `content` | 切片正文 |
| `summary` | 切片摘要 |
| `moduleTags` | 从标题路径提取的模块标签 |
| `score` | DashScope rerank 分数；仅前 20 个候选会被写入 |
| `vectorScore` | 语义检索得分 |
| `keywordScore` | 关键词检索得分 |

### 4.3 切片查询接口

| 方法 | 路径 | 鉴权 | 请求 |
| --- | --- | --- | --- |
| POST | `/document/chunk/query` | 是 | `["chunk-id-1", "chunk-id-2", ...]` |

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
| POST | `/qa/set/create` | 是 | `title?`, `userPrompt`, `jobDescription?`, `documentIds`, `requestedQuestionCount` |
| GET | `/qa/set/task-status?taskId=...` | 是 | `taskId` |
| GET | `/qa/set/task-messages?taskId=...` | 是 | `taskId` |
| GET | `/qa/set/task-list` | 是 | 无 |

说明：

1. `/qa/set/create` 返回 `text/event-stream`，不包 `Result<T>`。
2. `requestedQuestionCount` 当前限制为 `10 ~ 100`。
3. 删除 `qa_set` 时会级联删除 `qa_item`、`practice_session`、`practice_session_item`、`qa_set_document_ref`。
4. 当前没有 `/qa/set/create/test` 调试接口。

### 5.2 `/qa/set/create` SSE 事件

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

### 5.3 任务查询响应

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

### 5.4 题目接口

| 方法 | 路径 | 鉴权 | 请求 |
| --- | --- | --- | --- |
| GET | `/qa/item/detail?id=...` | 是 | `id` |
| POST | `/qa/item/query` | 是 | `id?`, `qaSetId?`, `question?`, `knowledgeNote?`, `answer?`, `moduleTag?`, `difficulty?`, `keywords?`, `sourceReliable?`, `sourceChunkIdsJson?`, `sortOrder?` |
| POST | `/qa/item/update` | 是 | 同上，`id` 必填 |
| POST | `/qa/item/delete` | 是 | `id` |

说明：当前 controller 没有开放 `/qa/item/create`，题目主要由 GenerateAgent 自动落库生成。

## 6. Practice

### 6.1 练习会话接口

| 方法 | 路径 | 鉴权 | 请求 |
| --- | --- | --- | --- |
| GET | `/practice/session/detail?id=...` | 是 | `id` |
| POST | `/practice/session/query` | 是 | `id?`, `qaSetId?`, `mode?`, `feedbackMode?`, `status?`, `selectedModule?`, `totalQuestions?`, `answeredCount?`, `score?`, `accuracy?`, `summary?`, `startedAt?`, `finishedAt?` |
| POST | `/practice/session/create` | 是 | 同上 |
| POST | `/practice/session/update` | 是 | 同上，`id` 必填 |
| POST | `/practice/session/delete` | 是 | `id` |
| POST | `/practice/session/assess` | 是 | `sessionId` |

说明：

1. `/practice/session/assess` 是同步接口，不走 SSE。
2. 只有在当前 session 所有题目都有 `answeredAt`、`result`、`score` 时，AssessAgent 才允许执行；否则返回 `40906 PRACTICE_SESSION_NOT_COMPLETED`。

### 6.2 练习题接口

| 方法 | 路径 | 鉴权 | 请求 |
| --- | --- | --- | --- |
| GET | `/practice/session-item/detail?id=...` | 是 | `id` |
| POST | `/practice/session-item/query` | 是 | `id?`, `sessionId?`, `qaItemId?`, `sortOrder?`, `userAnswer?`, `result?`, `score?`, `feedbackSummary?`, `answeredAt?` |
| POST | `/practice/session-item/create` | 是 | 同上 |
| POST | `/practice/session-item/update` | 是 | 同上，`id` 必填 |
| POST | `/practice/session-item/delete` | 是 | `id` |
| POST | `/practice/session-item/feedback` | 是 | `sessionItemId`, `userAnswer?`, `unknown?` |

### 6.3 `/practice/session-item/feedback`

请求示例：

```json
{
  "sessionItemId": "session-item-id",
  "userAnswer": "RDB 是快照，AOF 是命令日志",
  "unknown": false
}
```

响应 `FeedbackResponse`：

| 字段 | 说明 |
| --- | --- |
| `sessionItemId` | 当前练习题 ID |
| `qaItemId` | 原题 ID |
| `result` | `PERFECT` / `CORRECT` / `DEFICIENT` / `WRONG` / `UNKNOWN` |
| `score` | 单题分数 |
| `feedbackSummary` | 单句摘要 |
| `judgeDetail` | 结构化判题详情，未知分支为 `null` |
| `hintDetail` | 结构化提示详情，判题分支为 `null` |
| `sourceChunks` | 引用到的资料切片 |
| `answeredAt` | 本次写入时间 |

说明：

1. `unknown = true` 或 `userAnswer` 为空白时，后端统一走 Hint 分支。
2. 不会分支会固定写入：`result = UNKNOWN`、`score = 0`、`feedbackSummary = "这题已标记为不会。"`。

### 6.4 `/practice/session/assess`

请求示例：

```json
{
  "sessionId": "practice-session-id"
}
```

响应 `AssessResponse`：

| 字段 | 说明 |
| --- | --- |
| `sessionId` | 练习会话 ID |
| `qaSetId` | 题集 ID |
| `score` | 全部单题平均分，四舍五入为整数 |
| `accuracy` | `(PERFECT + CORRECT + DEFICIENT) / totalQuestions * 100`，保留两位小数 |
| `correctCount` | `PERFECT + CORRECT` 数量 |
| `deficientCount` | `DEFICIENT` 数量 |
| `wrongCount` | `WRONG` 数量 |
| `unknownCount` | `UNKNOWN` 数量 |
| `summary` | 等同于 `assessDetail.overallComment` |
| `assessDetail` | `overallComment`、`reviewGuidance`、`strengths[]`、`weaknesses[]` |
| `finishedAt` | 首次完成时间；重复评估不刷新 |

说明：`memory_clue_json` 只落库，不返回给前端。
