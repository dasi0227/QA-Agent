# V5.5 QuizPage 测试 / 刷题页面设计

## 1. 文档目标

本文定义 `QA_Agent` 测试 / 刷题核心模块的产品形态、前端页面结构、后端接口、数据流、进度恢复、数据库调整和实施顺序。

本次设计目标不是做一个普通表单页，而是建立一个真正可用的练习闭环：

1. 用户能从题集进入一轮练习。
2. 用户能专注作答、切题、标记不会。
3. 系统能保存草稿和当前进度。
4. 用户刷新、退出、稍后回来时能继续。
5. 逐题反馈模式下，用户提交本题后看到结构化反馈。
6. 整轮反馈模式下，提交整轮前不暴露正确 / 错误。
7. 完成后能查看整轮结果。
8. 后端能保存足够数据支撑历史回看和 Agent 输出排查。

## 1.1 2026-05-23 实现口径更新

本节覆盖本文早期设计中关于整轮反馈和旧接口命名的内容：

1. 单题接口以当前实现为准，统一使用 `/practice/item/save`、`/practice/item/unknown`、`/practice/item/answer`。
2. `ITEM_BY_ITEM`：提交本题时调用 FeedbackAgent，立即展示本题反馈，已提交题不可修改。
3. `AFTER_ALL`：做题阶段只通过 `/practice/item/save` 保存草稿和用时，不调用 FeedbackAgent；提交本轮时后端逐题调用 FeedbackAgent，再调用 AssessAgent。
4. 做题用时以 `practice_session.duration_seconds` 为准，不使用 `finished_at - started_at` 推算。
5. 新增只读回看页 `/practice/:sessionId/review?index=0`，从结果页题目明细进入。
6. 新增练习历史接口 `/practice/session/history?qaSetId=...`，只展示 `FINISHED` 会话。

## 2. 当前项目判断

### 2.1 前端现状

当前前端是 React + React Router + React Query，不是 Vue / Pinia 技术栈。

现有页面中：

1. `QuizPage` 已经具备题集选择、练习模式、反馈模式的 UI 雏形，但“开始练习”和“继续测试”仍是未实现提示。
2. `QAPage` 仍使用静态 mock 题目，未接真实 `practice_session`。
3. `ResultPage` 仍使用静态 mock 结果，未接真实 `AssessAgent` 输出。
4. `/practice/:sessionId` 当前挂在 `AppShell` 下，会带顶部导航和页脚，不适合沉浸式做题。
5. `frontend/src/lib/practice-history.ts` 只有 localStorage 快照能力，不能作为正式进度来源。

结论：正式刷题页不应继续沿用后台页结构，应改成独立沉浸式布局。

### 2.2 后端现状

当前后端已经具备练习会话和 Agent 能力底座：

1. `practice_session` 表表示一轮练习。
2. `practice_session_item` 表表示一轮练习中的单题作答结果。
3. `FeedbackAgent` 已可由 Practice Flow 的单题提交链路编排调用。
4. `AssessAgent` 已可由 Practice Flow 的整轮提交链路编排调用。
5. `FeedbackAgent` 能输出单题判定、分数、摘要、缺失点、错误点、改进建议、参考回答或不会提示。
6. `AssessAgent` 能输出整轮得分、达标率、正确 / 不足 / 错误 / 不会数量、整体点评、优势、薄弱点、复习建议和内部记忆线索。

旧接口曾偏 CRUD，不足以直接支撑高质量刷题页：

1. 没有“开始一轮练习”的领域化接口。
2. 没有“查询未完成进度”的明确接口。
3. 没有“会话详情 + 题目 + 答题记录”的聚合接口。
4. 没有保存当前题号。
5. 没有明确草稿状态。
6. 没有整轮反馈模式下的提交语义。
7. 没有重开测试和放弃会话语义。
8. Agent 输出后的结构化 feedback / assess 需要能从业务表稳定恢复。

## 3. 产品形态

测试 / 刷题页面应定位为“训练会话工作台”，不是后台 CRUD 页，也不是题目详情页。

用户进入页面后只处理一件事：围绕当前 `practice_session` 做题、保存、提交、恢复、看反馈。

### 3.1 反馈模式

保留两种反馈模式：

1. `ITEM_BY_ITEM`：逐题反馈。
2. `AFTER_ALL`：整轮反馈。

本次 V5.5 的实现优先级：

1. 优先完整打通逐题反馈闭环。
2. 整轮反馈的 UI、状态和数据结构必须预留。
3. 整轮批量判题可以后续增强，避免第一版引入过长同步等待。

### 3.2 题目状态

建议区分“作答状态”和“判题结果”。

`practice_session_item.status` 表示作答状态：

```text
UNANSWERED
DRAFT
UNKNOWN
SUBMITTED
```

`practice_session_item.result` 表示判题结果：

```text
PERFECT
CORRECT
DEFICIENT
WRONG
UNKNOWN
```

不要用 `result` 兼做“未做 / 已保存 / 不会 / 已提交”。这会让前端状态映射和整轮反馈模式变得混乱。

### 3.3 会话状态

`practice_session.status` 建议保持克制：

```text
IN_PROGRESS
FINISHED
ABANDONED
```

不建议新增 `PAUSED`。用户退出并保存，本质仍是 `IN_PROGRESS`。

## 4. 页面布局

### 4.1 桌面端布局

桌面端采用沉浸式双区布局：

1. 顶部轻量状态栏：退出、题集名、反馈模式、保存状态、用时。
2. 左侧主作答区：题号、题干、标签、难度、作答框、操作按钮。
3. 右侧答题卡：圆形题号网格、状态图例、提交整轮、退出并保存。
4. 答题卡支持折叠，折叠后只保留窄侧栏或悬浮入口。

页面不使用现有 `AppShell` 的顶部导航、页脚和首页装饰。

### 4.2 移动端布局

移动端不固定右侧答题卡。

推荐方案：

1. 主区域保持单列。
2. 答题卡改为底部抽屉。
3. 页面底部或右下角保留“答题卡 3/25”入口。

### 4.3 颜色方案

保持当前项目的暖纸色、半透明白、墨色文字、棕金和蓝灰体系。

答题卡不使用纯黑大面板。推荐使用暖炭灰：

```text
panel: #292520 或 #2f2a24
current ring: #6f8498
correct: #4f8a67
wrong: #b55a4c
unknown: #d7b957
unanswered: #6f675d
answered pending submit: #f7f1e8
```

原因：

1. 纯黑适合按钮和小面积强调，不适合整块答题卡。
2. 暖炭灰能形成沉浸感，又不破坏原有暖纸质感。
3. 状态色降低饱和度后更符合当前项目视觉语言。

## 5. 核心区域展示规则

### 5.1 提交前

默认展示：

1. 当前题号。
2. 题干。
3. 题型 / 标签 / 难度。
4. 作答框。
5. 上一题、下一题、不会、提交本题。
6. 保存状态。

默认不展示：

1. 标准答案。
2. 解析。
3. 来源切片。
4. 本题反馈容器。
5. 大段知识笔记。

提交前隐藏反馈区域，避免用户还没作答时被空反馈卡片干扰。

### 5.2 提交后

逐题反馈模式下，提交本题后展示结构化反馈。

普通判题分支展示：

1. `result + score`：结果徽标。
2. `feedbackSummary`：反馈摘要。
3. `judgeDetail.missingPoints`：缺失点。
4. `judgeDetail.wrongPoints`：错误点。
5. `judgeDetail.improvementAdvice`：改进建议。
6. `judgeDetail.betterAnswer`：参考回答，默认折叠。
7. `sourceChunks`：证据引用，默认折叠。

不会分支展示：

1. `result = UNKNOWN`。
2. `score = 0`。
3. `feedbackSummary`。
4. `hintDetail.memoryTip`。
5. `hintDetail.encouragement`。
6. `sourceChunks`，默认折叠。

整轮反馈模式下，在整轮提交前不展示正确 / 错误 / 标准答案 / 参考回答。

## 6. 答题卡设计

答题卡不是普通目录，而是圆形题号网格。

### 6.1 逐题反馈模式

状态颜色：

1. 当前题：蓝灰描边或高亮环。
2. 正确：绿色。
3. 错误：红色。
4. 不会：黄色。
5. 未做：暖灰。
6. 草稿：纸白或浅色。

`PERFECT`、`CORRECT` 可以合并为绿色。

`DEFICIENT` 可视为“部分正确”。第一版建议归入绿色或单独使用弱绿色 / 橙色。为了降低复杂度，第一版可以合并进绿色，同时在反馈徽标里显示 `DEFICIENT`。

### 6.2 整轮反馈模式

整轮提交前：

1. 已作答：纸白。
2. 不会：黄色。
3. 未作答：暖灰。
4. 当前题：蓝灰描边。
5. 不显示红绿。

整轮提交后：

1. 正确：绿色。
2. 错误：红色。
3. 不会：黄色。
4. 部分正确：可使用绿色或橙色，按最终视觉规则决定。

### 6.3 折叠行为

答题卡支持折叠。

展开时：

1. 显示状态图例。
2. 显示题号网格。
3. 显示提交整轮和退出并保存。

折叠时：

1. 保留窄侧栏或悬浮按钮。
2. 显示当前题号。
3. 点击可展开答题卡。

## 7. 前端组件拆分

第一版不做过细拆分，避免组件森林。

建议 5 个核心组件：

```text
PracticePage
PracticeLayout
QuestionWorkspace
QuestionFeedbackPanel
AnswerCard
```

职责：

1. `PracticePage`：页面容器，负责接口、当前题索引、保存 / 提交状态、跳转结果页。
2. `PracticeLayout`：独立沉浸式布局，处理左右栏、答题卡折叠、移动端结构。
3. `QuestionWorkspace`：题干、标签、作答框、操作按钮。它们强绑定当前题，第一版不拆太碎。
4. `QuestionFeedbackPanel`：结构化反馈展示。反馈字段多，且逐题反馈 / 整轮反馈展示规则不同，适合独立。
5. `AnswerCard`：圆形题号网格、状态映射、点击跳题、折叠状态。

弹窗先复用现有 `ConfirmDialog`，不单独创建 `ConfirmExitDialog` / `SessionResumeDialog`。

## 8. 路由设计

建议调整为：

```text
/quiz
/practice/:sessionId
/practice/:sessionId/result
```

说明：

1. `/quiz` 负责题集选择、练习模式、反馈模式、开始 / 继续。
2. `/practice/:sessionId` 使用独立沉浸式布局，不挂 `AppShell`。
3. `/practice/:sessionId/result` 展示整轮结果。

如果为了减少改动，也可以继续保留 `/result/:sessionId`，但新页面语义上更推荐挂在 practice 下。

## 9. 进度恢复机制

进度以服务端为准，localStorage 只做兜底。

### 9.1 服务端保存

服务端保存：

1. 当前 session。
2. 当前题号 `current_index`。
3. 每题草稿答案。
4. 每题状态。
5. 不会标记。
6. 单题反馈结果。
7. 开始时间。
8. 最近活跃时间。
9. 完成时间。
10. 反馈模式。
11. 练习模式。

### 9.2 localStorage 保存

localStorage 只保存：

1. 最近 `sessionId`。
2. `qaSetId`。
3. `currentIndex`。
4. `updatedAt`。

localStorage 不作为权威数据源。

### 9.3 进入流程

用户在 `/quiz` 选择题集时：

1. 调 `GET /practice/session/exist?qaSetId=...`。
2. 如果存在未完成会话，提示继续上次进度或重新开始。
3. 继续时跳转 `/practice/:sessionId`。
4. 重新开始时调用 `restart`，创建新 session。

用户刷新 `/practice/:sessionId` 时：

1. 直接使用 URL 中的 `sessionId` 查询服务端。
2. 如果服务端可用，以服务端为准恢复。
3. 如果服务端失败但本地有快照，只提示“检测到本地记录，需要重新连接服务端”，不直接恢复答题数据。

## 10. 后端接口设计

保留必要查询接口，但新增领域化练习 API，前端刷题页只使用领域化练习 API。

### 10.1 接口列表

```text
POST /practice/session/init
GET  /practice/session/exist?qaSetId=...
GET  /practice/session/detail?sessionId=...
POST /practice/item/save
POST /practice/item/unknown
POST /practice/item/answer
POST /practice/session/submit
POST /practice/session/restart
POST /practice/session/abandon
```

### 10.2 session/init

用途：创建一轮新的练习会话，并创建本轮的 `practice_session_item`。

请求示例：

```json
{
  "qaSetId": "qa-set-id",
  "mode": "SEQUENTIAL",
  "feedbackMode": "ITEM_BY_ITEM",
  "selectedModule": null
}
```

响应：`PracticeSessionDetailResponse`。

要求：

1. 后端根据 `qaSetId` 查询题目。
2. `SEQUENTIAL` 按 `sort_order` 排序。
3. `RANDOM` 后端生成随机顺序并写入 `practice_session_item.sort_order`。
4. 创建 session 时保存 `current_index = 0`。
5. 创建 item 时保存题目快照。

### 10.3 session/exist

用途：查询某题集是否存在未完成会话。

返回：

1. 没有未完成会话：`null`。
2. 有未完成会话：返回轻量 session 摘要。

只返回 `IN_PROGRESS` 状态。

### 10.4 session/detail

用途：返回刷题页需要的一次完整快照。

响应结构：

```json
{
  "session": {
    "id": "session-id",
    "qaSetId": "qa-set-id",
    "qaSetTitle": "Redis 面试题集",
    "mode": "SEQUENTIAL",
    "feedbackMode": "ITEM_BY_ITEM",
    "status": "IN_PROGRESS",
    "currentIndex": 2,
    "totalQuestions": 25,
    "answeredCount": 6,
    "score": null,
    "accuracy": null,
    "summary": null,
    "assessDetail": null,
    "startedAt": "2026-05-21 10:00:00",
    "lastActiveAt": "2026-05-21 10:12:00",
    "finishedAt": null
  },
  "items": [
    {
      "sessionItemId": "session-item-id",
      "qaItemId": "qa-item-id",
      "sortOrder": 1,
      "question": "题干",
      "knowledgeNote": "知识笔记",
      "standardAnswer": "标准答案",
      "moduleTag": "Redis",
      "difficulty": "MEDIUM",
      "keywords": "惰性删除,定期删除",
      "hint": "先从删除触发时机区分两种策略。",
      "sourceChunkIdsJson": "[]",
      "userAnswer": "用户答案",
      "status": "SUBMITTED",
      "unknown": false,
      "result": "CORRECT",
      "score": 90,
      "feedbackSummary": "反馈摘要",
      "judgeDetail": {},
      "hintDetail": {},
      "answeredAt": "2026-05-21 10:10:00",
      "submittedAt": "2026-05-21 10:11:00"
    }
  ]
}
```

### 10.5 item/save

用途：保存草稿答案，不触发 Agent。

请求：

```json
{
  "sessionId": "session-id",
  "sessionItemId": "session-item-id",
  "userAnswer": "用户草稿",
  "currentIndex": 2
}
```

行为：

1. 更新 `user_answer`。
2. 未提交题设置 `status = DRAFT`。
3. 更新 `practice_session.current_index`。
4. 更新 `last_active_at`。
5. 不修改 `result`、`score`、`feedback_*`。

### 10.6 item/unknown

用途：标记不会。

逐题反馈模式下：

1. 可直接调用 `FeedbackAgent` 的 hint 分支。
2. 保存 `result = UNKNOWN`。
3. 保存 `status = SUBMITTED`。
4. 返回 `hintDetail`。

整轮反馈模式下：

1. 只保存 `status = UNKNOWN`、`unknown = true`。
2. 提交整轮前不返回正确 / 错误 / 参考答案。

### 10.7 item/answer

用途：提交单题并返回结构化反馈。

请求：

```json
{
  "sessionId": "session-id",
  "sessionItemId": "session-item-id",
  "userAnswer": "用户答案",
  "currentIndex": 2
}
```

行为：

1. 校验 session 属于当前用户。
2. 校验 session 未完成。
3. 校验 feedbackMode 为 `ITEM_BY_ITEM`。
4. 调用 `FeedbackAgent`。
5. 由 `FeedbackSaver` 保存单题结构化反馈到 `practice_session_item`。
6. 返回 `PracticeFlowItemResponse`。

### 10.8 session/submit

用途：提交整轮并返回整轮结果。

逐题反馈模式：

1. 校验所有题已 `SUBMITTED` 或 `UNKNOWN`。
2. 调用 `AssessAgent`。
3. 更新 session 为 `FINISHED`。
4. 返回整轮评估结果。

整轮反馈模式：

1. 对已作答题批量调用判题。
2. 对 `UNKNOWN` 题写入 UNKNOWN 结果。
3. 全部单题完成后调用 `AssessAgent`。
4. 第一版可暂缓实现批量判题，但接口和状态必须预留。

### 10.9 session/restart

用途：重新开始一轮练习。

行为：

1. 不删除旧 session。
2. 可将旧未完成 session 标为 `ABANDONED`，或保留并创建新 session。
3. 推荐标记旧 session 为 `ABANDONED`，避免 session/exist 查询反复命中旧会话。
4. 创建新 session 并返回 detail。

### 10.10 session/abandon

用途：用户明确放弃当前练习。

行为：

1. 将 session 标为 `ABANDONED`。
2. 不删除历史数据。
3. 不计入题集完成统计。

## 11. 数据库设计判断

### 11.1 当前表是否足够支撑刷题页

当前 `practice_session` 和 `practice_session_item` 可以继续作为刷题业务主表，不需要新增 `TestSession` 或 `AnswerRecord` 替换它们。

但需要补字段，否则无法自然支持草稿、恢复、当前题、整轮反馈和历史复现。

### 11.2 practice_session 调整

建议新增：

```sql
current_index INT NOT NULL DEFAULT 0
last_active_at DATETIME NULL
duration_seconds INT NOT NULL DEFAULT 0
```

说明：

1. `current_index` 用于恢复上次所在题号。
2. `last_active_at` 用于判断最近活跃和恢复提示。
3. `duration_seconds` 用于统计用时。第一版也可以由前端展示动态计时，后端只在保存时更新。

### 11.3 practice_session_item 调整

建议新增：

```sql
status VARCHAR(32) NOT NULL DEFAULT 'UNANSWERED'
unknown TINYINT(1) NOT NULL DEFAULT 0
submitted_at DATETIME NULL
```

建议修改：

```sql
result VARCHAR(32) NULL
```

当前 DDL 中 `result VARCHAR(32) NOT NULL` 不适合未提交题。未提交题本来就不应该有判题结果。

### 11.4 题目快照字段

如果要保证历史练习不受题库后续编辑影响，建议在 `practice_session_item` 新增题目快照：

```sql
question_snapshot LONGTEXT NULL
standard_answer_snapshot LONGTEXT NULL
knowledge_note_snapshot LONGTEXT NULL
keywords_snapshot LONGTEXT NULL
hint_snapshot LONGTEXT NULL
module_tag_snapshot VARCHAR(120) NULL
difficulty_snapshot VARCHAR(32) NULL
source_chunk_ids_snapshot_json JSON NULL
```

推荐新增。

原因：

1. 当前 `practice_session_item` 只引用 `qa_item_id`。
2. 如果之后修改题干、标准答案或知识笔记，历史练习页面会看到被修改后的题目。
3. 训练历史应该反映当时作答时的题目版本。

第一版 `detail` 接口应优先读取快照字段；如果快照为空，再回退读取 `qa_item`。

## 12. Feedback / Assess 输出保存与复现判断

### 12.1 当前 Feedback 保存内容

当前 `FeedbackAgent` 输出保存到 `practice_session_item`：

```text
user_answer
result
score
feedback_summary
feedback_judge_detail
feedback_hint_detail
answered_at
```

其中：

1. `feedback_judge_detail` 保存 `JudgeDetail` JSON。
2. `feedback_hint_detail` 保存 `HintDetail` JSON。
3. 普通判题分支写 `judgeDetail`。
4. 不会分支写 `hintDetail`。

这足够支持历史页面重新展示结构化单题反馈。

### 12.2 当前 Assess 保存内容

当前 `AssessAgent` 输出保存到 `practice_session`：

```text
score
accuracy
correct_count
deficient_count
wrong_count
unknown_count
summary
assessment_detail_json
memory_clue_json
finished_at
```

其中：

1. `assessment_detail_json` 保存用户可读整轮评估。
2. `memory_clue_json` 保存内部记忆线索，不返回前端。
3. `finished_at` 首次完成后不刷新。

这足够支持历史结果页重新展示整轮评估。

### 12.3 复现边界

如果“复现”指重新打开历史页面看到当时结果，当前业务表足够：

1. `practice_session_item` 保存单题 feedback 的结构化结果。
2. `practice_session` 保存整轮 assess 的结构化结果。
3. `FeedbackSaver` / `AssessSaver` 是 Agent 业务输出的唯一保存入口。

如果后续需要严格还原原始 LLM 调用、prompt 版本、模型名、调用参数和阶段耗时，再单独设计审计表；V5.5 不引入额外审计表。

## 13. 后端 DDD 放置方案

根据当前项目分层规则，新增代码按以下方式放置。

### 13.1 types

请求 DTO 放：

```text
backend/qa-agent-types/src/main/java/com/dasi/qa/agent/types/dto/request/practice/
```

响应 DTO 放：

```text
backend/qa-agent-types/src/main/java/com/dasi/qa/agent/types/dto/response/practice/
```

建议新增：

```text
StartPracticeRequest
ActivePracticeRequest
PracticeDetailRequest
SaveAnswerRequest
SubmitItemRequest
SubmitSessionRequest
RestartPracticeRequest
AbandonPracticeRequest
PracticeSessionDetailResponse
PracticeFlowSessionResponse
PracticeFlowItemResponse
```

基础入参校验使用 Jakarta Validation 注解，并由 Controller `@Valid` 触发。

### 13.2 domain

新增业务服务：

```text
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/practice/service/flow/IPracticeFlowService.java
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/practice/service/flow/PracticeFlowService.java
```

职责：

1. 编排开始练习、恢复、保存、提交、重开、放弃。
2. 读取当前用户 ID。
3. 调用仓储接口。
4. 调用 `IFeedbackAgent` 和 `IAssessAgent`。
5. 不写 MyBatis Wrapper。
6. 不直接访问 DB Entity。

扩展仓储接口：

```text
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/practice/repository/IPracticeRepository.java
```

### 13.3 infrastructure

Entity 放：

```text
backend/qa-agent-infrastructure/src/main/java/com/dasi/qa/agent/infrastructure/persistent/entity/
```

Mapper 放：

```text
backend/qa-agent-infrastructure/src/main/java/com/dasi/qa/agent/infrastructure/persistent/mapper/mysql/
```

Repository 实现放：

```text
backend/qa-agent-infrastructure/src/main/java/com/dasi/qa/agent/infrastructure/repository/
```

重点：

1. `PracticeRepository` 可聚合 `PracticeSessionMapper`、`PracticeSessionItemMapper`、`QaSetMapper`、`QaItemMapper`。
2. Agent 结构化输出由 `FeedbackSaver` / `AssessSaver` 经 `IAgentRepository` 写入业务表。
3. 事务写库放 infrastructure repository 或由 domain service 调用具备事务的方法。

### 13.4 interfaces

新增 Controller：

```text
backend/qa-agent-interfaces/src/main/java/com/dasi/qa/agent/interfaces/controller/PracticeController.java
```

职责：

1. 接收 HTTP 请求。
2. 触发 `@Valid`。
3. 调用 `IPracticeFlowService`。
4. 返回 `Result<T>`。

Controller 不写业务编排。

### 13.5 application

仅修改 SQL：

```text
backend/qa-agent-application/src/main/resources/sql/table.sql
```

如果没有新增配置，不需要修改 configuration。

## 14. 前端 API 封装

### 14.1 类型

在 `frontend/src/lib/api/types.ts` 增加：

```text
PracticeMode
PracticeFeedbackMode
PracticeSessionStatus
PracticeItemStatus
PracticeFlowSession
PracticeFlowItem
PracticeSessionDetail
StartPracticeInput
SaveAnswerInput
SubmitItemInput
SubmitSessionInput
```

### 14.2 hooks

在 `frontend/src/lib/api/hooks.ts` 增加：

```text
useExistingPracticeSessionQuery
usePracticeDetailQuery
useStartPracticeMutation
useSavePracticeAnswerMutation
useMarkPracticeUnknownMutation
useSubmitPracticeItemMutation
useSubmitPracticeSessionMutation
useRestartPracticeMutation
useAbandonPracticeMutation
```

前端页面只依赖领域化练习 API，不再直接拼 CRUD。

## 15. 前端数据流

### 15.1 开始练习

```text
QuizPage
  -> session/init
  -> /practice/:sessionId
  -> session/detail
```

### 15.2 恢复练习

```text
QuizPage
  -> session/exist
  -> 用户选择继续
  -> /practice/:sessionId
  -> session/detail
```

### 15.3 保存草稿

```text
AnswerEditor 输入
  -> debounce item/save
  -> item.status = DRAFT
  -> 更新保存状态
```

注意：

1. 保存需要防抖。
2. 需要处理请求竞态，避免旧请求覆盖新答案。
3. 页面切题前可立即 flush 当前草稿。

### 15.4 提交本题

```text
item/answer
  -> FeedbackAgent
  -> 保存反馈
  -> 返回结构化 feedback
  -> 更新答题卡状态
```

提交前反馈隐藏，提交后展示 `QuestionFeedbackPanel`。

### 15.5 标记不会

逐题反馈模式：

```text
item/unknown
  -> Hint 分支
  -> result = UNKNOWN
  -> 展示 hintDetail
```

整轮反馈模式：

```text
item/unknown
  -> 只保存 unknown 状态
  -> 不展示答案和判题
```

### 15.6 完成整轮

```text
session/submit
  -> 校验全部完成
  -> AssessAgent
  -> session.status = FINISHED
  -> 跳转结果页
```

## 16. 实施顺序

建议按以下顺序实施：

1. 修改数据库结构：补 session、session item、题目快照。
2. 修改 Entity、Mapper、DTO。
3. 新增 Practice Flow 后端接口。
4. 补强 `FeedbackSaver` / `AssessSaver` 的业务保存。
5. 更新前端 API 类型和 hooks。
6. 调整路由，使 `/practice/:sessionId` 使用独立沉浸式布局。
7. 实现 `PracticePage`、`PracticeLayout`、`QuestionWorkspace`、`QuestionFeedbackPanel`、`AnswerCard`。
8. 改造 `QuizPage` 的开始 / 继续入口。
9. 改造 `ResultPage` 接真实数据。
10. 更新 `docs/API.md` 和 `docs/TABLE.md`。

## 17. 建议修改文件

### 17.1 后端

```text
backend/qa-agent-interfaces/src/main/java/com/dasi/qa/agent/interfaces/controller/PracticeFlowController.java
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/practice/service/flow/IPracticeFlowService.java
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/practice/service/flow/PracticeFlowService.java
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/practice/repository/IPracticeRepository.java
backend/qa-agent-infrastructure/src/main/java/com/dasi/qa/agent/infrastructure/repository/PracticeRepository.java
backend/qa-agent-infrastructure/src/main/java/com/dasi/qa/agent/infrastructure/persistent/entity/PracticeSession.java
backend/qa-agent-infrastructure/src/main/java/com/dasi/qa/agent/infrastructure/persistent/entity/PracticeSessionItem.java
backend/qa-agent-application/src/main/resources/sql/table.sql
```

### 17.2 前端

```text
frontend/src/pages/PracticePage.tsx
frontend/src/components/practice/PracticeLayout.tsx
frontend/src/components/practice/QuestionWorkspace.tsx
frontend/src/components/practice/QuestionFeedbackPanel.tsx
frontend/src/components/practice/AnswerCard.tsx
frontend/src/router/routes.tsx
frontend/src/pages/QuizPage.tsx
frontend/src/pages/ResultPage.tsx
frontend/src/lib/api/types.ts
frontend/src/lib/api/hooks.ts
frontend/src/styles/globals.css
```

`frontend/src/pages/QAPage.tsx` 可以替换为 `PracticePage`，或者保留一层导出壳减少路由改动。

## 18. 验证策略

### 18.1 后端验证

至少验证：

1. `session/init` 能创建 session 和全部 session items。
2. `session/exist` 只返回未完成会话。
3. `session/detail` 能返回题目快照和答题状态。
4. `item/save` 不触发反馈，只保存草稿。
5. `item/answer` 能保存 feedback。
6. `item/unknown` 能保存 UNKNOWN。
7. `session/submit` 未完成时拒绝，完成后写 assess。
8. `session/restart` 不破坏旧历史，新建会话。
9. `session/abandon` 不删除历史数据。
10. 用户隔离正常，不能访问别人的 session。
11. feedback / assess 结构化内容能从业务表恢复。

### 18.2 前端验证

至少验证：

1. 没有题集时入口状态正确。
2. 有未完成会话时能提示继续。
3. 刷新 `/practice/:sessionId` 能恢复。
4. 输入答案能保存草稿。
5. 答题卡状态颜色随接口变化。
6. 提交前反馈隐藏。
7. 提交后结构化反馈展示。
8. 答题卡折叠和展开可用。
9. 完成后跳转结果页。
10. 移动端答题卡不破坏主作答区。

## 19. 风险与取舍

### 19.1 主要风险

1. 当前 `result NOT NULL` 会阻碍未提交状态，必须改。
2. 如果不做题目快照，历史练习会受后续题目编辑影响。
3. 不保存原始 LLM 调用审计时，只能恢复展示结果，不能严格排查模型输出过程。
4. 整轮反馈模式如果一次性批量调用 `FeedbackAgent`，耗时可能较长。
5. 前端保存草稿需要防抖和竞态处理。
6. 过度拆分组件会增加第一版实现成本。

### 19.2 本次取舍

第一版建议：

1. 先完整实现逐题反馈闭环。
2. 整轮反馈保留数据结构和 UI 规则。
3. 批量判题可后续增强。
4. 前端组件先保持 5 个核心组件。
5. 后端主业务表沿用 `practice_session` 和 `practice_session_item`，不新增替代性的 TestSession 表。
6. 不新增额外审计表，业务表保存最终结构化输出。

## 20. 非目标

本次 V5.5 不做：

1. 完整考试监考模式。
2. 防作弊。
3. 复杂计时策略。
4. 多人排行榜。
5. 错题本独立模块。
6. Memory 正式产品化页面。
7. 整轮批量判题的 SSE / 异步任务化。

这些能力可以在刷题闭环稳定后继续扩展。
