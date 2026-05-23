# V5.5 练习时长、反馈模式、回看与历史设计

## 1. 目标

本设计补充 `V5.5 QuizPage` 的第二阶段能力，重点解决五个问题：

1. 做题时长必须持久化，刷新、退出、继续后仍能恢复。
2. 顺序练习和随机练习必须稳定复现。
3. 逐题反馈和整轮反馈要有清晰的数据流和交互差异。
4. 结果页题目明细应支持进入只读回看。
5. 问答集详情应支持查看已完成练习历史。

本轮仍以“自我测验 / 刷题复盘”为产品定位，不按严格考试防作弊系统设计。因此不需要为了阻止用户查看 Network 而做响应脱敏，但前端展示规则必须保持清晰。

## 2. 核心产品结论

### 2.1 两种反馈模式

保留两种反馈模式，但语义重新收敛：

| 模式 | 做题中是否调用 FeedbackAgent | 做题中是否可修改答案 | 反馈展示时机 |
| --- | --- | --- | --- |
| `ITEM_BY_ITEM` | 是，提交本题时调用 | 已提交题不可修改 | 提交本题后立即展示 |
| `AFTER_ALL` | 否 | 提交本轮前可反复修改 | 提交本轮后统一展示 |

`AFTER_ALL` 不再采用“提前调用 FeedbackAgent 但前端隐藏”的方案。它的最终答案以服务端草稿为准，提交本轮时后端逐题生成 feedback，再生成整轮 assess。

### 2.2 统一数据权威

服务端是权威数据源：

1. 用户答案保存到 `practice_session_item.user_answer`。
2. 不会标记保存到 `practice_session_item.unknown` 和 `status`。
3. 当前题号保存到 `practice_session.current_index`。
4. 累计用时保存到 `practice_session.duration_seconds`。
5. 结果和反馈保存到 `practice_session_item.result / score / feedback_*`。
6. 整轮评估保存到 `practice_session.assessment_detail_json` 等字段。

前端状态只负责输入体验、防抖保存、保存状态展示和最近 session 兜底提示，不承担最终进度存储。

## 3. 做题时长

### 3.1 数据库

不需要新增表，也不需要新增字段。继续使用：

```text
practice_session.duration_seconds
practice_session.last_active_at
```

`duration_seconds` 表示本轮真实累计活跃作答时间，不使用 `finished_at - started_at` 推算。用户可能中途退出数小时或隔天继续，直接用起止时间相减会严重失真。

### 3.2 接口入参

以下请求建议增加 `durationSeconds`：

```text
ItemSaveRequest.durationSeconds
ItemSubmitRequest.durationSeconds
PracticeSubmitRequest.durationSeconds
PracticeAbandonRequest.durationSeconds
```

适用接口：

```text
POST /practice/item/save
POST /practice/item/unknown
POST /practice/item/answer
POST /practice/session/submit
POST /practice/session/abandon
```

### 3.3 后端保存规则

后端更新时长时使用保守规则：

```text
duration_seconds = max(已有值, 请求值)
last_active_at = now()
```

约束：

1. `FINISHED` 和 `ABANDONED` 会话不再更新用时。
2. 小于当前值的请求值不覆盖。
3. 明显异常的大值可以在 Repository 或 Service 中截断，避免前端计时异常污染数据。

### 3.4 前端计时规则

前端维护本轮活跃计时器：

1. 进入练习页时，以后端 `durationSeconds` 作为基准。
2. 页面可见且 session 未完成时累加秒数。
3. 保存答案、切题、退出、提交本轮前 flush 当前秒数。
4. 结果页展示后端 `durationSeconds`。

前端不再用 `finishedAt - startedAt` 展示总耗时。

## 4. 顺序练习与随机练习

### 4.1 结论

随机练习必须由后端生成顺序，前端不做 shuffle。

原因：

1. `practice_session_item.sort_order` 是一轮练习的真实顺序。
2. 刷新恢复、历史回看、结果明细都依赖固定顺序。
3. 前端乱序会导致同一 session 在不同进入时顺序不一致。

### 4.2 请求字段

`PracticeInitRequest.mode` 继续作为请求字段：

```text
SEQUENTIAL
RANDOM
```

`PracticeRestartRequest.mode` 同样保留，用于重新开始时生成新顺序。

### 4.3 后端生成规则

创建 session 时确定题序：

1. `SEQUENTIAL`：按 `qa_item.sort_order ASC, qa_item.created_at ASC`。
2. `RANDOM`：后端读取候选题后洗牌，再写入 `practice_session_item.sort_order`。
3. session 创建后，题序不可变。

前端始终使用 `GET /practice/session/detail` 返回的 items 顺序渲染答题卡、结果页和回看页。

## 5. 逐题反馈模式

### 5.1 做题流程

```text
用户输入答案
  -> /practice/item/save 防抖保存草稿

用户提交本题
  -> /practice/item/answer
  -> 后端调用 FeedbackAgent
  -> FeedbackSaver 写入 result / score / feedback
  -> 返回最新 item
  -> 前端展示本题反馈
```

提交成功后：

1. `practice_session_item.status = SUBMITTED`
2. 当前题答案锁定，不再编辑。
3. 答题卡展示正确、缺漏、错误、不会等结果色。
4. 用户手动点击下一题继续。

### 5.2 不会流程

逐题反馈模式下点击“不会”：

```text
/practice/item/unknown
  -> 后端调用 FeedbackAgent 的 unknown 分支
  -> 写入 UNKNOWN 结果和 hintDetail
  -> status = SUBMITTED
```

前端展示不会提示，不再允许继续编辑该题。

### 5.3 提交本轮

逐题反馈模式下，`/practice/session/submit` 不需要再逐题调用 FeedbackAgent，只需要：

1. 校验所有题已经 `SUBMITTED` 或不会。
2. 调用 AssessAgent。
3. 保存整轮评估。
4. 将 session 标记为 `FINISHED`。

## 6. 整轮反馈模式

### 6.1 做题流程

整轮反馈模式下，做题阶段只保存答案，不生成反馈。

```text
用户输入答案
  -> /practice/item/save
  -> 后端写 practice_session_item.user_answer
  -> status = DRAFT

用户切题 / 退出 / 提交本轮前
  -> flush 当前答案
  -> 后端继续写 user_answer / current_index / duration_seconds
```

这不是前端临时草稿。刷新、退出、换设备继续时，都以服务端 `detail` 返回的 `userAnswer` 为准。

### 6.2 不会流程

整轮反馈模式下点击“不会”：

```text
/practice/item/unknown
  -> 只保存 unknown=true
  -> status = UNKNOWN
  -> 可保留 user_answer
```

不触发 FeedbackAgent。提交本轮前，用户可以回到该题取消不会或修改答案。取消不会可通过再次保存有效答案实现，或者后续显式提供“取消不会”交互。

### 6.3 页面按钮

整轮反馈模式下不建议显示“提交本题”，避免用户误解为已经判题或锁题。

推荐按钮：

```text
上一题
下一题
不会
保存并下一题
提交本轮
退出
```

其中“保存并下一题”只是显式保存草稿并跳题，不调用 FeedbackAgent。

### 6.4 提交本轮

整轮反馈模式下，`/practice/session/submit` 是关键编排接口：

```text
1. 校验 session 属于当前用户
2. 校验 session.status = IN_PROGRESS
3. flush 当前答案后，校验每题有 userAnswer 或 unknown=true
4. 遍历 session items 调用 FeedbackAgent
5. FeedbackSaver 写入每题 result / score / feedback
6. 调用 AssessAgent
7. AssessSaver 写入整轮 score / accuracy / counts / assessDetail
8. session.status = FINISHED
9. 返回 PracticeDetail
```

第一版建议同步执行，保持实现简单。后续如果题量较大或 Agent 耗时明显，再引入 `ASSESSING` 异步状态和轮询。

## 7. 正在生成评估状态

前端需要一个轻量等待状态，用于：

1. `AFTER_ALL` 点击提交本轮后，等待逐题 feedback 和整轮 assess。
2. `ITEM_BY_ITEM` 点击提交本轮后，等待整轮 assess。

### 7.1 视觉风格

等待状态必须符合当前项目整体样式：

1. 背景继续使用暖纸色。
2. 面板使用浅纸白或半透明白，不使用黑色大卡片。
3. 重点色使用棕金和蓝灰。
4. 动效克制，只表达“正在处理”，不伪造真实百分比。

推荐文案：

```text
正在生成评估
系统正在整理本轮作答、单题反馈和整轮分析。
```

阶段提示：

```text
整理作答
生成单题反馈
汇总整轮分析
```

这些阶段第一版仅作为视觉提示，不代表真实后端进度。

### 7.2 失败处理

提交失败时：

1. 保留当前练习页状态。
2. 显示“生成失败，重试提交”。
3. 不清空用户答案。
4. 用户可再次点击提交本轮。

## 8. 回看页

### 8.1 路由

新增只读回看页：

```text
/practice/:sessionId/review?index=0
```

它从 ResultPage 的题目明细进入。

### 8.2 页面定位

ReviewPage 是“已完成会话的单题复盘页”，不是做题页。

复用 PracticePage 的沉浸式双栏结构，但所有行为只读。

### 8.3 桌面布局

顶部状态栏：

1. 返回结果页。
2. 题集名。
3. 完成时间。
4. 用时。
5. 反馈模式。

左侧主区：

1. 当前题号。
2. 题干。
3. 标签、难度。
4. 我的答案。
5. 标准答案。
6. 本题反馈摘要。
7. 缺失点、错误点、改进建议。
8. 不会提示。
9. 来源证据，默认折叠。

右侧答题卡：

1. 圆形题号网格。
2. 使用结果色展示正确、缺漏、错误、不会。
3. 点击题号切换当前回看题。
4. 支持折叠。

底部操作：

1. 上一题。
2. 下一题。
3. 返回结果页。

### 8.4 不展示内容

ReviewPage 不展示：

1. 答案输入框。
2. 保存状态。
3. 提交本题。
4. 不会按钮。
5. 提交本轮。
6. 放弃练习。

### 8.5 移动端

移动端继续单列展示：

1. 回看内容优先。
2. 答题卡改为底部抽屉或顶部轻量入口。
3. 题目正文、答案、反馈不能被答题卡挤压。

## 9. 做题历史

### 9.1 范围

做题历史只展示已完成会话：

```text
practice_session.status = FINISHED
```

不展示：

1. `IN_PROGRESS`，继续练习入口单独处理。
2. `ABANDONED`，第一版不暴露给用户。

### 9.2 入口

在问答集详情页增加“查看练习历史”。

按钮强度应低于“开始练习”：

1. 使用浅色按钮。
2. 不使用危险色。
3. 放在题集操作区或统计区附近。

### 9.3 路由

推荐新增页面：

```text
/qa-sets/:qaSetId/practice-history
```

不建议第一版使用弹窗承载历史。历史列表可能增长，页面更适合筛选、滚动和移动端展示。

### 9.4 历史列表字段

历史项展示：

1. 完成时间。
2. 用时。
3. 练习模式：顺序 / 随机。
4. 反馈模式：逐题 / 整轮。
5. 总题数。
6. 正确、缺漏、错误、不会数量。
7. 平均分。
8. 达标率。
9. 查看结果按钮。

点击历史项进入：

```text
/practice/:sessionId/result
```

## 10. 接口设计

### 10.1 复用接口

继续复用现有领域化接口：

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

说明：当前代码中单题接口路径是 `/practice/item/*`，文档实现时应避免继续写旧的 `/practice/qaSetEntry/*`。

### 10.2 新增接口

新增练习历史接口：

```text
GET /practice/session/history?qaSetId=...
```

返回当前用户、当前题集、已完成练习摘要列表。

响应字段建议直接复用 `PracticeSessionResponse`，但 Repository 层固定过滤：

```text
user_id = currentUserId
qa_set_id = qaSetId
status = FINISHED
ORDER BY finished_at DESC, created_at DESC
```

Controller 不接受任意 status 条件，避免前端拼出不符合历史语义的查询。

### 10.3 请求 DTO 调整

以下 DTO 增加 `durationSeconds`：

```text
ItemSaveRequest
ItemSubmitRequest
PracticeSubmitRequest
PracticeAbandonRequest
```

`PracticeInitRequest.mode` 继续表达顺序 / 随机：

```text
SEQUENTIAL
RANDOM
```

`PracticeInitRequest.feedbackMode` 继续表达反馈模式：

```text
ITEM_BY_ITEM
AFTER_ALL
```

## 11. 后端结构

后端应继续保持当前 DDD 分层：

1. `PracticeController` 只做接口适配。
2. `IPracticeFlowService / PracticeFlowService` 负责练习流程编排。
3. `IPracticeRepository / PracticeRepository` 负责会话、题目、历史查询和状态写入。
4. `FeedbackAgent / FeedbackSaver` 继续作为单题反馈生成和保存入口。
5. `AssessAgent / AssessSaver` 继续作为整轮评估生成和保存入口。

### 11.1 PracticeFlowService 调整

`submit` 需要根据 `feedbackMode` 分流：

```text
ITEM_BY_ITEM:
  校验所有题已提交
  执行 AssessAgent

AFTER_ALL:
  校验所有题有答案或 unknown
  对未生成 feedback 的题逐题执行 FeedbackAgent
  执行 AssessAgent
```

日志风格保持：

```text
【练习流程】提交本轮: sessionId={}, feedbackMode={}
【练习流程】生成整轮反馈: sessionId={}, total={}
```

异常继续使用 `ApiException(ResultCode.X)`，不新增散乱异常体系。

### 11.2 Repository 调整

Repository 需要提供：

1. 更新 duration 和 lastActiveAt 的能力。
2. 查询 session state 的能力，包含 feedbackMode、status。
3. 查询本轮 items 用于 AFTER_ALL 批量 feedback。
4. 查询历史 FINISHED session 列表。
5. `RANDOM` 初始化时写固定 `sort_order`。

不建议 Repository 调用 Agent。Agent 编排仍在 Flow Service。

## 12. 前端结构

### 12.1 PracticePage

PracticePage 根据 `session.feedbackMode` 分流：

`ITEM_BY_ITEM`：

1. 展示“提交本题”。
2. 点击后等待返回。
3. 成功后展示 `QuestionFeedbackPanel`。
4. 已提交题只读。

`AFTER_ALL`：

1. 不展示“提交本题”。
2. 展示“保存并下一题”。
3. 答案防抖保存到后端。
4. 已保存题仍可回头修改。
5. 提交本轮时显示生成评估等待态。

### 12.2 ResultPage

ResultPage 继续作为整轮报告页：

1. 展示统计看板。
2. 展示 assess 分析。
3. 展示题目结果明细。
4. 题目明细每一项可点击，进入 ReviewPage。

题目明细不再只是静态行。

### 12.3 ReviewPage

新增 `ReviewPage`：

1. 使用 `usePracticeDetailQuery(sessionId)`。
2. 只允许查看 `FINISHED` session。
3. 通过 query 参数 `index` 控制当前题。
4. 复用答题卡视觉，但行为只读。
5. 支持返回 ResultPage。

### 12.4 PracticeHistoryPage

新增 `PracticeHistoryPage`：

1. 读取 `qaSetId`。
2. 调用 `usePracticeHistoryQuery(qaSetId)`。
3. 展示已完成历史列表。
4. 点击历史进入 ResultPage。

### 12.5 API hooks

新增：

```text
usePracticeHistoryQuery
```

调整：

```text
SaveAnswerInput.durationSeconds
SubmitItemInput.durationSeconds
SubmitSessionInput.durationSeconds
AbandonPracticeInput.durationSeconds
```

## 13. 视觉规范

本轮新增页面必须符合当前前端整体配色：

1. 背景：暖纸色、浅米白。
2. 正文：墨色文字。
3. 辅助线：低透明棕金或墨色。
4. 主操作：棕金主题按钮或现有 primary 样式。
5. 答题卡：沿用暖炭灰方案，不使用纯黑大面板。
6. 状态色：
   - 正确：低饱和绿。
   - 缺漏：暖黄或弱橙。
   - 错误：低饱和红。
   - 不会：黄色。
   - 当前题：蓝灰环。
   - 未做：暖灰。

等待态不做大面积黑色遮罩，也不做营销式 hero。它应该像一张正在生成的报告，而不是加载广告页。

## 14. 状态恢复

恢复逻辑继续以服务端为准：

1. 进入练习页调用 `detail`。
2. 使用 `session.currentIndex` 恢复题号。
3. 使用 `item.userAnswer` 恢复输入框。
4. 使用 `item.unknown` 恢复不会状态。
5. 使用 `session.durationSeconds` 恢复用时。

localStorage 只保存最近 session 快照，用于入口提示，不作为作答数据源。

## 15. 实施顺序建议

1. 后端补 `durationSeconds` 请求字段和保存逻辑。
2. 后端确认 `RANDOM` 初始化时固定 `practice_session_item.sort_order`。
3. 后端改 `submit`，支持 `AFTER_ALL` 下统一逐题 feedback 再 assess。
4. 后端新增 `GET /practice/session/history`。
5. 前端 API types/hooks 补时长字段和 history query。
6. PracticePage 按 `feedbackMode` 调整按钮、保存和提交行为。
7. 增加生成评估等待态。
8. ResultPage 题目明细支持跳转 ReviewPage。
9. 新增 ReviewPage。
10. 新增 PracticeHistoryPage 和问答集详情入口。
11. 更新 `docs/V5.5-QuizPage.md`、`docs/API.md`、`docs/TABLE.md`。

## 16. 验证重点

后端：

1. `AFTER_ALL` 做题阶段只保存草稿，不调用 FeedbackAgent。
2. `AFTER_ALL` 提交本轮后每题都有 result / score / feedback。
3. `ITEM_BY_ITEM` 已提交题不会在整轮提交时重复生成 feedback。
4. `duration_seconds` 刷新、退出、继续后保持正确。
5. `RANDOM` 会话刷新后题序不变。
6. 历史接口只返回当前用户当前题集的 `FINISHED` session。

前端：

1. `AFTER_ALL` 可以回头修改答案。
2. `AFTER_ALL` 刷新后答案从后端恢复。
3. `ITEM_BY_ITEM` 提交本题后展示结构化反馈并锁定。
4. 提交本轮时出现等待态，成功后进入结果页。
5. ResultPage 题目明细可进入 ReviewPage。
6. ReviewPage 只读、可通过答题卡切题、可返回结果页。
7. 问答集详情能进入练习历史，只展示已完成记录。

