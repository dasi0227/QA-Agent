# V3 生成 Agent DAG 设计说明

## 一、生成 Agent 是什么

生成 Agent 是 QA_Agent 系统的**自动问答集生成链路**。它基于用户选择的学习资料（Markdown 文档），自动规划模块化题集结构，检索资料证据，起草题目，审校修订，最终生成一份完整的结构化技术面试问答集。

核心职责：把静态学习资料转成可直接用于面试练习的结构化 Q&A 资产，全程证据可追溯、阶段可追踪。

## 二、用例：用户视角

1. 用户创建 Profile，配置目标岗位、领域、公司、LLM 接入信息。
2. 用户上传 Markdown 学习资料（如 Redis 笔记、JVM 总结）。
3. 用户进入创建页面，选择资料、填写题数、岗位描述等，点击"开始生成"。
4. 前端通过 SSE 实时看到生成进度：请求判定 → 模块规划 → 检索起草 → 审校修订 → 结果汇总。
5. 生成完成后，问答集自动落库，用户可直接进入练习。

## 三、整体架构

```
POST /qa-agent/api/v1/qa/set/create
        │
        ▼
┌─────────────────────────────┐
│     QaController            │
│  参数校验、userId、SseEmitter │
└─────────────┬───────────────┘
              │ ① return SseEmitter
              │ ② applicationTaskExecutor.execute
              ▼
┌─────────────────────────────────────────────────────────┐
│                  GenerateAgent.execute()                 │
│                                                         │
│  1. 读取 user_profile → UserLlmModelProvider → ChatModel │
│  2. 读取 UserProfileInfo/Style/Allow                     │
│  3. 创建 GenerateSupervisor（Token 追踪 + SSE 推送）     │
│  4. 构建 GenerateContext → GenerateAgentFactory.build()  │
│  5. UntypedAgent.invoke(initialData)                    │
└─────────────┬───────────────────────────────────────────┘
              │
              ▼
┌─────────────────────────────────────────────────────────┐
│              AgenticServices DAG                         │
│                                                         │
│  sequenceBuilder("GenerateAgent")                       │
│    ├─ DECIDE agentAction                                │
│    │    DecideAgent.decide(taskId, userPrompt,           │
│    │                      retryHint)                     │
│    │    → DecideResult → scope.decideResult              │
│    │                                                     │
│    └─ ROUTE conditionalBuilder                          │
│         ├─ valid=true  → CREATE (Plan→Write→Validate→   │
│         │                 Summarize)                     │
│         └─ valid=false → ABORT agentAction              │
│              AbortAgent.abort(...)                       │
│              → publishCanceled(CONTENT_FILTERED)         │
│                                                         │
│  CREATE = sequenceBuilder("WriteAgent")                 │
│    ├─ PLAN agentAction                                  │
│    │    PlanAgent.plan(taskId, documents, userProfile,   │
│    │                   userPrompt, jobDescription,       │
│    │                   questionCount, retryHint)         │
│    │    → PlanResult → scope.planResult                 │
│    │                                                     │
│    ├─ WRITE agentAction                                 │
│    │    Phase 1: 串行预搜（RAG + Web 面经）             │
│    │    Phase 2: parallelBuilder 并行出题               │
│    │    → List<DraftItem> → scope.draftResult           │
│    │                                                     │
│    ├─ VALIDATE agentAction                              │
│    │    按 10 题一批，CompletableFuture 并行            │
│    │    每批 loopBuilder(maxIterations=2)               │
│    │      EvaluateAgent → 分拣 PASS/非PASS             │
│    │      AmendAgent → 修订后重评                       │
│    │    → List<DraftItem> → scope.validateResult        │
│    │                                                     │
│    └─ SUMMARIZE agentAction                             │
│         saveGeneratedQaSet (Java 写库)                  │
│         SummarizeAgent.summarize(...) → 完成说明        │
└─────────────────────────────────────────────────────────┘
```

## 四、DAG 拓扑与 Scope 数据流

### 4.1 顶层 DAG（GenerateAgentFactory）

```java
// sequenceBuilder: DECIDE → ROUTE
// ROUTE = conditionalBuilder(decideResult.valid → CREATE : ABORT)
// CREATE = sequenceBuilder: PLAN → WRITE → VALIDATE → SUMMARIZE
// WRITE = parallelBuilder: 每模块一个 agentAction（Phase 2 并发出题）
// VALIDATE 内部 = loopBuilder: Evaluate → [Amend → Evaluate]
```

### 4.2 Scope 数据流

Scope 是 DAG 各阶段间的唯一共享状态通道，当前定义 4 个 scope key：

| Key | 类型 | 写入阶段 | 读取阶段 | 写入方式 |
|-----|------|---------|---------|---------|
| `decideResult` | DecideResult | DECIDE | ROUTE(conditional)、ABORT | writeDecideResult() |
| `planResult` | PlanResult | PLAN | WRITE、SUMMARIZE | writePlanResult() |
| `draftResult` | List\<DraftItem\> | WRITE | VALIDATE | writeDraftResult() |
| `validateResult` | List\<DraftItem\> | VALIDATE | SUMMARIZE | writeValidateResult() |

所有读写均通过 `GeneratePhase.XXX.getScopeKey()` 引用，杜绝硬编码字符串。写入侧有 null 兜底（写空 List 或 fallback 值），读取侧无特殊处理（上游保证已写入）。

### 4.3 阶段枚举（GeneratePhase）

对外 SSE 阶段标签使用中文 + emoji：

| 枚举值 | generateStage | 说明 |
|--------|--------------|------|
| INIT | 🚀 任务启动 | 任务已创建 |
| DECIDE | 🤔 请求判定 | 判断请求是否合法 |
| PLAN | 🗓️ 规划模块 | 规划模块划分与题量 |
| WRITE | 📝 题目编写 | 对外阶段（DraftAgent 映射到此） |
| DRAFT | ✍️ 检索起草 | 起草题目 |
| VALIDATE | 🧐 审校修订 | 对外阶段（EvaluateAgent/AmendAgent 映射到此） |
| EVALUATE | 🔍 内容审校 | 审校判定 |
| AMEND | 🔧 修订完善 | 最小修订 |
| SUMMARIZE | 📈 结果汇总 | 写库并完成 |
| COMPLETE | 🎉 任务完成 | 成功终止 |
| ABORT | 🗑️ 任务终止 | 请求被拒 |
| FAIL | 💣 任务失败 | 异常终止 |

## 五、各阶段详述

### 5.1 Decide（请求判定）

**Agent**: `DecideAgent`，返回 `DecideResult`（POJO）。

```java
DecideResult decide(@V("taskId") String taskId,
                    @V("userPrompt") String userPrompt,
                    @V("retryHint") String retryHint);
```

- 使用用户模型判断请求是否与"根据学习资料生成技术面试问答集"相关
- `valid=true` → 进入 CREATE 分支
- `valid=false` → 进入 ABORT 分支，任务置为 FAILED (CANCELED)，错误类型 `CONTENT_FILTERED`
- 支持最多 2 次重试（`MAX_RETRY=2`），每次失败将错误信息作为 `retryHint` 传入下一轮
- 成功/失败后通过 `GenerateSupervisor.doSupervise()` 生成阶段总结并推送 SSE

### 5.2 Plan（模块规划）

**Agent**: `PlanAgent`，返回 `PlanResult`（POJO）。

```java
PlanResult plan(@V("taskId") String taskId,
                @V("documents") String documents,
                @V("userProfile") String userProfile,
                @V("userPrompt") String userPrompt,
                @V("jobDescription") String jobDescription,
                @V("questionCount") int questionCount,
                @V("retryHint") String retryHint);
```

输入说明：
- `documents`：资料目录摘要（文件名 + 模块标签 + 摘要，不含正文全文），由 `getDocumentsSummary()` 获取
- `userProfile`：UserProfileInfoVO → JSON（岗位、领域、公司、专业、年级、阶段）
- `userPrompt`：用户自由文本
- `jobDescription`：岗位 JD 描述，用于调整题目方向
- `questionCount`：目标题数

输出 `PlanResult`：
```java
String title;                          // 问答集标题
String description;                    // 问答集概述
List<PlanItem> planItems;              // 模块规划列表
// PlanItem: moduleTag, questionCount, focusTopics, suggestedQuestionTypes
```

规则：
- `planItems.questionCount` 总和必须等于请求题数
- 单个模块不超过 20 题，超出需拆分
- 资料不足时仍输出计划，风险写入 `focusTopics` 或 `suggestedQuestionTypes`
- 不允许创建没有资料支撑的模块
- 启用 `allowFallback` 时，调用失败使用 `fallbackPlan()`（单模块 "General"）
- 支持最多 2 次重试

### 5.3 Write（检索起草）

Write 阶段采用**两阶段分离**设计：

**Phase 1：串行预搜证据**

在并行出题之前，主线程串行遍历所有 PlanItem，每个模块调用：
- `RagEvidenceProvider.search()`：调 V2 RAG 检索（PostgreSQL chunk_search，HYBRID 策略）
- `WebEvidenceProvider.search()`（如用户开启 allowWebSearch）：调 web-search 模型搜索面经趋势

预搜结果合并为 `{"ragResults": [...], "interviewInsights": [...]}` JSON 字符串，存入 `evidenceMap`。此阶段每次仅占用 1 个 DB 连接，根治并行下 PG 连接池耗尽问题。

**Phase 2：并行出题**

每个模块创建一个 `agentAction`，通过 `parallelBuilder` 并发执行。agentAction 内部**只做纯 LLM 调用，零 DB 访问**（证据已在 Phase 1 预加载）。

```java
// parallelBuilder，线程池 3
UntypedAgent writer = AgenticServices.parallelBuilder()
        .executor(Executors.newFixedThreadPool(3))
        .subAgents(moduleAgents)
        .output(moduleScope -> draftResults)
        .build();
```

**Agent**: `DraftAgent`，返回 JSON 字符串。

```java
String draft(@V("taskId") String taskId,
             @V("moduleTag") String moduleTag,
             @V("evidence") String evidence,
             @V("userProfile") String userProfile,
             @V("userPrompt") String userPrompt,
             @V("jobDescription") String jobDescription,
             @V("questionCount") int questionCount,
             @V("previousQuestions") String previousQuestions,
             @V("answerStyle") String answerStyle,
             @V("retryHint") String retryHint);
```

输入说明：
- `evidence`：Phase 1 预搜的 JSON（RAG 结果 + 面经洞察）
- `previousQuestions`：同模块前面批次的问题文本列表，用于模块内跨批次去重
- `answerStyle`：用户偏好的回答风格（来自 UserProfileStyleVO）
- `jobDescription`：岗位 JD 描述

输出 `DraftItem[]` JSON 数组（7 字段）：

```json
{
  "question": "面试场景的口语化问题",
  "knowledgeNote": "知识笔记，供学习回顾用",
  "answer": "标准面试回答，逻辑清晰有分层",
  "tag": "题目分类标签",
  "difficulty": "EASY / MEDIUM / HARD",
  "conflictTip": "证据不足或冲突提示，无则留空",
  "evidence": "从证据块中引用的原文句子"
}
```

特性：
- 每批最多 10 题，模块内串行分批
- 每批支持最多 2 次重试，失败后使用 `fallbackDraft()`（占位题目）
- 批次间通过 `previousQuestions` 去重
- 所有模块的 draftResults 汇总到 `Collections.synchronizedList`

### 5.4 Validate（审校修订）

按 10 题一批，通过 `CompletableFuture` 并行处理各批次。每批内使用 `loopBuilder` 实现"审校→修订→复审"循环。

**loopBuilder 结构**（maxIterations=2）：

```
EvaluateAgent（审校判定）
    │
    ├─ PASS  → 加入 passItems
    └─ 非PASS → 组装 AmendItem
         │
         ▼
    AmendAgent（最小修订）
         │
         ▼
    EvaluateAgent（二次审校）  ← flag 控制是否退出循环
```

**EvaluateAgent**：返回 JSON 字符串。

```java
String evaluate(@V("taskId") String taskId,
                @V("draftItemsJson") String draftItemsJson,
                @V("userPrompt") String userPrompt,
                @V("jobDescription") String jobDescription,
                @V("retryHint") String retryHint);
```

输出 `EvaluateItem[]` JSON 数组（3 字段）：
```json
{
  "verdict": "PASS / AMEND / REJECT",
  "reason": "判定原因",
  "suggestion": "修改建议，AMEND 时提供"
}
```

**AmendAgent**：返回 JSON 字符串。

```java
String amend(@V("taskId") String taskId,
             @V("amendItemsJson") String amendItemsJson,
             @V("userPrompt") String userPrompt,
             @V("jobDescription") String jobDescription,
             @V("answerStyle") String answerStyle,
             @V("retryHint") String retryHint);
```

输入 `AmendItem[]` → JSON：
```json
{
  "draftResult": { ... },
  "reason": "审校不通过的原因",
  "suggestion": "修改建议"
}
```

输出修订后的 `DraftItem[]`，长度必须等于输入。

**修订循环规则**：
- flag 初始为 `false`（不退出），Evaluate 中 `flag.set(amends.isEmpty())`
- PASS 直接进入 passItems
- 非 PASS（AMEND/REJECT）均送入 AmendAgent 修订
- AmendAgent 输出与输入长度不匹配 → 该批 AMEND 项退回原始题目
- 循环最多 2 次，第二次审校后仍不通过即丢弃
- 每批有独立的 `exceptionally` 兜底：异常时退回原始 batch 题目，不丢数据
- EvaluateAgent/AmendAgent 均支持最多 2 次重试

### 5.5 Summarize（结果汇总）

**Agent**: `SummarizeAgent`，返回自然语言字符串。

```java
String summarize(@V("taskId") String taskId,
                 @V("userPrompt") String userPrompt,
                 @V("jobDescription") String jobDescription,
                 @V("userProfile") String userProfile,
                 @V("title") String title,
                 @V("description") String description,
                 @V("requiredCount") int requiredCount,
                 @V("generatedCount") int generatedCount,
                 @V("modules") String modules,
                 @V("tags") String tags,
                 @V("qa") String qa);
```

Summarize 阶段流程：
1. 读取 `planResult` 和 `validateResult`
2. 调用 `agentRepository.saveGeneratedQaSet()` 写入 `qa_set`、`qa_item`、`qa_set_document_ref`
3. 统计模块名、标签分布
4. 调用 `SummarizeAgent.summarize()` 生成完成说明文本
5. `markTaskCompleted()` 标记任务完成
6. 推送 `COMPLETE` SSE 事件

## 六、重试与容错机制

### 6.1 LLM 调用重试

所有 Agent 调用统一使用 `MAX_RETRY=2` 的重试循环：

```
for (attempt = 0; attempt <= MAX_RETRY; attempt++) {
    try {
        result = agent.doSomething(..., retryHint);
        break;
    } catch (Exception e) {
        retryHint = e.getMessage();
        // 最后一次失败 → fallback
    }
}
```

每次失败将异常信息作为 `retryHint` 传入下一轮调用，LLM 可据此修正输出格式或内容。

### 6.2 Fallback 策略

| Agent | Fallback 行为 |
|-------|--------------|
| DecideAgent | 返回 `valid=false`，终止 DAG |
| PlanAgent | 返回单模块 "General" 规划（需 allowFallback 开启，否则抛异常） |
| DraftAgent | 返回占位题目（evidence 原样填充） |
| EvaluateAgent | 全部判定为 PASS（保守宽容） |
| AmendAgent | 返回原始 DraftItem（不做修改） |
| SummarizeAgent | 返回简单统计字符串 |

### 6.3 Validate 批次兜底

```java
.exceptionally(ex -> {
    log.warn("批次执行异常，退回原始题目");
    return batch;  // 异常时保留原始题目，不丢数据
})
```

## 七、SSE 实时反馈

### 7.1 EventPublisher

```java
public void publishEvent(GeneratePhase phase, GenerateStatus status,
                         String message, int currentTokens) {
    SseEvent sseEvent = SseEvent.builder()
            .taskId(taskId)
            .phase(phase.getGenerateStage())   // 中文+emoji 标签
            .status(status.name())
            .message(message)
            .timestamp(System.currentTimeMillis())
            .currentTokens(currentTokens)
            .totalTokens(totalTokens.get())
            .isCompleted(status.isTerminated())
            .build();
    agentRepository.appendTaskMessage(taskId, userId, phase, message,
            jsonUtil.toJsonString(sseEvent)); // content 存完整 JSON
    eventSink.accept(sseEvent);
}
```

### 7.2 Token 追踪

- 使用 `ChatModelListener` 在每次 LLM 响应时累计 `AtomicInteger totalTokens`
- `GenerateSupervisor.doSupervise()` 计算增量 token（`total - lastPublished`）并推送

### 7.3 GenerateSupervisor

替代早期 `AgentListener` 的设计：在每个 Agent 调用成功后，调用 Supervisor LLM 生成可读的阶段总结，然后推送 SSE + 写 DB。

```java
public void doSupervise(GeneratePhase phase, String reference) {
    // 调 supervisorChatModel 生成总结 → publishEvent
}
```

调用时机：Decide/Plan/Draft/Evaluate/Amend 成功后立即调用。

## 八、代码组织（DDD 分层）

```
domain/agent/service/generate/
  GenerateAgent.java              ← DAG 执行编排
  IGenerateAgent.java             ← 接口
  model/
    context/
      GenerateContext.java         ← DAG 上下文（Step 函数式接口）
      DecideContext.java
      AbortContext.java
      PlanContext.java
      WriteContext.java
      DraftContext.java
      ValidateContext.java
      ValidateLoopContext.java
      EvaluateContext.java
      AmendContext.java
      SummarizeContext.java
    enumeration/
      GeneratePhase.java           ← 阶段枚举（含 scopeKey）
      GenerateStatus.java          ← 任务状态
      VerdictType.java             ← PASS/AMEND
    exception/
      GenerateException.java
    result/
      DecideResult.java
      PlanResult.java / PlanItem.java
      DraftItem.java
      EvaluateItem.java
      AmendItem.java
      InterviewInsights.java       ← Web 面经结构
  subagent/
      DecideAgent.java
      AbortAgent.java
      PlanAgent.java
      DraftAgent.java
      EvaluateAgent.java
      AmendAgent.java
      SummarizeAgent.java
  support/
      GenerateAgentFactory.java    ← DAG 组装
      GenerateSupervisor.java      ← 阶段总结器
      RagEvidenceProvider.java     ← RAG 预搜
      WebEvidenceProvider.java     ← 联网面经预搜
      UserLlmModelProvider.java    ← 用户模型构建

application/src/main/resources/prompt/
  generation-decide.txt
  generation-abort.txt
  generation-plan.txt
  generation-draft.txt
  generation-evaluate.txt
  generation-amend.txt
  generation-summarize.txt
  supervisor-summary.txt
  web-search-system.txt

interfaces/controller/
  QaController.java               ← SSE 端点
```

### 分层原则

| 层 | 放什么 | 不放什么 |
|----|--------|---------|
| **domain** | Agent 接口 + DAG 编排 + Context + Result POJO + 枚举 | SQL、HTTP、Kafka、Spring 注解 |
| **infrastructure** | AgentRepository 实现（MyBatis-Plus） | 业务编排逻辑 |
| **interfaces** | QaController（SSE 端点） | Agent 调用逻辑 |
| **application** | Spring Boot 配置、Bean 装配 | — |

## 九、Prompt 管理

### 9.1 输入参数透传

所有 Agent 的 System Prompt 使用 `@SystemMessage(fromResource = "prompt/xxx.txt")` 外部化。User Message 使用 `@UserMessage` + `{{变量名}}` 占位符。

当前 Agent 输入参数矩阵：

| 参数 | Decide | Plan | Draft | Evaluate | Amend | Summarize |
|------|--------|------|-------|----------|-------|-----------|
| taskId | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| userPrompt | ✓ | ✓ | ✓ | ✓ | ✓ | ✓ |
| jobDescription | | ✓ | ✓ | ✓ | ✓ | ✓ |
| userProfile | | ✓ | ✓ | | | ✓ |
| answerStyle | | | ✓ | | ✓ | |
| evidence | | | ✓ | | | |
| moduleTag | | | ✓ | | | |
| questionCount | | ✓ | ✓ | | | |
| previousQuestions | | | ✓ | | | |
| documents | | ✓ | | | | |
| draftItemsJson | | | | ✓ | | |
| amendItemsJson | | | | | ✓ | |
| retryHint | ✓ | ✓ | ✓ | ✓ | ✓ | |
| title/description | | | | | | ✓ |
| requiredCount/generatedCount | | | | | | ✓ |
| modules/tags/qa | | | | | | ✓ |

### 9.2 JSON 格式约束

String-returning Agent（DraftAgent、EvaluateAgent、AmendAgent）的 @UserMessage 末尾包含显式格式约束：

```
输出要求：
1. 只输出一个合法 JSON 数组，以 [ 开头，以 ] 结尾。
2. 不要输出 Markdown，不要使用 ```json 代码块。
3. 不要输出解释文字或任何非 JSON 内容。
4. 必须包含所有指定字段，缺失字段用 "" 填充。
5. 数组长度必须等于输入数量。
6. 不允许添加未定义字段。
```

POJO-returning Agent（DecideAgent、PlanAgent）使用 LangChain4j 框架自动生成的 JSON Schema，System Prompt 中保留业务规则和判定标准。

## 十、技术选型

| 组件 | 选型 | 说明 |
|------|------|------|
| Agent 框架 | LangChain4j 1.14.0 AgenticServices | 声明式 DAG：sequenceBuilder / parallelBuilder / conditionalBuilder / loopBuilder |
| LLM 模型 | 用户自配置（user_profile.llm_*） | OpenAI 兼容接口，OpenAiChatModel 构建，60s 超时，1 次重试 |
| Supervisor 模型 | 系统配置（SUPERVISOR_LLM_*） | 阶段总结生成 |
| Web-Search 模型 | 系统配置（WEB_SEARCH_LLM_*） | 联网面经检索 |
| RAG 检索 | V2 PostgreSQL chunk_search | pgvector + zhparser，HYBRID 策略 |
| 异步执行 | ThreadPoolTaskExecutor + CompletableFuture | WRITE 并行出题、VALIDATE 并行审校 |
| JSON 解析 | fastjson2 | String-returning Agent 输出解析 |
| SSE | Spring SseEmitter | 120s 超时，实时推送阶段消息 |

## 十一、与 V2 RAG 的关系

生成 Agent 是 V2 RAG 的下游消费者：

| 阶段 | RAG 调用方式 | 用途 |
|------|------------|------|
| WRITE Phase 1 | Java 侧直接调用 `RagEvidenceProvider.search()` | 预搜每个模块的证据片段 |
| WRITE Phase 1 | Java 侧直接调用 `WebEvidenceProvider.search()` | 预搜目标公司岗位的面经趋势 |

RAG 检索结果与 Web 面经合并为一个 evidence JSON，传入 DraftAgent 作为出题依据。每道 DraftItem 的 `evidence` 字段从中引用原文句子，保证答案可溯源。

## 十二、V3 边界（不做的事）

1. 不做 Kafka 异步——整个 DAG 在 HTTP 请求线程内同步执行（除 SSE 推送提前返回）
2. 不做语义相似度去重——Validate 阶段当前只做 LLM 侧重复判定
3. 不做 Redis 缓存——任务状态直接写 MySQL，SSE 即发即忘
4. 不做系统 LLM 降级——用户未配置 LLM 即 FAILED（LLM_NOT_CONFIGURED）
5. 不做前端联调——API 通过 curl/集成测试验证
6. 不做自动化评测——端到端质量人工验证
7. 不做 `responseFormat` API 级 JSON Schema——枚举字段改用 String，Prompt 手写格式约束
8. 不做跨模块去重——当前只做模块内分批次去重（previousQuestions）
9. 不做 ChatMemory / @MemoryId——已从全部 Agent 和 Factory 中移除
10. 不做 Agent @Tool 按需调用——RAG 和 Web 搜索均在 Java 侧预搜，Agent 变为纯 LLM 调用
