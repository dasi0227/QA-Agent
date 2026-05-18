# V4 Feedback Agent 设计说明

本文以当前代码实现为准，核心文件包括：

- `PracticeController`
- `FeedbackAgent`
- `FeedbackAgentFactory`
- `FeedbackSaver`
- `FeedbackScoreCorrector`
- `AgentRepository.getPracticeVO()` / `saveFeedbackResult()`

## 1. 当前目标

FeedbackAgent 负责同步返回单题反馈。输入是一道练习题和用户作答，输出是：

1. 判定结果
2. 分数
3. 摘要反馈
4. 结构化改进建议或不会提示
5. 资料切片引用

当前链路不做：

1. 整轮评估
2. SSE
3. 异步任务
4. RAG 二次检索

## 2. 对外接口

接口：`POST /practice/session-item/feedback`

请求：

```json
{
  "sessionItemId": "string",
  "userAnswer": "string",
  "unknown": false
}
```

规则：

1. `sessionItemId` 必填
2. `unknown = true` 时允许 `userAnswer` 为空
3. `unknown = false` 但 `userAnswer` 为空白时，后端仍按 unknown 分支处理

响应 `FeedbackResponse`：

```json
{
  "sessionItemId": "string",
  "qaItemId": "string",
  "result": "CORRECT",
  "score": 90,
  "feedbackSummary": "string",
  "judgeDetail": {
    "missingPoints": [],
    "wrongPoints": [],
    "improvementAdvice": "string",
    "betterAnswer": "string"
  },
  "hintDetail": null,
  "sourceChunks": [
    {
      "chunkId": "string",
      "documentId": "string",
      "titlePath": "string",
      "summary": "string",
      "content": "string"
    }
  ],
  "answeredAt": "2026-05-18T12:00:00"
}
```

## 3. 主流程

```text
FeedbackAgent.execute()
  -> 取当前 userId
  -> UserLlmModelProvider.getUserLlmModel()
  -> AgentRepository.getPracticeVO(sessionItemId, userId)
  -> 规范化 userAnswer
  -> 根据 unknown / 空白回答路由
  -> 构建 HintContext / JudgeContext
  -> FeedbackAgentFactory.build()
  -> invokeWithAgenticScope(routeFlag)
  -> FeedbackSaver.save()
```

## 4. DB 快照输入

`AgentRepository.getPracticeVO()` 会读取：

1. `practice_session_item`
2. `practice_session`
3. `qa_item`
4. `user_profile.answer_style`
5. `user_profile.feedback_style`
6. `qa_item.source_chunk_ids_json` 对应的 `document_chunk`

组装结果 `PracticeVO`：

| 字段 | 说明 |
| --- | --- |
| `sessionItemId` | 当前练习题 ID |
| `sessionId` | 练习会话 ID |
| `qaItemId` | 原题 ID |
| `question` | 题目 |
| `standardAnswer` | 标准答案 |
| `knowledgeNote` | 复习笔记 |
| `tip` | 证据边界提示 |
| `answerStyle` | 用户答案风格 |
| `feedbackStyle` | 用户反馈风格 |
| `sourceChunks` | 资料切片列表 |

## 5. DAG 结构

```text
conditional
  -> routeFlag = true  : HINT
  -> routeFlag = false : JUDGE
```

当前 `FeedbackContext` 只承担：

1. 用户模型
2. `hintStep`
3. `judgeStep`
4. route flag key

scope 中只保存一个阶段输出：

| key | 类型 |
| --- | --- |
| `hintResult` | `HintResult` |
| `judgeResult` | `JudgeResult` |

## 6. HINT 分支

SubAgent：`HintAgent`

输入：

1. `question`
2. `standardAnswer`
3. `knowledgeNote`
4. `tip`
5. `answerStyle`
6. `feedbackStyle`
7. `retryHint`

输出模型：

```json
{
  "memoryTip": "string",
  "encouragement": "string"
}
```

后端固定补充：

| 字段 | 值 |
| --- | --- |
| `result` | `UNKNOWN` |
| `score` | `0` |
| `feedbackSummary` | `这题已标记为不会。` |

## 7. JUDGE 分支

SubAgent：`JudgeAgent`

输入：

1. `question`
2. `standardAnswer`
3. `knowledgeNote`
4. `tip`
5. `userAnswer`
6. `answerStyle`
7. `feedbackStyle`
8. `retryHint`

输出模型 `JudgeResult`：

```json
{
  "result": "PERFECT|CORRECT|DEFICIENT|WRONG",
  "score": 80,
  "feedbackSummary": "string",
  "missingPoints": ["string"],
  "wrongPoints": ["string"],
  "improvementAdvice": "string",
  "betterAnswer": "string"
}
```

注意：

1. prompt 明确禁止输出 `UNKNOWN`
2. `FeedbackResult.fromValue()` 会把空值、非法值和 `UNKNOWN` 兜底成 `DEFICIENT`

## 8. 结果与分数校准

`FeedbackScoreCorrector` 当前规则：

| result | 允许分数 |
| --- | --- |
| `PERFECT` | `100` |
| `CORRECT` | `80`, `90` |
| `DEFICIENT` | `50`, `60`, `70` |
| `WRONG` | `0`, `10`, `20`, `30`, `40` |
| `UNKNOWN` | `0` |

默认修正分：

| result | 默认分 |
| --- | --- |
| `PERFECT` | `100` |
| `CORRECT` | `90` |
| `DEFICIENT` | `60` |
| `WRONG` | `20` |
| `UNKNOWN` | `0` |

## 9. 保存逻辑

`FeedbackSaver.save()` 的行为：

1. 从 scope 读取 `HintResult` 或 `JudgeResult`
2. 组装 `FeedbackSaveCommand`
3. 调 `agentRepository.saveFeedbackResult(...)`
4. 把 `PracticeVO.sourceChunks` 转成 `FeedbackResponse.sourceChunks`

### 9.1 数据库存储

`practice_session_item` 当前使用两个字段存结构化详情：

| 字段 | 内容 |
| --- | --- |
| `feedback_judge_detail` | `JudgeDetail` JSON |
| `feedback_hint_detail` | `HintDetail` JSON |

当前已经不再使用旧版 `feedback_detail_json`。

### 9.2 覆盖规则

每次反馈都会覆盖：

1. `user_answer`
2. `result`
3. `score`
4. `feedback_summary`
5. `feedback_judge_detail`
6. `feedback_hint_detail`
7. `answered_at`
8. `updated_at`

如果本题此前 `answered_at` 为空，则：

- `practice_session.answered_count + 1`

重复提交只覆盖，不重复累加。

## 10. 返回对象

### 10.1 `judgeDetail`

```json
{
  "missingPoints": [],
  "wrongPoints": [],
  "improvementAdvice": "string",
  "betterAnswer": "string"
}
```

### 10.2 `hintDetail`

```json
{
  "memoryTip": "string",
  "encouragement": "string"
}
```

## 11. Prompt 文件

```text
prompt/feedback/feedback-judge.txt
prompt/feedback/feedback-hint.txt
```

当前 prompt 与代码的关键对齐点：

1. `feedback-judge` 只输出 `PERFECT|CORRECT|DEFICIENT|WRONG`
2. `feedback-hint` 只输出 `memoryTip` / `encouragement`
3. JSON-only 约束由 subagent `userMessage` 再补一次

## 12. 当前代码口径

1. 当前 `FeedbackPhase.FEEDBACK` 只用于顶层命名，不存 scope 数据。
2. `FeedbackException` 仍然保留，用于保存失败时抛出链路异常。
3. `sourceChunks` 只返回给前端展示，不会传给 `JudgeAgent` / `HintAgent`。
