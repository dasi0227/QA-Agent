# V5 Assess 设计说明

本文以当前代码实现为准，核心文件包括：

- `PracticeController`
- `AssessAgent`
- `AssessAgentFactory`
- `AssessStatCalculator`
- `AssessResultCleaner`
- `AssessSaver`
- `AgentRepository.getAssessContext()` / `saveAssessResult()`

## 1. 当前目标

AssessAgent 负责一轮练习完成后的同步整轮评估。它基于已经落库的单题反馈结果生成：

1. 总分
2. 达标率
3. `correct / deficient / wrong / unknown` 分布
4. 用户可读整轮诊断与复习建议
5. 内部记忆线索

当前链路不做：

1. 重新判题
2. SSE / 后台任务
3. RAG / Web 搜索
4. source chunk 再利用

## 2. 对外接口

旧 Assess 直连接口已删除。当前对外入口是 `POST /practice/session/submit`，由 Practice Flow 编排调用 `AssessAgent` 并保存整轮评估。

请求：

```json
{
  "sessionId": "practice-session-id"
}
```

响应：

```json
{
  "sessionId": "practice-session-id",
  "qaSetId": "qa-set-id",
  "score": 75,
  "accuracy": 80.00,
  "perfectCount": 1,
  "correctCount": 2,
  "deficientCount": 5,
  "wrongCount": 1,
  "unknownCount": 1,
  "summary": "string",
  "assessDetail": {
    "overallComment": "string",
    "reviewGuidance": "string",
    "strengths": [],
    "weaknesses": []
  },
  "finishedAt": "2026-05-18T12:00:00"
}
```

说明：`memory_clue_json` 只落库，不返回前端。

## 3. 主流程

```text
AssessAgent.execute()
  -> 取当前 userId
  -> UserLlmModelProvider.getUserLlmModel()
  -> AgentRepository.getAssessContext(sessionId, userId)
  -> AssessStatCalculator.calculate()
  -> 预构建 Diagnose / Advise / Record Context
  -> AssessAgentFactory.build()
  -> invokeWithAgenticScope()
  -> AssessSaver.save()
```

## 4. 预校验与统计

`AssessStatCalculator.validate()` 会在 LLM 调用前校验：

1. `SessionContext` 不为空
2. `items` 不为空
3. `items.size == totalQuestions`
4. 每道题都有 `answeredAt`
5. 每道题都有 `result`
6. 每道题都有 `score`

不满足时抛：

- `AssessException(ResultCode.PRACTICE_SESSION_NOT_COMPLETED, ...)`

由 `GlobalExceptionHandler` 转成：

- `40906 PRACTICE_SESSION_NOT_COMPLETED`

### 4.1 统计规则

| 字段 | 规则 |
| --- | --- |
| `score` | 所有单题 `score` 平均值，四舍五入为整数 |
| `accuracy` | `(PERFECT + CORRECT + DEFICIENT) / totalQuestions * 100`，保留 2 位 |
| `perfectCount` | `PERFECT` 数量 |
| `correctCount` | `CORRECT` 数量 |
| `deficientCount` | `DEFICIENT` 数量 |
| `wrongCount` | `WRONG` 数量 |
| `unknownCount` | `UNKNOWN` 数量 |

## 5. DB 快照输入

`AgentRepository.getAssessContext()` 会读取：

1. `practice_session`
2. `qa_set`
3. 当前 session 下全部 `practice_session_item`
4. 每道 qaSetEntry 对应的 `qa_item`
5. `feedback_judge_detail` 反序列化出的 `JudgeDetail`

组装 `SessionContext`：

| 字段 | 说明 |
| --- | --- |
| `sessionId` | 练习会话 ID |
| `qaSetId` | 题集 ID |
| `qaSetTitle` | 题集标题 |
| `totalQuestions` | 题数 |
| `items` | `AssessItemDetail[]` |
| `stats` | 由 Java 计算后回填 |

`AssessItemDetail` 关键字段：

1. `question`
2. `moduleTag`
3. `difficulty`
4. `standardAnswer`
5. `userAnswer`
6. `result`
7. `score`
8. `feedbackSummary`
9. `missingPoints`
10. `wrongPoints`
11. `answeredAt`

## 6. DAG 结构

```text
parallel
  -> REVIEW sequence
       -> DIAGNOSE
       -> ADVISE
  -> RECORD
```

说明：

1. `ReviewAgent` 负责用户可读评估
2. `RecordAgent` 并行提取内部记忆线索
3. scope 中只保存阶段输出，不存整份大上下文

scope key：

| key | 类型 |
| --- | --- |
| `diagnoseResult` | `DiagnoseResult` |
| `adviseResult` | `AdviseResult` |
| `recordResult` | `RecordResult` |

## 7. 三个 SubAgent

### 7.1 DiagnoseAgent

输入：

1. `qaSetTitle`
2. `statsJson`
3. `itemsJson`
4. `retryHint`

输出：

```json
{
  "strengths": [
    {
      "title": "string",
      "analysis": "string"
    }
  ],
  "weaknesses": [
    {
      "title": "string",
      "analysis": "string"
    }
  ]
}
```

### 7.2 AdviseAgent

输入：

1. `qaSetTitle`
2. `statsJson`
3. `diagnosis`
4. `itemBriefsJson`
5. `retryHint`

输出：

```json
{
  "overallComment": "string",
  "reviewGuidance": "string"
}
```

### 7.3 RecordAgent

输入：

1. `qaSetTitle`
2. `statsJson`
3. `itemsJson`
4. `retryHint`

输出根节点直接是数组，Java 再包成 `RecordResult.clues`：

```json
[
  {
    "type": "CONCEPT_WEAKNESS",
    "observation": "string",
    "importance": "HIGH"
  }
]
```

允许值：

`type`

1. `CONCEPT_WEAKNESS`
2. `EXPRESSION_WEAKNESS`
3. `MISTAKE_PATTERN`
4. `UNKNOWN_PATTERN`
5. `STABLE_STRENGTH`

`importance`

1. `HIGH`
2. `MEDIUM`
3. `LOW`

## 8. 清洗与 fallback

`AssessResultCleaner` 当前负责：

1. `DiagnoseResult` 条目裁剪和去空
2. `AdviseResult` 去空和 trim
3. `RecordResult` 的 type / importance 归一

当前上限：

- `strengths` / `weaknesses` / `clues` 最多保留 3 条

fallback：

1. `DiagnoseAgent` 失败 -> `strengths = []`, `weaknesses = []`
2. `AdviseAgent` 失败 -> Java 生成基础 `overallComment` / `reviewGuidance`
3. `RecordAgent` 失败 -> `clues = []`

## 9. 保存逻辑

`AssessSaver.save()` 会：

1. 从 scope 读取 `DiagnoseResult`
2. 从 scope 读取 `AdviseResult`
3. 从 scope 读取 `RecordResult`
4. 组装 `AssessDetail`
5. 组装 `AssessSaveCommand`
6. 调用 `agentRepository.saveAssessResult(...)`

### 9.1 practice_session 更新

`saveAssessResult()` 会写：

1. `status = FINISHED`
2. `score`
3. `accuracy`
4. `correct_count`
5. `deficient_count`
6. `wrong_count`
7. `unknown_count`
8. `summary = assessDetail.overallComment`
9. `assessment_detail_json`
10. `memory_clue_json`
11. `finished_at = 首次完成时间`
12. `updated_at`

### 9.2 qa_set 聚合刷新

评估成功后会重算：

1. `practice_count`
2. `average_score`
3. `best_score`
4. `average_accuracy`
5. `best_accuracy`
6. `last_practiced_at`

## 10. 返回模型

### 10.1 `AssessDetail`

```json
{
  "overallComment": "string",
  "reviewGuidance": "string",
  "strengths": [
    {
      "title": "string",
      "analysis": "string"
    }
  ],
  "weaknesses": [
    {
      "title": "string",
      "analysis": "string"
    }
  ]
}
```

### 10.2 `memory_clue_json`

根节点是数组，不包额外对象：

```json
[
  {
    "type": "STABLE_STRENGTH",
    "observation": "string",
    "importance": "LOW"
  }
]
```

## 11. Prompt 文件

```text
prompt/assess/assess-diagnose.txt
prompt/assess/assess-advise.txt
prompt/assess/assess-record.txt
```

当前 prompt 与代码对齐点：

1. diagnose / record 最多 3 条
2. advise 只输出 `overallComment` / `reviewGuidance`
3. record 顶层必须是 JSON 数组

## 12. 当前代码口径

1. 代码组织已经是 `SessionContext` / `AssessContext` / `AssessStatCalculator` / `AssessResultCleaner` / `AssessSaver`，不要再使用旧版 `AssessSessionContext`、`AssessmentMetricCalculator`、`AssessResultSanitizer` 名称。
2. `memory_clue_json` 不返回前端，但会持久化供后续 V6 Memory 使用。
3. 评估是同步接口，失败直接走全局异常，不存在任务表或阶段消息留档。
