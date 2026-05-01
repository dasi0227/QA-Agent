# 前后端 API 冲突清单

## 1. 对照范围

- 前端依据：`frontend/src/lib/api/client.ts`、`frontend/src/lib/api/hooks.ts`、`frontend/src/lib/api/types.ts`
- 后端依据：`docs/API.md`
- 第一版目标依据：`docs/PRD.md`、`docs/TABLE.md`

本文只记录两类问题：

1. 前端需要的 API，但 `docs/API.md` 没有提供
2. 前端需要的 API，`docs/API.md` 提供了，但字段数量或字段含义不一致

## 2. 全局冲突

### 2.1 返回体包裹格式不一致

- 前端 `apiRequest` 默认按 `{ success, data, error }` 解析
- 后端 `API.md` 约定返回 `Result<T>`，字段是 `{ code, msg, data }`
- 这会导致前端即使请求成功，也拿不到预期的错误语义和成功语义

### 2.2 路径风格不一致

- 前端统一请求 `/api/...`
- 后端 `API.md` 展示的是 `/auth/...`、`/user-profile/...`、`/qa-set/...` 这一套路径
- 前端大量使用 REST 风格路径，例如 `/api/qa-sets/{id}`、`/api/practice-sessions/{id}/finish`
- 后端 `API.md` 主要是 CRUD 风格路径，例如 `/qa-set/detail`、`/qa-set/update`

### 2.3 字段命名风格不一致

- 前端主要使用语义化业务字段，如 `targetDirection`、`interviewAnswer`、`questionSetId`
- 后端 `API.md` 主要使用数据库映射字段，如 `targetDomain`、`answer`、`qaSetId`
- 前端虽然在部分地方做了兼容映射，但仍有很多核心字段没有对齐

## 3. 前端需要但后端没有提供的 API

### 3.1 当前用户

- 前端需要：`GET /api/auth/me`
- 后端 `API.md` 未提供
- 前端用途：启动后恢复登录态、读取当前用户

### 3.2 Profile 简化接口

- 前端需要：`GET /api/profile`
- 前端需要：`PUT /api/profile`
- 后端只提供：`/user-profile/detail`、`/user-profile/create`、`/user-profile/update`、`/user-profile/delete`
- 结论：前端依赖的是“当前用户唯一 Profile”的简化接口，后端文档没有提供同路径能力

### 3.3 资料库列表与详情 REST 接口

- 前端需要：`GET /api/documents`
- 前端需要：`GET /api/documents/{documentId}`
- 前端需要：`DELETE /api/documents/{documentId}`
- 后端只提供：`/source-document/detail`、`/source-document/query`、`/source-document/delete`
- 结论：后端文档没有提供前端实际使用的 REST 路径

### 3.4 资料上传接口

- 前端需要：`POST /api/documents/upload`
- 请求方式：`multipart/form-data`，字段名是 `files`
- 后端 `API.md` 未提供上传接口
- 这和 `PRD.md` 第一版“上传资料”主链路直接冲突

### 3.5 问答集列表、详情、删除、更新 REST 接口

- 前端需要：`GET /api/qa-sets`
- 前端需要：`GET /api/qa-sets/{questionSetId}`
- 前端需要：`PUT /api/qa-sets/{questionSetId}`
- 前端需要：`DELETE /api/qa-sets/{questionSetId}`
- 后端只提供：`/qa-set/detail`、`/qa-set/query`、`/qa-set/update`、`/qa-set/delete`
- 结论：路径形态不一致，前端不能直接按现有方式接后端

### 3.6 问答集生成任务接口

- 前端需要：`POST /api/qa-sets/generate`
- 前端需要：`GET /api/jobs/{taskId}`
- 后端 `API.md` 未提供
- `TABLE.md` 第一版明确存在：
  - `qa_generation_task`
  - `qa_generation_task_message`
- 结论：前端主链路已依赖异步生成任务，但后端文档缺失对应接口

### 3.7 题目新增嵌套路由

- 前端需要：`POST /api/qa-sets/{questionSetId}/items`
- 后端只提供：`POST /qa-item/create`
- 结论：前端使用“问答集下新增题目”的路径，后端文档未提供

### 3.8 题目更新与删除 REST 接口

- 前端需要：`PUT /api/qa-items/{questionItemId}`
- 前端需要：`DELETE /api/qa-items/{questionItemId}`
- 后端只提供：`/qa-item/update`、`/qa-item/delete`

### 3.9 练习主流程接口

- 前端需要：`POST /api/practice-sessions`
- 前端需要：`GET /api/practice-sessions/{sessionId}`
- 前端需要：`GET /api/practice-sessions/{sessionId}/result`
- 前端需要：`POST /api/practice-sessions/{sessionId}/answer`
- 前端需要：`POST /api/practice-sessions/{sessionId}/mark-unknown`
- 前端需要：`POST /api/practice-sessions/{sessionId}/continue`
- 前端需要：`POST /api/practice-sessions/{sessionId}/finish`
- 后端只提供：`practice_session` / `practice_session_item` 的基础 CRUD
- 结论：后端文档没有覆盖前端正在使用的练习闭环接口

## 4. 已提供但字段数量和内容不一致

### 4.1 Auth

#### `POST /api/auth/login` vs `POST /auth/login`

- 前端请求字段：`account`, `password`, `remember`
- 后端文档字段：`username`, `password`
- 冲突点：
  - 前端用 `account`，后端只认 `username`
  - 前端传 `remember`，后端文档没有这个字段

#### `POST /api/auth/register` vs `POST /auth/register`

- 前端请求字段：`username`, `name`, `email`, `password`
- 后端文档字段：`username`, `email`, `password`
- 冲突点：
  - 前端额外传 `name`

#### 登录 / 注册 / 刷新响应字段

- 前端期望：
  - `accessToken` 或 `token`
  - `refreshToken`
  - 用户字段直接平铺在响应 `data` 中，至少包括 `id/userId`、`username`、`email`，最好还有 `profileCompleted`
- 后端文档提供：
  - `userId`, `username`, `email`, `accessToken`, `refreshToken`
- 冲突点：
  - 前端还依赖 `profileCompleted`
  - 前端 `AuthUser` 还预留了 `displayName`、`status`

### 4.2 Profile

#### 前端请求 / 响应字段

- 前端需要字段：
  - `targetRole`
  - `targetDirection`
  - `allowGeneralKnowledge`
  - `answerStyle`
  - `feedbackStyle`
  - `grade`
  - `education`
  - `stage`
  - `companyType`
  - `note`

#### 后端文档字段

- `targetRole`
- `targetDomain`
- `targetCompany`
- `allowGeneralKnowledge`
- `allowWebSearch`
- `answerStyle`
- `feedbackStyle`
- `age`
- `grade`
- `major`
- `stage`

#### 冲突点

- 字段重命名不一致：
  - 前端 `targetDirection`，后端 `targetDomain`
  - 前端 `companyType`，后端 `targetCompany`
  - 前端 `education`，后端 `major`
- 前端缺少但后端需要：
  - `allowWebSearch`
  - `age`
- 前端多出但第一版不支持：
  - `note`
- `TABLE.md` 第一版明确“不保留 note”，所以这里以前端字段为主不合适

### 4.3 资料 `source_document`

#### 前端需要字段

- `id`
- `fileName`
- `fileType`
- `size`
- `createdAt`
- `updatedAt`
- `rawContent`
- `normalizedText`
- `summary`
- `contentPreview`
- `chunkCount`
- `usedInGeneration`

#### 后端文档字段

- `id`
- `fileName`
- `fileType`
- `filePath`
- `rawContent`
- `normalizedContent`
- `summary`
- `moduleTagsJson`
- `referenceCount`
- `deleted`

#### 冲突点

- 字段名不一致：
  - 前端 `normalizedText`，后端 `normalizedContent`
- 前端需要但后端文档没有：
  - `size`
  - `contentPreview`
  - `chunkCount`
  - `usedInGeneration`
- 后端有但前端基本没接：
  - `filePath`
  - `moduleTagsJson`
  - `referenceCount`
  - `deleted`
- 按 `PRD.md` / `TABLE.md`，第一版重点是资料正文、摘要、模块结构、引用统计
- 因此这里最关键缺口不是 `size`，而是前端没有真正接 `moduleTagsJson` 和 `referenceCount`

### 4.4 问答集 `qa_set`

#### 前端需要字段

- `id`
- `title`
- `note`
- `moduleTags`
- `questionCount`
- `practiceCount`
- `averageScore`
- `lastPracticedAt`
- `status`
- `documentCount`
- `createdAt`
- `updatedAt`

#### 后端文档字段

- `id`
- `taskId`
- `title`
- `description`
- `moduleTagsJson`
- `questionCount`
- `practiceCount`
- `averageScore`
- `bestScore`
- `averageAccuracy`
- `bestAccuracy`
- `lastPracticedAt`

#### 冲突点

- 字段重命名不一致：
  - 前端 `note`，后端 `description`
  - 前端 `moduleTags`，后端 `moduleTagsJson`
- 前端需要但后端文档没有：
  - `status`
  - `documentCount`
  - `createdAt`
  - `updatedAt`
- 后端有但前端没接：
  - `taskId`
  - `bestScore`
  - `averageAccuracy`
  - `bestAccuracy`
- `TABLE.md` 第一版明确：
  - 保留 `description`
  - 不保留 `status`
- 所以前端当前 `note`、`status` 设计与第一版目标不一致

### 4.5 问答集详情

- 前端读取 `GET /api/qa-sets/{id}` 时，默认期望返回：
  - `qaSet`
  - `items`
- 后端 `API.md` 只有：
  - `/qa-set/detail`
  - `/qa-item/query`
- 冲突点：
  - 即使后端能分别查到，也没有提供前端当前依赖的聚合返回结构

### 4.6 题目 `qa_item`

#### 前端需要字段

- `id`
- `questionSetId`
- `question`
- `knowledgeNote`
- `interviewAnswer`
- `moduleTag`
- `tags`
- `sortOrder`
- `status`
- `difficulty`
- `conflictTip`
- `scoringRubric`
- `sourceChunkIds`

#### 后端文档字段

- `id`
- `qaSetId`
- `question`
- `knowledgeNote`
- `answer`
- `moduleTag`
- `difficulty`
- `conflictTip`
- `sourceChunkIdsJson`
- `sortOrder`

#### 冲突点

- 字段重命名不一致：
  - 前端 `questionSetId`，后端 `qaSetId`
  - 前端 `interviewAnswer`，后端 `answer`
  - 前端 `sourceChunkIds`，后端 `sourceChunkIdsJson`
- 前端需要但后端文档没有：
  - `tags`
  - `status`
  - `scoringRubric`
- `TABLE.md` 第一版明确：
  - 不保留 `tags_json`
- 所以前端的 `tags`、`status`、`scoringRubric` 都超出了第一版正式字段边界

### 4.7 生成任务 `qa_generation_task`

#### 前端需要字段

- `id`
- `title`
- `note`
- `allowGeneralKnowledge`
- `requestedQuestionCount`
- `type`
- `targetId`
- `status`
- `stage`
- `progress`
- `message`
- `errorMessage`
- `documentIds`
- `documentNames`
- `createdAt`
- `updatedAt`
- `startedAt`
- `completedAt`
- `questionSetId`

#### 第一版表设计字段

- `id`
- `title`
- `note`
- `document_ids_json`
- `qa_set_id`
- `status`
- `stage`
- `error_message`
- `allow_general_knowledge`
- `allow_web_search`
- `requested_question_count`
- `created_at`
- `started_at`
- `completed_at`
- `updated_at`

#### 冲突点

- 前端需要但第一版主表没有：
  - `progress`
  - `message`
  - `type`
  - `targetId`
  - `documentNames`
- `TABLE.md` 第一版明确：
  - 主表不保留 `progress`
  - 主表不保留 `message`
  - 阶段消息应来自 `qa_generation_task_message`
- 所以前端当前任务字段模型明显超出第一版设计

### 4.8 练习会话 `practice_session`

#### 前端需要字段

- `id`
- `questionSetId`
- `questionSetTitle`
- `mode`
- `feedbackMode`
- `status`
- `currentQuestionIndex`
- `totalQuestions`
- `currentQuestion`
- `answeredCount`
- `canRevealAnswer`
- `currentAnswer`
- `feedback`
- `answerGuide`
- `score`
- `summary`
- `strengths`
- `gaps`
- `moduleResults`
- `latestAnswer`

#### 后端文档字段

- `id`
- `qaSetId`
- `mode`
- `feedbackMode`
- `status`
- `selectedModule`
- `totalQuestions`
- `answeredCount`
- `score`
- `accuracy`
- `summary`
- `startedAt`
- `finishedAt`

#### 冲突点

- 字段重命名不一致：
  - 前端 `questionSetId`，后端 `qaSetId`
- 前端需要但后端文档没有：
  - `questionSetTitle`
  - `currentQuestionIndex`
  - `currentQuestion`
  - `canRevealAnswer`
  - `currentAnswer`
  - `feedback`
  - `answerGuide`
  - `strengths`
  - `gaps`
  - `moduleResults`
  - `latestAnswer`
- 后端有但前端未重点使用：
  - `selectedModule`
  - `accuracy`
  - `startedAt`
  - `finishedAt`
- `TABLE.md` 第一版明确：
  - 不保留 `module_results_json`
- 所以前端当前会话结果字段比第一版要重

### 4.9 练习答题结果 `practice_session_item`

#### 前端需要的答题返回结构

- `sessionId`
- `score`
- `result`
- `currentAnswer`
- `feedback`
- `answerGuide`
- `standardAnswer`
- `nextQuestion`
- `feedbackDetail`
- `missingPoints`
- `suggestions`
- `evidenceRefs`

#### 后端文档已提供字段

- `id`
- `sessionId`
- `qaItemId`
- `sortOrder`
- `userAnswer`
- `result`
- `score`
- `feedbackSummary`
- `answeredAt`

#### 冲突点

- 前端需要但后端文档没有：
  - `currentAnswer`
  - `feedback`
  - `answerGuide`
  - `standardAnswer`
  - `nextQuestion`
  - `feedbackDetail`
  - `missingPoints`
  - `suggestions`
  - `evidenceRefs`
- 后端只有 `feedbackSummary`，信息量不足以支撑前端当前页面

### 4.10 练习结果页

#### 前端需要字段

- `sessionId`
- `questionSetId`
- `score`
- `summary`
- `strengths`
- `gaps`
- `moduleResults`
- `reviewOrder`
- `evidenceRefs`
- `detail`
- `completedCount`
- `totalCount`

#### 后端文档可提供字段

- 只能从 `practice_session` 得到：
  - `qaSetId`
  - `score`
  - `summary`
  - `answeredCount`
  - `totalQuestions`
  - `accuracy`

#### 冲突点

- 前端结果页所需的大部分结果字段，在 `API.md` 中都没有正式定义
- 其中 `strengths`、`gaps`、`reviewOrder`、`evidenceRefs`、`detail` 都超出了第一版表设计

## 5. 以第一版为目标，最应该做的字段调整

下面只谈字段，不建议在这一轮大改前后端整体结构。

### 5.1 前端最应该调整的字段

#### Profile

- 把 `targetDirection` 改成 `targetDomain`
- 把 `companyType` 改成 `targetCompany`
- 把 `education` 改成 `major`
- 补上 `allowWebSearch`
- 评估是否补上 `age`
- 删除 `note`，因为 `TABLE.md` 第一版明确不保留

#### 问答集

- 把 `note` 改成 `description`
- 把 `moduleTags` 改成从 `moduleTagsJson` 解析
- 不再把 `status` 作为第一版正式字段依赖
- 不再把 `documentCount` 作为第一版必需字段依赖
- 补接 `bestScore`、`averageAccuracy`、`bestAccuracy`

#### 题目

- 把 `questionSetId` 和后端 `qaSetId` 对齐
- 把 `interviewAnswer` 改成 `answer`
- 把 `sourceChunkIds` 改成从 `sourceChunkIdsJson` 解析
- 删除第一版不需要的 `tags`
- 删除第一版不需要的 `status`
- 删除第一版不需要的 `scoringRubric`

#### 生成任务

- 删除对 `progress` 的正式依赖
- 删除对主表 `message` 的正式依赖
- 若要展示阶段消息，改为单独消费任务消息流
- `documentNames` 不应作为第一版任务主返回必填字段

#### 练习

- 第一版先把结果字段收敛到：
  - `score`
  - `summary`
  - `answeredCount`
  - `totalQuestions`
  - `accuracy`
  - 单题 `feedbackSummary`
- 不再把 `strengths`、`gaps`、`moduleResults`、`reviewOrder`、`evidenceRefs` 作为第一版必需字段

### 5.2 后端最应该调整的字段

#### Auth

- 保持现有登录字段 `username` 也可以
- 但最好兼容接收前端的 `account`
- 登录 / 注册 / 刷新响应里建议补 `profileCompleted`

#### 资料

- 在资料详情和列表响应中，至少补齐前端第一版真正需要的：
  - `createdAt`
  - `updatedAt`
  - `rawContent`
  - `normalizedContent`
  - `summary`
  - `moduleTagsJson`
  - `referenceCount`
- 如果不准备正式支持：
  - `size`
  - `contentPreview`
  - `chunkCount`
  - `usedInGeneration`
  则应由前端降级，不应由后端为兼容临时造字段

#### 问答集

- 在 `qa_set` 返回里坚持第一版字段：
  - `description`
  - `moduleTagsJson`
  - `questionCount`
  - `practiceCount`
  - `averageScore`
  - `bestScore`
  - `averageAccuracy`
  - `bestAccuracy`
  - `lastPracticedAt`
- 不建议为了适配当前前端再回退增加 `note` 或 `status`

#### 题目

- 在 `qa_item` 返回里坚持第一版字段：
  - `qaSetId`
  - `question`
  - `knowledgeNote`
  - `answer`
  - `moduleTag`
  - `difficulty`
  - `conflictTip`
  - `sourceChunkIdsJson`
  - `sortOrder`
- 不建议第一版补 `tags`、`status`、`scoringRubric`

#### 生成任务

- 任务主接口字段应按第一版表设计返回
- 阶段消息单独提供任务消息接口，不要把 `message`、`progress` 硬塞回主表
- 建议补上 `allowWebSearch`，因为 `TABLE.md` 里第一版任务表有该字段

#### 练习

- 第一版练习结果先收敛到表内已有字段：
  - `score`
  - `accuracy`
  - `summary`
  - `answeredCount`
  - `totalQuestions`
  - `feedbackSummary`
- 不建议第一版直接承诺：
  - `strengths`
  - `gaps`
  - `moduleResults`
  - `reviewOrder`
  - `evidenceRefs`
  - `feedbackDetail`

## 6. 结论

按 `PRD.md` 和 `TABLE.md` 的第一版目标看，当前主要不是“后端字段太少”，而是“前端字段模型明显比第一版更重”。

最稳妥的做法是：

1. 后端补齐第一版真正缺失的业务接口和正式字段
2. 前端收敛到第一版字段边界，删除超前字段依赖
3. 等第一版资产链路稳定后，再逐步把生成任务细节、反馈详情、评分细节重新加回来
