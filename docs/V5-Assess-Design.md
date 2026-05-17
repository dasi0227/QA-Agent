# V5 整轮评估 AssessAgent 设计说明

## 一、AssessAgent 是什么

AssessAgent 是 QA_Agent 系统的整轮练习评估链路。它只在一轮练习全部题目完成后执行，基于 V4 已经落库的单题结果和反馈摘要，生成本轮总分、达标率、结果分布、整体点评、复习指导、优势/薄弱分析，以及供 V6 Memory 使用的内部记忆线索。

边界：

1. 不重新判题，不修改单题 `result` 和 `score`。
2. 不做部分评估。
3. 不使用 SSE、轮询、任务表。
4. 不使用 Tool、RAG、Web、sourceChunks。
5. 不修改前端。

## 二、接口

`POST /practice/session/assess`

请求：

```json
{
  "sessionId": "88888888-8888-8888-8888-888888888881"
}
```

响应：

```json
{
  "sessionId": "88888888-8888-8888-8888-888888888881",
  "qaSetId": "55555555-5555-5555-5555-555555555541",
  "score": 75,
  "accuracy": 80.00,
  "correctCount": 3,
  "deficientCount": 5,
  "wrongCount": 1,
  "unknownCount": 1,
  "summary": "本轮整体完成度中等，能覆盖部分核心方向，但在代理边界和持久化机制对比上还不稳定。",
  "assessDetail": {
    "overallComment": "",
    "reviewGuidance": "",
    "strengths": [],
    "weaknesses": []
  },
  "finishedAt": "2026-05-17T10:00:00"
}
```

`memory_clue_json` 只落库，不返回前端。

## 三、数据结构

`practice_session` 新增字段：

| 字段 | 含义 |
| --- | --- |
| `correct_count` | 本轮 `PERFECT + CORRECT` 题数 |
| `deficient_count` | 本轮 `DEFICIENT` 题数 |
| `wrong_count` | 本轮 `WRONG` 题数 |
| `unknown_count` | 本轮 `UNKNOWN` 题数 |
| `assessment_detail_json` | 用户可读整轮评估详情 |
| `memory_clue_json` | V6 Memory 使用的内部记忆线索 |

`assessment_detail_json`：

```json
{
  "overallComment": "",
  "reviewGuidance": "",
  "strengths": [
    {
      "title": "",
      "analysis": ""
    }
  ],
  "weaknesses": [
    {
      "title": "",
      "analysis": ""
    }
  ]
}
```

`memory_clue_json` 根节点直接是数组：

```json
[
  {
    "type": "CONCEPT_WEAKNESS",
    "observation": "",
    "importance": "HIGH"
  }
]
```

允许的 `type`：

1. `CONCEPT_WEAKNESS`
2. `EXPRESSION_WEAKNESS`
3. `MISTAKE_PATTERN`
4. `UNKNOWN_PATTERN`
5. `STABLE_STRENGTH`

允许的 `importance`：

1. `HIGH`
2. `MEDIUM`
3. `LOW`

## 四、DAG

```text
Java prepare step
  -> parallel(
       user assessment sequence:
         DiagnoseAgent -> AdviseAgent,
       RecordAgent
     )
  -> Java save step
```

说明：

1. prepare 和 save 是 Java step，不进入 DAG scope；DAG scope 只保存阶段输出。
2. `DiagnoseAgent` 输出 `strengths / weaknesses`。
3. `AdviseAgent` 基于诊断结果和单题简要摘要输出 `overallComment / reviewGuidance`。
4. `RecordAgent` 并发输出 `memory_clue_json`。
5. `AssessAgentFactory` 只负责组装 DAG；业务阶段逻辑留在 `AssessAgent`。

## 五、输入上下文

`metrics` 由 Java 计算：

```json
{
  "totalQuestions": 10,
  "score": 75,
  "accuracy": 80.00,
  "correctCount": 3,
  "deficientCount": 5,
  "wrongCount": 1,
  "unknownCount": 1
}
```

`items` 给 `DiagnoseAgent` 和 `RecordAgent`：

```json
{
  "question": "",
  "moduleTag": "",
  "difficulty": "",
  "standardAnswer": "",
  "userAnswer": "",
  "result": "",
  "score": 0,
  "feedbackSummary": "",
  "missingPoints": [],
  "wrongPoints": []
}
```

`itemBriefs` 给 `AdviseAgent`：

```json
{
  "question": "",
  "standardAnswer": "",
  "userAnswer": "",
  "result": "",
  "score": 0,
  "feedbackSummary": ""
}
```

不传给 LLM：

1. `sessionId`
2. `answerStyle`
3. `feedbackStyle`
4. `selectedModule`
5. Profile
6. sourceChunks

## 六、计算规则

Java 负责稳定指标：

| 指标 | 规则 |
| --- | --- |
| `score` | 所有单题 `score` 平均值，四舍五入为整数 |
| `accuracy` | `(PERFECT + CORRECT + DEFICIENT) / totalQuestions * 100`，保留 2 位小数 |
| `correctCount` | `PERFECT + CORRECT` 数量 |
| `deficientCount` | `DEFICIENT` 数量 |
| `wrongCount` | `WRONG` 数量 |
| `unknownCount` | `UNKNOWN` 数量 |

LLM 不参与打分，不允许推翻单题结果。

## 七、完成校验

执行 LLM 前校验：

1. `sessionId` 非空。
2. `practice_session` 存在且属于当前用户。
3. 当前 session 下 item 数大于 0。
4. item 数等于 `practice_session.total_questions`。
5. 所有 item 都有 `answered_at`。
6. 所有 item 都有 `result` 和 `score`。
7. 用户 LLM 配置完整。

未完成或数据不完整时返回 `40906 PRACTICE_SESSION_NOT_COMPLETED`。

## 八、保存规则

成功后更新当前 `practice_session`：

```text
status = FINISHED
score = Java 计算结果
accuracy = Java 计算结果
correct_count / deficient_count / wrong_count / unknown_count
summary = assessDetail.overallComment
assessment_detail_json = assessDetail
memory_clue_json = RecordAgent 输出数组
finished_at = COALESCE(finished_at, now)
updated_at = now
```

每次评估成功后重算 `qa_set` 聚合：

```text
practice_count = count(FINISHED sessions)
average_score = avg(score)
best_score = max(score)
average_accuracy = avg(accuracy)
best_accuracy = max(accuracy)
last_practiced_at = max(finished_at)
```

重复调用允许覆盖 `assessment_detail_json` 和 `memory_clue_json`，但不刷新已有 `finished_at`。

## 九、Prompt 与容错

Prompt 文件：

```text
prompt/assess/assess-diagnose.txt
prompt/assess/assess-advise.txt
prompt/assess/assess-record.txt
```

Prompt 必须明确：

1. 不重新判分。
2. 不逐题复述。
3. 不输出 Markdown。
4. 只输出指定 JSON。
5. `RecordAgent` 只输出内部线索，不输出用户话术。

容错：

1. `DiagnoseAgent` 失败时 fallback 为空 strengths/weaknesses。
2. `AdviseAgent` 失败时使用 Java 规则生成基础 overallComment/reviewGuidance。
3. `RecordAgent` 失败时 fallback 为空数组。
4. DB 保存失败必须中断。

## 十、代码组织

```text
domain/agent/service/assess/
  IAssessAgent.java
  AssessAgent.java
  model/
    command/
      AssessSaveCommand.java
    context/
      AssessContext.java
      AssessSessionContext.java
      DiagnoseContext.java
      AdviseContext.java
      RecordContext.java
    enumeration/
    result/
  subagent/
    DiagnoseAgent.java
    AdviseAgent.java
    RecordAgent.java
  support/
    AssessAgentFactory.java
    AssessSaver.java
    AssessDetailAssembler.java
    AssessmentMetricCalculator.java
    AssessResultSanitizer.java

application/src/main/resources/prompt/assess/
  assess-diagnose.txt
  assess-advise.txt
  assess-record.txt
```

## 十一、测试边界

1. 单元测试覆盖指标计算、结果清洗、DAG 路径。
2. 编译验证 `mvn -pl qa-agent-application -am compile`。
3. 启动验证 `mvn -pl qa-agent-application spring-boot:run`。
4. 不做自动接口场景验证，不做前端修改，不做真实 LLM 质量评测。
