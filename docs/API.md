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
| POST | `/identity/account/update` | 是 | `id?`, `username?`, `email?`, `password?`, `status?`, `avatar?` | 更新当前账号信息 |
| POST | `/identity/account/delete` | 是 | `id` | 账号改为 `DISABLED` |
| POST | `/identity/account/avatar` | 是 | `file` (multipart/form-data, image/*) | 上传/替换头像，旧 OSS 对象自动删除 |

### 用户画像

| 方法 | 路径 | 鉴权 | 请求字段 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/identity/profile/me` | 是 | — | 返回当前登录用户的画像 |
| POST | `/identity/profile/create` | 是 | `targetRole?`, `targetDomain?`, `targetCompany?`, `allowGeneralKnowledge?`, `allowWebSearch?`, `allowFallback?`, `answerStyle?`, `feedbackStyle?`, `grade?`, `major?`, `stage?`, `llmBaseUrl?`, `llmApiKey?`, `llmModelName?` | 创建当前用户画像 |
| POST | `/identity/profile/update` | 是 | 同上 | 更新当前用户画像 |

说明：V3 GenerateAgent 不使用系统默认用户模型。`/qa/set/create` 会从当前用户 `Profile` 的 `llmBaseUrl`、`llmApiKey`、`llmModelName` 读取模型配置；缺失时返回 `40902` 并将生成任务置为失败。

## Document

### 资料文档

| 方法 | 路径 | 鉴权 | 请求字段 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/document/source/detail?id=...` | 是 | `id` | 按主键查询 |
| POST | `/document/source/query` | 是 | `id?`, `fileName?`, `fileType?`, `filePath?`, `rawContent?` | 条件查询 |
| POST | `/document/source/upload` | 是 | `fileName`, `fileType`, `rawContent` | 上传资料，自动触发异步 RAG 索引 |
| POST | `/document/source/update` | 是 | 同上，`id` 必填 | 更新资料，自动触发重新索引 |
| POST | `/document/source/delete` | 是 | `id` | 软删除（`deleted=true`），级联清理切片和检索数据 |

### V2 RAG 检索

| 方法 | 路径 | 鉴权 | 请求字段 | 说明 |
| --- | --- | --- | --- | --- |
| POST | `/document/source/search` | 是 | `queryText`, `filterDocumentIds?` | 混合检索证据 |

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
      "summary": "跳表是一种随机化的数据结构，通过多层索引实现 O(log n) 的查找效率",
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
| POST | `/qa/item/query` | 是 | `id?`, `qaSetId?`, `question?`, `knowledgeNote?`, `answer?`, `moduleTag?`, `difficulty?`, `tip?`, `sourceChunkIdsJson?`, `sortOrder?` |
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
| POST | `/practice/session/assess` | 是 | `sessionId` |

说明：`/practice/session/assess` 为 V5 整轮同步评估接口，只允许当前练习会话全部题目完成后调用。不使用 SSE、轮询或后台任务；不返回内部 `memory_clue_json`。

#### `/practice/session/assess` 请求示例

```json
{
  "sessionId": "88888888-8888-8888-8888-888888888881"
}
```

#### `/practice/session/assess` 响应字段

| 字段 | 说明 |
| --- | --- |
| `sessionId` | 练习会话 ID |
| `qaSetId` | 对应问答集 ID |
| `score` | 本轮平均分，Java 根据单题分数计算 |
| `accuracy` | 本轮达标率，`(CORRECT + DEFICIENT) / totalQuestions * 100` |
| `correctCount` | `CORRECT` 题数 |
| `deficientCount` | `DEFICIENT` 题数 |
| `wrongCount` | `WRONG` 题数 |
| `unknownCount` | `UNKNOWN` 题数 |
| `summary` | 本轮整体摘要，等同于 `assessmentDetail.overallComment` |
| `assessmentDetail` | 用户可读整轮评估详情 |
| `finishedAt` | 练习首次完成时间，重复评估不刷新 |

#### `/practice/session/assess` 响应示例

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "sessionId": "88888888-8888-8888-8888-888888888881",
    "qaSetId": "55555555-5555-5555-5555-555555555541",
    "score": 75,
    "accuracy": 100.00,
    "correctCount": 1,
    "deficientCount": 1,
    "wrongCount": 0,
    "unknownCount": 0,
    "summary": "本轮 Spring Boot 练习整体完成度较好，能答出自动配置与事务代理的主要方向，但代理机制边界仍需要补充。",
    "assessmentDetail": {
      "overallComment": "本轮 Spring Boot 练习整体完成度较好，能答出自动配置与事务代理的主要方向，但代理机制边界仍需要补充。",
      "reviewGuidance": "下一轮先重答事务自调用题，补充代理增强生效条件和调用路径，再用自动配置题保持依赖收敛与条件装配的表达稳定性。",
      "strengths": [
        {
          "title": "自动配置理解稳定",
          "analysis": "Starter 与自动配置关系题回答准确，能覆盖依赖收敛和条件装配两个核心点。"
        }
      ],
      "weaknesses": [
        {
          "title": "代理边界仍需补充",
          "analysis": "事务自调用题能答到没有经过代理，但缺少代理增强生效条件和同类内部调用绕过代理的完整说明。"
        }
      ]
    },
    "finishedAt": "2026-05-17T10:00:00"
  }
}
```

### 练习题目

| 方法 | 路径 | 鉴权 | 请求字段 |
| --- | --- | --- | --- |
| GET | `/practice/session-item/detail?id=...` | 是 | `id` |
| POST | `/practice/session-item/query` | 是 | `id?`, `sessionId?`, `qaItemId?`, `sortOrder?`, `userAnswer?`, `result?`, `score?`, `feedbackSummary?` |
| POST | `/practice/session-item/create` | 是 | 同上 |
| POST | `/practice/session-item/update` | 是 | 同上，`id` 必填 |
| POST | `/practice/session-item/delete` | 是 | `id` |
| POST | `/practice/session-item/feedback` | 是 | `sessionItemId`, `userAnswer?`, `unknown?` |

说明：`/practice/session-item/feedback` 为 V4 单题同步反馈接口，不使用 SSE、轮询或后台任务。`unknown=true` 或 `userAnswer` 为空白时进入不会提示分支，后端固定 `result=UNKNOWN`、`score=0`。

#### `/practice/session-item/feedback` 请求示例

```json
{
  "sessionItemId": "99999999-9999-9999-9999-999999999991",
  "userAnswer": "Starter 负责依赖收敛，自动配置负责按条件装配 Bean。",
  "unknown": false
}
```

#### `/practice/session-item/feedback` 响应字段

| 字段 | 说明 |
| --- | --- |
| `sessionItemId` | 练习题目结果 ID |
| `qaItemId` | 对应题目 ID |
| `result` | `CORRECT` / `DEFICIENT` / `WRONG` / `UNKNOWN` |
| `score` | 单题离散分数 |
| `feedbackSummary` | 单题反馈摘要 |
| `judgeDetail` | 有效回答分支详情，包含 `missingPoints`、`wrongPoints`、`improvementAdvice`、`betterAnswer` |
| `hintDetail` | 不会分支详情，包含 `memoryTip`、`encouragement` |
| `sourceChunks` | 根据 `qa_item.source_chunk_ids_json` 回查的资料切片，供前端折叠展示，不传给 Agent |
| `answeredAt` | 最近一次作答时间 |

#### 有效回答响应示例

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "sessionItemId": "99999999-9999-9999-9999-999999999991",
    "qaItemId": "77777777-7777-7777-7777-777777777771",
    "result": "DEFICIENT",
    "score": 60,
    "feedbackSummary": "你答到了依赖收敛和条件装配，但缺少二者协作关系。",
    "judgeDetail": {
      "missingPoints": ["没有说明 Starter 和自动配置如何配合降低接入成本"],
      "wrongPoints": [],
      "improvementAdvice": "先区分职责，再说明二者如何配合完成开箱即用。",
      "betterAnswer": "Starter 负责收敛依赖，自动配置负责在条件满足时装配 Bean，两者配合让 Spring Boot 能以较少配置完成能力接入。"
    },
    "hintDetail": null,
    "sourceChunks": [],
    "answeredAt": "2026-05-16T01:00:00"
  }
}
```

#### 不会分支响应示例

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "sessionItemId": "99999999-9999-9999-9999-999999999991",
    "qaItemId": "77777777-7777-7777-7777-777777777771",
    "result": "UNKNOWN",
    "score": 0,
    "feedbackSummary": "这题已标记为不会。",
    "judgeDetail": null,
    "hintDetail": {
      "memoryTip": "把 Starter 记成依赖包，把自动配置记成装配开关。",
      "encouragement": "暂时不会也没关系，能把卡住的题标出来，本身就是一次有效练习。"
    },
    "sourceChunks": [],
    "answeredAt": "2026-05-16T01:00:00"
  }
}
```

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
| `40906` | 练习会话尚未完成，不能生成整轮评估 |
| `42900` | verify code rate limited |
| `50000` | internal error |
