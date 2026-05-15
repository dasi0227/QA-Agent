# V4 反馈 Agent DAG 设计说明

## 一、反馈 Agent 是什么

反馈 Agent 是 QA_Agent 系统的**单题即时反馈链路**。它服务练习会话中的某一道题，接收用户回答后同步返回判定、分数、反馈详情和资料依据。

核心职责：让用户每答完一道题就知道当前回答是否达标、缺了什么、哪里错了，以及如何改进。本版本不做整轮评分、不做长期记忆、不做异步任务。

## 二、用例：用户视角

1. 用户进入某个问答集的练习会话。
2. 用户对一道题提交回答，或点击“不会”。
3. 后端同步执行 FeedbackAgent。
4. 前端拿到本题反馈结果并展示：
   - 有效回答：判定、分数、摘要、缺失点、错误点、改进建议、优化回答。
   - 不会分支：记忆技巧和情绪支持。
   - 原始资料依据：折叠展示 `sourceChunks`。

## 三、整体架构

```
POST /qa-agent/api/v1/practice/session-item/feedback
        │
        ▼
┌─────────────────────────────┐
│     PracticeController       │
│  接收 FeedbackRequest         │
└─────────────┬───────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────┐
│                FeedbackAgent.execute()              │
│                                                     │
│  1. 获取当前 userId                                 │
│  2. 读取用户 LLM 配置并构建 ChatModel                │
│  3. 构建 FeedbackWorkflowContext                    │
│  4. FeedbackAgentFactory.build()                    │
│  5. UntypedAgent.invokeWithAgenticScope()           │
└─────────────┬───────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────┐
│             AgenticServices DAG                      │
│                                                     │
│  PREPARE Java action                                │
│    - 读取 practice_session_item / practice_session  │
│    - 校验 session.user_id                           │
│    - 读取 qa_item / user_profile / sourceChunks      │
│    - 根据 unknown 或空白 userAnswer 写入 isUnknown   │
│                                                     │
│  ROUTE conditionalBuilder                           │
│    - isUnknown=true  -> HINT                        │
│    - isUnknown=false -> JUDGE                       │
│                                                     │
│  HINT Java action                                   │
│    - 调 HintAgent                                   │
│    - 输出 memoryTip / encouragement                 │
│                                                     │
│  JUDGE Java action                                  │
│    - 调 JudgeAgent                                  │
│    - 输出 result / score / feedback detail          │
│    - 后端校验 result + score                         │
│                                                     │
│  SAVE Java action                                   │
│    - 覆盖 practice_session_item 最新反馈             │
│    - 首次作答时 answered_count + 1                  │
│    - 写入 FeedbackResponse 到 scope                  │
└─────────────────────────────────────────────────────┘
```

## 四、接口设计

### 4.1 请求

`POST /practice/session-item/feedback`

```json
{
  "sessionItemId": "string",
  "userAnswer": "string",
  "unknown": false
}
```

规则：

1. `sessionItemId` 必填。
2. `unknown=true` 时允许 `userAnswer` 为空。
3. `unknown=false` 但 `userAnswer` 为空白时，后端仍按 UNKNOWN 分支处理。
4. 不做“不会/不知道”等自然语言短语识别，前端应通过 `unknown` 明确表达。

### 4.2 响应

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
  "answeredAt": "2026-05-16T01:00:00"
}
```

`sourceChunks` 由后端根据 `qa_item.source_chunk_ids_json` 回查 `document_chunk`。它只返回给前端折叠展示，不传给 `JudgeAgent` 或 `HintAgent`。

## 五、结果与分数

### 5.1 result

| result | 含义 |
| --- | --- |
| `CORRECT` | 方向正确，核心点完整或基本完整 |
| `DEFICIENT` | 部分正确，但关键点缺失、表达不足或理解不完整 |
| `WRONG` | 主体错误、答偏或核心概念混乱 |
| `UNKNOWN` | 空答、明确不会或无法形成有效回答 |

### 5.2 score

| result | 可选分数 |
| --- | --- |
| `CORRECT` | 80 / 90 / 100 |
| `DEFICIENT` | 40 / 50 / 60 / 70 |
| `WRONG` | 0 / 10 / 20 / 30 |
| `UNKNOWN` | 0 |

`JudgeAgent` 输出 `result + score`，后端通过 `FeedbackScorePolicy` 二次校验。不合法时修正为默认分：

| result | 默认分 |
| --- | ---: |
| `CORRECT` | 90 |
| `DEFICIENT` | 60 |
| `WRONG` | 20 |
| `UNKNOWN` | 0 |

## 六、Agent 分支

### 6.1 JudgeAgent

触发条件：`unknown=false` 且 `userAnswer` 非空白。

输入：

1. `question`
2. `standardAnswer`
3. `knowledgeNote`
4. `tip`
5. `userAnswer`
6. `answerStyle`
7. `feedbackStyle`
8. `retryHint`

依据优先级：

1. `standardAnswer`：最高判定标准。
2. `tip`：证据边界提示。
3. `knowledgeNote`：辅助识别缺失点和易混淆点。

输出：

```json
{
  "result": "DEFICIENT",
  "score": 60,
  "feedbackSummary": "string",
  "missingPoints": [],
  "wrongPoints": [],
  "improvementAdvice": "string",
  "betterAnswer": "string"
}
```

### 6.2 HintAgent

触发条件：`unknown=true` 或 `userAnswer` 为空白。

后端直接确定：

```text
result = UNKNOWN
score = 0
feedbackSummary = 这题已标记为不会。
```

`HintAgent` 只输出：

```json
{
  "memoryTip": "string",
  "encouragement": "string"
}
```

边界：

1. 不判分。
2. 不复述标准答案。
3. 不输出完整讲解。
4. `encouragement` 偏情绪支持，不承担教学职责。

## 七、数据落库

`practice_session_item` 新增：

```sql
feedback_detail_json JSON NULL
```

有效回答分支：

```json
{
  "type": "JUDGE",
  "judgeDetail": {
    "missingPoints": [],
    "wrongPoints": [],
    "improvementAdvice": "",
    "betterAnswer": ""
  },
  "hintDetail": null
}
```

不会分支：

```json
{
  "type": "HINT",
  "judgeDetail": null,
  "hintDetail": {
    "memoryTip": "",
    "encouragement": ""
  }
}
```

保存规则：

1. 同一个 `practice_session_item` 只保留最新反馈。
2. 每次提交覆盖 `user_answer`、`result`、`score`、`feedback_summary`、`feedback_detail_json`、`answered_at`。
3. 如果本题之前 `answered_at` 为空，`practice_session.answered_count + 1`。
4. 重复提交只覆盖反馈，不重复增加 `answered_count`。
5. V4 不更新 `practice_session.score`、`accuracy`、`summary`。

## 八、代码组织

```
domain/agent/service/feedback/
  IFeedbackAgent.java
  FeedbackAgent.java
  model/
    context/
      FeedbackContext.java
      FeedbackWorkflowContext.java
      JudgeContext.java
      HintContext.java
    enumeration/
      FeedbackPhase.java
    exception/
      FeedbackException.java
    result/
      JudgeResult.java
      HintResult.java
  subagent/
    JudgeAgent.java
    HintAgent.java
  support/
    FeedbackAgentFactory.java
    FeedbackScorePolicy.java
    FeedbackLlmModelProvider.java

application/src/main/resources/prompt/feedback/
  feedback-judge.txt
  feedback-hint.txt
```

说明：

1. `FeedbackAgentFactory` 只组装 DAG，不写 DB。
2. `FeedbackAgent` 负责阶段方法、重试、fallback、落库协调。
3. 复用 `IAgentRepository`，不新建反馈专属 Repository。
4. `JudgeAgent`、`HintAgent` 是 LangChain4J 子 Agent 接口，不写实现类。

## 九、V4 边界

1. 不做前端改造。
2. 不做 SSE、轮询、后台任务或任务消息表。
3. 不保存多次反馈历史。
4. 不做整轮评分和整体总结。
5. 不引入 Memory。
6. 不让 Agent 读取 `sourceChunks.content`；资料正文只回显给前端。
