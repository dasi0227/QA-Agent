# QA_Agent V1 API

## 通用约定

- 返回体：`Result<T>`，字段为 `code`、`msg`、`data`
- 成功：`code=0`
- 鉴权：受保护接口统一使用 `Authorization: Bearer <accessToken>`
- 刷新接口：`POST /auth/refresh`，请求体携带 `refreshToken`
- 时间格式：`yyyy-MM-dd HH:mm:ss`

## Auth

| 方法 | 路径 | 鉴权 | 请求字段 | 响应 |
| --- | --- | --- | --- | --- |
| POST | `/auth/register` | 否 | `username`, `email`, `password` | `userId`, `username`, `email`, `accessToken`, `refreshToken` |
| POST | `/auth/login` | 否 | `username`, `password` | 同上 |
| POST | `/auth/refresh` | 否 | `refreshToken` | 同上 |

### 示例响应

```json
{
  "code": 0,
  "msg": "success",
  "data": {
    "userId": "11111111-1111-1111-1111-111111111111",
    "username": "root",
    "email": "root@example.com",
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "eyJhbGciOi..."
  }
}
```

## Identity

### `user_account`

| 方法 | 路径 | 鉴权 | 请求字段 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/user-account/detail?id=...` | 是 | `id` | 按主键查询 |
| POST | `/user-account/query` | 是 | `id?`, `username?`, `email?`, `password?`, `status?` | 条件查询 |
| POST | `/user-account/create` | 是 | 同上 | 创建账号，密码会做 BCrypt |
| POST | `/user-account/update` | 是 | 同上，`id` 必填 | 更新账号 |
| POST | `/user-account/delete` | 是 | `id` | 账号改为 `DISABLED` |

### `user_profile`

| 方法 | 路径 | 鉴权 | 请求字段 | 说明 |
| --- | --- | --- | --- | --- |
| GET | `/user-profile/detail?id=...` | 是 | `id` | 返回当前用户画像，`id` 仅作兼容 |
| POST | `/user-profile/query` | 是 | `targetRole?`, `targetDomain?`, `targetCompany?`, `allowGeneralKnowledge?`, `allowWebSearch?`, `answerStyle?`, `feedbackStyle?`, `age?`, `grade?`, `major?`, `stage?` | 条件查询 |
| POST | `/user-profile/create` | 是 | 同上 | 创建当前用户画像 |
| POST | `/user-profile/update` | 是 | 同上 | 更新当前用户画像 |
| POST | `/user-profile/delete` | 是 | `id?` | 删除当前用户画像 |

## Document

### `source_document`

| 方法 | 路径 | 鉴权 | 请求字段 |
| --- | --- | --- | --- |
| GET | `/source-document/detail?id=...` | 是 | `id` |
| POST | `/source-document/query` | 是 | `id?`, `fileName?`, `fileType?`, `filePath?`, `rawContent?`, `normalizedContent?`, `summary?`, `moduleTagsJson?`, `referenceCount?`, `deleted?` |
| POST | `/source-document/create` | 是 | 同上 |
| POST | `/source-document/update` | 是 | 同上，`id` 必填 |
| POST | `/source-document/delete` | 是 | `id` |

说明：删除为软删，`deleted=true`。

### `document_chunk`

| 方法 | 路径 | 鉴权 | 请求字段 |
| --- | --- | --- | --- |
| GET | `/document-chunk/detail?id=...` | 是 | `id` |
| POST | `/document-chunk/query` | 是 | `id?`, `documentId?`, `chunkIndex?`, `titlePath?`, `content?`, `summary?`, `moduleTagsJson?`, `embeddingVector?` |
| POST | `/document-chunk/create` | 是 | 同上 |
| POST | `/document-chunk/update` | 是 | 同上，`id` 必填 |
| POST | `/document-chunk/delete` | 是 | `id` |

## QA

### `qa_set`

| 方法 | 路径 | 鉴权 | 请求字段 |
| --- | --- | --- | --- |
| GET | `/qa-set/detail?id=...` | 是 | `id` |
| POST | `/qa-set/query` | 是 | `id?`, `taskId?`, `title?`, `description?`, `moduleTagsJson?`, `questionCount?`, `practiceCount?`, `averageScore?`, `bestScore?`, `averageAccuracy?`, `bestAccuracy?`, `lastPracticedAt?` |
| POST | `/qa-set/create` | 是 | 同上 |
| POST | `/qa-set/update` | 是 | 同上，`id` 必填 |
| POST | `/qa-set/delete` | 是 | `id` |

说明：删除 `qa_set` 会级联删除 `qa_item`、`practice_session`、`practice_session_item`、`qa_set_document_ref`。

### `qa_item`

| 方法 | 路径 | 鉴权 | 请求字段 |
| --- | --- | --- | --- |
| GET | `/qa-item/detail?id=...` | 是 | `id` |
| POST | `/qa-item/query` | 是 | `id?`, `qaSetId?`, `question?`, `knowledgeNote?`, `answer?`, `moduleTag?`, `difficulty?`, `conflictTip?`, `sourceChunkIdsJson?`, `sortOrder?` |
| POST | `/qa-item/create` | 是 | 同上 |
| POST | `/qa-item/update` | 是 | 同上，`id` 必填 |
| POST | `/qa-item/delete` | 是 | `id` |

## Practice

### `practice_session`

| 方法 | 路径 | 鉴权 | 请求字段 |
| --- | --- | --- | --- |
| GET | `/practice-session/detail?id=...` | 是 | `id` |
| POST | `/practice-session/query` | 是 | `id?`, `qaSetId?`, `mode?`, `feedbackMode?`, `status?`, `selectedModule?`, `totalQuestions?`, `answeredCount?`, `score?`, `accuracy?`, `summary?`, `startedAt?`, `finishedAt?` |
| POST | `/practice-session/create` | 是 | 同上 |
| POST | `/practice-session/update` | 是 | 同上，`id` 必填 |
| POST | `/practice-session/delete` | 是 | `id` |

### `practice_session_item`

| 方法 | 路径 | 鉴权 | 请求字段 |
| --- | --- | --- | --- |
| GET | `/practice-session-item/detail?id=...` | 是 | `id` |
| POST | `/practice-session-item/query` | 是 | `id?`, `sessionId?`, `qaItemId?`, `sortOrder?`, `userAnswer?`, `result?`, `score?`, `feedbackSummary?`, `answeredAt?` |
| POST | `/practice-session-item/create` | 是 | 同上 |
| POST | `/practice-session-item/update` | 是 | 同上，`id` 必填 |
| POST | `/practice-session-item/delete` | 是 | `id` |

## 错误码

| code | 含义 |
| --- | --- |
| `0` | success |
| `40000` | bad request |
| `40100` | unauthorized |
| `40300` | forbidden |
| `40400` | not found |
| `40900` | conflict |
| `50000` | internal error |

