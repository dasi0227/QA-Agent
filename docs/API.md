# QA_Agent API 文档

## 通用约定

- 基础路径：`/qa-agent/api/v1`
- 返回体：`Result<T>`，字段为 `code`、`msg`、`data`
- 成功：`code=0`
- 鉴权：受保护接口统一使用 `Authorization: Bearer <accessToken>`
- 刷新接口：`POST /auth/refresh`，请求体携带 `refreshToken`
- 时间格式：`yyyy-MM-dd HH:mm:ss`

## Auth

| 方法 | 路径 | 鉴权 | 请求字段 | 响应 |
| --- | --- | --- | --- | --- |
| POST | `/auth/register` | 否 | `username`, `email`, `password`, `verifyCode` | `userId`, `username`, `email`, `avatar`, `accessToken`, `refreshToken` |
| POST | `/auth/login` | 否 | `username`, `password` | 同上 |
| POST | `/auth/refresh` | 否 | `refreshToken` | 同上 |
| POST | `/auth/send-verify-code` | 否 | `email` | 无 data，60s 限频 |
| GET | `/auth/me` | 是 | — | `userId`, `username`, `email`, `avatar`, `profileCompleted` |

### 示例响应

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "userId": "11111111-1111-1111-1111-111111111111",
    "username": "root",
    "email": "root@example.com",
    "profileCompleted": true,
    "avatar": "https://dasi-qa-agent.oss-cn-guangzhou.aliyuncs.com/avatar/default.png",
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "eyJhbGciOi..."
  }
}
```

## Identity

### 用户账号

| 方法 | 路径 | 鉴权 | 请求字段 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/identity/account/detail?id=...` | 是 | `id` | 按主键查询 |
| POST | `/identity/account/query` | 是 | `id?`, `username?`, `email?`, `status?`, `avatar?` | 条件查询 |
| POST | `/identity/account/create` | 是 | 同上 | 创建账号，密码做 BCrypt |
| POST | `/identity/account/update` | 是 | 同上，`id` 必填 | 更新账号 |
| POST | `/identity/account/delete` | 是 | `id` | 账号改为 `DISABLED` |
| POST | `/identity/account/avatar` | 是 | `file` (multipart/form-data, image/*) | 上传/替换头像，旧 OSS 对象自动删除 |

### 用户画像

| 方法 | 路径 | 鉴权 | 请求字段 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/identity/profile/me` | 是 | — | 返回当前登录用户的画像 |
| POST | `/identity/profile/query` | 是 | `targetRole?`, `targetDomain?`, `targetCompany?`, `allowGeneralKnowledge?`, `allowWebSearch?`, `allowFallback?`, `answerStyle?`, `feedbackStyle?`, `grade?`, `major?`, `stage?`, `llmBaseUrl?`, `llmApiKey?`, `llmModelName?` | 条件查询 |
| POST | `/identity/profile/create` | 是 | 同上 | 创建当前用户画像 |
| POST | `/identity/profile/update` | 是 | 同上 | 更新当前用户画像 |
| POST | `/identity/profile/delete` | 是 | `id?` | 删除当前用户画像 |

说明：V3 GenerateAgent 不使用系统默认用户模型。`/qa/set/create` 会从当前用户 `Profile` 的 `llmBaseUrl`、`llmApiKey`、`llmModelName` 读取模型配置；缺失时返回 `40902` 并将生成任务置为失败。

## Document

### 资料文档

| 方法 | 路径 | 鉴权 | 请求字段 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/document/source/detail?id=...` | 是 | `id` | 按主键查询 |
| POST | `/document/source/query` | 是 | `id?`, `fileName?`, `fileType?`, `filePath?`, `rawContent?`, `summary?` | 条件查询 |
| POST | `/document/source/upload` | 是 | `fileName`, `fileType`, `rawContent`, `summary?` | 上传资料，自动触发异步 RAG 索引 |
| POST | `/document/source/update` | 是 | 同上，`id` 必填 | 更新资料，自动触发重新索引 |
| POST | `/document/source/delete` | 是 | `id` | 软删除（`deleted=true`），级联清理切片和检索数据 |

### 文档切片

| 方法 | 路径 | 鉴权 | 请求字段 |
| --- | --- | --- | --- |
| GET | `/document/chunk/detail?id=...` | 是 | `id` |
| POST | `/document/chunk/query` | 是 | `id?`, `documentId?`, `chunkIndex?`, `titlePath?`, `content?`, `summary?`, `moduleTagsJson?` |
| POST | `/document/chunk/create` | 是 | 同上 |
| POST | `/document/chunk/update` | 是 | 同上，`id` 必填 |
| POST | `/document/chunk/delete` | 是 | `id` |

### V2 RAG 检索

| 方法 | 路径 | 鉴权 | 请求字段 | 说明 |
| --- | --- | --- | --- | --- |
| POST | `/document/source/search` | 是 | `queryText`, `filterDocumentIds?` | 混合检索证据 |
| POST | `/document/source/reindex` | 是 | `id` | 手动触发指定资料重索引 |
| GET | `/document/source/chunks?documentId=...` | 是 | `documentId` | 查看某资料的切片列表 |

#### `/document/source/search` 请求示例

```json
{
  "queryText": "Redis 跳表的数据结构和应用场景",
  "filterDocumentIds": ["uuid-1"]
}
```

#### 响应示例

```json
{
  "code": 0,
  "msg": "success",
  "data": [
    {
      "chunkId": "chunk-uuid-1",
      "documentId": "doc-uuid-1",
      "titlePath": "Redis > 五大数据结构 > 跳表",
      "content": "跳表（Skip List）是一种随机化的数据结构...",
      "moduleTags": ["Redis", "数据结构"],
      "score": 0.8912,
      "vectorScore": 0.8765,
      "keywordScore": 0.7234
    }
  ]
}
```

## QA

### 题集

| 方法 | 路径 | 鉴权 | 请求字段 |
| --- | --- | --- | --- |
| GET | `/qa/set/detail?id=...` | 是 | `id` |
| POST | `/qa/set/query` | 是 | `id?`, `taskId?`, `title?`, `description?`, `moduleTagsJson?`, `questionCount?`, `practiceCount?`, `averageScore?`, `bestScore?` |
| POST | `/qa/set/update` | 是 | 同上，`id` 必填 |
| POST | `/qa/set/delete` | 是 | `id` |
| POST | `/qa/set/create` | 是 | `title?`, `userPrompt?`, `documentIds`, `requestedQuestionCount?` |
| GET | `/qa/set/task-status?taskId=...` | 是 | `taskId` |
| GET | `/qa/set/task-messages?taskId=...` | 是 | `taskId` |
| GET | `/qa/set/task-list` | 是 | — |

说明：删除 `qa_set` 会级联删除 `qa_item`、`practice_session`、`practice_session_item`、`qa_set_document_ref`。

说明：`/qa/set/create` 返回 `text/event-stream`，不包裹 `Result<T>`。请求线程只完成参数校验、用户识别、`SseEmitter` 创建和异步任务提交，实际 GenerateAgent DAG 在线程池中执行。

### 开发测试接口

| 方法 | 路径 | 环境 | 说明 |
|---|---|---|---|
| POST | `/qa/set/create/test` | dev only | 模拟 SSE 事件流，无需请求体，约 15 秒返回 8 条模拟阶段事件，最后 `isCompleted=true` 收尾 |

模拟事件序列：INITIALIZED → DECIDING → PLANNING → WRITING（3 条多模块起草消息）→ VALIDATING（2 条审校修订消息）→ COMPLETED

#### `/qa/set/create` SSE 事件示例

```json
{
  "taskId": "uuid",
  "stage": "VALIDATOR",
  "status": "PROCESSING",
  "message": "已完成本批题目审校和修订。",
  "timestamp": 1717000000000,
  "currentTokens": 1200,
  "totalTokens": 2400,
  "isCompleted": false
}
```

V3 GenerateAgent 当前 DAG：

```text
DECIDE
  DecideAgent

PLAN
  PlanAgent

WRITE
  RagEvidenceProvider → DraftAgent（模块并行，预搜串行）

VALIDATE
  EvaluateAgent → AmendAgent → EvaluateAgent（Loop，maxIterations=2）

SUMMARIZE
  SummarizeAgent
```

对外 SSE `stage` 暴露 `DECIDE`、`PLAN`、`WRITE`、`VALIDATE`、`SUMMARIZE`、`COMPLETED`、`FAILED`，不暴露 `EVALUATOR` 或 `AMENDER`。

#### `/qa/set/task-status` 响应字段

| 字段 | 说明 |
| --- | --- |
| `taskId` | 生成任务 ID |
| `userId` | 用户 ID |
| `title` | 任务标题 |
| `userPrompt` | 用户原始输入 |
| `documentIdsJson` | 所选资料 ID 数组 |
| `documentNamesJson` | 所选资料文件名数组 |
| `qaSetId` | 成功生成后的问答集 ID，未完成时为空 |
| `status` | `PENDING` / `PROCESSING` / `SOLVED` / `UNSOLVED` / `CANCELED` |
| `stage` | `INIT` / `DECIDING` / `PLANNING` / `WRITING` / `VALIDATING` / `SUMMARIZING` / `COMPLETED` / `FAILED` |
| `errorCode` | 失败错误类型 |
| `errorMessage` | 失败原因 |
| `requestedQuestionCount` | 请求题数 |
| `createdAt` | 创建时间 |
| `startedAt` | 开始时间 |
| `completedAt` | 完成时间 |

#### `/qa/set/task-list` 响应字段

| 字段 | 说明 |
| --- | --- |
| `taskId` | 生成任务 ID |
| `title` | 任务标题 |
| `status` | `PENDING` / `PROCESSING` / `SOLVED` / `UNSOLVED` / `CANCELED` |
| `stage` | 当前阶段 |
| `qaSetId` | 成功生成后的问答集 ID，可空 |
| `createdAt` | 创建时间 |

#### `/qa/set/task-messages` 响应字段

| 字段 | 说明 |
| --- | --- |
| `id` | 消息 ID |
| `taskId` | 生成任务 ID |
| `stage` | 阶段 |
| `message` | 阶段消息摘要 |
| `content` | 完整 SseEvent JSON |
| `createdAt` | 创建时间 |

### 题目

| 方法 | 路径 | 鉴权 | 请求字段 |
| --- | --- | --- | --- |
| GET | `/qa/item/detail?id=...` | 是 | `id` |
| POST | `/qa/item/query` | 是 | `id?`, `qaSetId?`, `question?`, `knowledgeNote?`, `answer?`, `moduleTag?`, `difficulty?`, `conflictTip?`, `sourceChunkIdsJson?`, `sortOrder?` |
| POST | `/qa/item/create` | 是 | 同上 |
| POST | `/qa/item/update` | 是 | 同上，`id` 必填 |
| POST | `/qa/item/delete` | 是 | `id` |

## Practice

### 练习会话

| 方法 | 路径 | 鉴权 | 请求字段 |
| --- | --- | --- | --- |
| GET | `/practice/session/detail?id=...` | 是 | `id` |
| POST | `/practice/session/query` | 是 | `id?`, `qaSetId?`, `mode?`, `feedbackMode?`, `status?`, `selectedModule?`, `totalQuestions?`, `answeredCount?`, `score?`, `accuracy?`, `summary?` |
| POST | `/practice/session/create` | 是 | 同上 |
| POST | `/practice/session/update` | 是 | 同上，`id` 必填 |
| POST | `/practice/session/delete` | 是 | `id` |

### 练习题目

| 方法 | 路径 | 鉴权 | 请求字段 |
| --- | --- | --- | --- |
| GET | `/practice/session-item/detail?id=...` | 是 | `id` |
| POST | `/practice/session-item/query` | 是 | `id?`, `sessionId?`, `qaItemId?`, `sortOrder?`, `userAnswer?`, `result?`, `score?`, `feedbackSummary?` |
| POST | `/practice/session-item/create` | 是 | 同上 |
| POST | `/practice/session-item/update` | 是 | 同上，`id` 必填 |
| POST | `/practice/session-item/delete` | 是 | `id` |

## 错误码

| code | 含义 |
| --- | --- |
| `0` | success |
| `40000` | bad request |
| `40001` | verify code expired |
| `40002` | verify code invalid |
| `40100` | unauthorized |
| `40200` | invalid parameters |
| `40300` | forbidden |
| `40400` | not found |
| `40900` | username already registered |
| `40901` | email already registered |
| `40902` | 用户未配置 LLM 接入信息（llm_base_url/llm_api_key/llm_model_name 缺失）|
| `40903` | PlanAgent 规划失败 |
| `40904` | WriteAgent 出题失败 |
| `40905` | 所有题目均未通过审校（ALL_REJECTED） |
| `42900` | verify code rate limited |
| `50000` | internal error |
