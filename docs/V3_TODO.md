# V3 生成 Agent DAG 设计说明书

本文档为 QA_Agent V3 阶段「生成 Agent DAG」的完整技术设计说明书。全部内容基于 brainstorm 最终结论，供 Agent 阅读后直接实施。

---

## 一、V3 技术定位

### 1.1 要解决什么问题

V1 完成了核心资产 CRUD，V2 完成了资料向量化与检索。但问答集的创建仍然是**手工操作**。

V3 的目标：**用户选择资料 → 系统自动生成完整的结构化问答集**。

PRD 要求：
1. 题目必须**高度贴合用户资料**，有明确**证据边界**，能追溯到来源切片
2. 输出同时适合**知识笔记和面试回答**两种用途
3. 能进入后续反馈、评分和长期训练流程
4. 按「单 Agent 单职责」原则，生成 Agent 只负责"资料 → 问答集"

### 1.2 在系统演进中的位置

```
V1: 核心资产 CRUD          ← 已完成
V2: RAG 证据底座           ← 已完成
V3: 生成 Agent DAG         ← 本文档
V4: 反馈 Agent DAG         ← 后续
V5: 评分 Agent DAG         ← 后续
V6: Memory 体系            ← 后续
```

### 1.3 设计原则

1. **单 Agent 单职责**：只负责"资料 → 问答集"，不负责反馈或评分
2. **DAG 非自由对话**：Planner → Creator → Validator → Summarizer 四阶段链路式构造
3. **证据驱动**：每个题目必须有 `source_chunk_ids_json`，追溯到资料原文
4. **同步 SSE**：不使用 Kafka 解耦，HTTP 请求内同步执行 DAG，SSE 实时推送阶段消息
5. **用户自配模型**：LLM 由用户在 Profile 中配置，系统不绑死任何供应商

---

## 二、技术栈选型

### 2.1 核心组件

| 组件 | 选型 | 角色 |
|------|------|------|
| Agent 编排 | LangChain4j 1.14.0 `AgenticServices` | DAG 声明式组装（sequence/parallel/loop）、结构化输出、ChatModelSupplier |
| 用户 LLM | 用户自配置（OpenAI 兼容 API） | Planner/Drafter/Validator 阶段的推理引擎 |
| Supervisor LLM | 系统配置（OpenAI 兼容 API） | Guardrails 分类 + AgentListener 阶段总结生成 SSE message |
| WebSearch LLM | 系统配置（OpenAI 兼容 API） | 面经搜索（内置 web_search 能力的模型） |
| Embedding + Rerank | 阿里云 DashScope（V2 复用） | 向量化 + 重排序 |
| 证据检索 | V2 RAG `ISearchService` | 资料内证据检索 |
| 实时反馈 | SSE（SseEmitter） | 阶段消息实时推送到前端 |
| 阶段追踪 | `qa_generation_task` + `qa_generation_task_message` | 状态机 + 阶段消息持久化 |
| ChatMemory | `MessageWindowChatMemory` | 跨阶段对话记忆（memoryId=taskId） |
| Markdown 解析 | flexmark-java（V2 复用） | 资料切片 |

### 2.2 三类模型替代单一 ChatModel

系统需要三类 LLM，职责分离：

| 模型 | 来源 | 用途 | 配置路径 |
|------|------|------|---------|
| **用户模型** | `user_profile.llm_*` | DAG 核心推理（Planner/Drafter/Validator） | 每次请求动态创建 |
| **Supervisor 模型** | 系统 `application-dev.yml` | Guardrails 分类 + AgentListener 阶段总结（SSE message） | `qa-agent.llm.supervisor` |
| **WebSearch 模型** | 系统 `application-dev.yml` | 面经搜索（内置 web_search 能力的模型） | `qa-agent.llm.web-search` |

原则：用户模型专注推理质量，Supervisor 模型用便宜的模型（如 gpt-4o-mini）降成本。

### 2.3 为什么是 LangChain4j AgenticServices

- **`sequenceBuilder`**：严格顺序执行 Planner→Creator→Validator→Summarizer
- **`parallelBuilder`**：Creator 内按模块并发检索+起草
- **`loopBuilder`**：Validator REVISE→Drafter 重试 + exitCondition
- **`ChatModelSupplier`**：DAG 拓扑启动时构建一次（Spring Bean），每次请求延迟注入用户模型
- **`AgentListener`**：原生钩子，阶段完成后调 SupervisorAgent 总结 → SLF4J → SSE → DB
- **结构化输出**：定义 Java 接口返回 JSON，LangChain4j 自动生成 JSON Schema、解析、失败重试

### 2.4 为什么不用

| 拒用项 | 理由 |
|--------|------|
| Kafka | 同步 SSE 模式无需中间人，30-60s DAG 在 HTTP 超时范围内 |
| `@AiService` 注解 | 要求全局单例 ChatModel Bean，与"用户自配模型"冲突 |
| `ChatLanguageModel` | LangChain4j 1.14 agentic 模块标准接口名是 `ChatModel` |
| `langchain4j-spring-boot-starter` | 全局 Bean 自动配置与动态模型冲突，反而增加复杂度 |
| LangGraph（Python） | Java 项目跨语言调用增加运维复杂度 |
| Spring AI | DAG 编排和结构化输出弱于 LangChain4j |
| HumanInTheLoop | 生成任务不需要用户中途插手 |
| MCP | 面经搜索只需 HTTP 调模型内置 web_search，不需要多服务器动态发现 |

---

## 三、配置文件设计

### 3.1 `application-dev.yml`

```yaml
qa-agent:
  llm:
    dashscope:                                 # V2 RAG（Embedding + Rerank）
      api-key: ${DASHSCOPE_API_KEY}
      embedding-model: ${DASHSCOPE_EMBEDDING_MODEL:text-embedding-v4}
      rerank-model: ${DASHSCOPE_RERANK_MODEL:gte-rerank}
    supervisor:                                # Guardrails + AgentListener 总结
      base-url: ${SUPERVISOR_LLM_BASE_URL:https://api.openai.com}
      api-key: ${SUPERVISOR_LLM_API_KEY}
      model: ${SUPERVISOR_LLM_MODEL:gpt-4o-mini}
    web-search:                                # 面经搜索模型
      base-url: ${WEB_SEARCH_LLM_BASE_URL:https://api.openai.com}
      api-key: ${WEB_SEARCH_LLM_API_KEY}
      model: ${WEB_SEARCH_LLM_MODEL:gpt-4o-mini}
```

### 3.2 `.env` 新增变量

```bash
# Supervisor 模型（Guardrails 分类 + 阶段总结）
SUPERVISOR_LLM_BASE_URL=https://api.openai.com
SUPERVISOR_LLM_API_KEY=sk-your-key
SUPERVISOR_LLM_MODEL=gpt-4o-mini

# WebSearch 模型（面经搜索）
WEB_SEARCH_LLM_BASE_URL=https://api.openai.com
WEB_SEARCH_LLM_API_KEY=sk-your-key
WEB_SEARCH_LLM_MODEL=gpt-4o-mini
```

### 3.3 Properties 记录类（infrastructure 层）

```java
// qa-agent.infrastructure.properties.DashScopeProperties.java（V2 已有，移动配置路径）
@ConfigurationProperties("qa-agent.llm.dashscope")
public record DashScopeProperties(String apiKey, String embeddingModel, String rerankModel) {}

// V3 新增
@ConfigurationProperties("qa-agent.llm.supervisor")
public record SupervisorLlmProperties(String baseUrl, String apiKey, String model) {}

// V3 新增
@ConfigurationProperties("qa-agent.llm.web-search")
public record WebSearchLlmProperties(String baseUrl, String apiKey, String model) {}
```

### 3.4 user_profile 表扩展

```sql
ALTER TABLE user_profile
    ADD COLUMN llm_base_url VARCHAR(500) NULL COMMENT '用户自配 LLM API 端点',
    ADD COLUMN llm_api_key VARCHAR(255) NULL COMMENT '用户自配 LLM API Key',
    ADD COLUMN llm_model_name VARCHAR(100) NULL COMMENT '用户自配 LLM 模型名';
```

---

## 四、DAG 链路设计

### 4.1 状态机

```
PENDING → PLANNER → CREATOR → VALIDATOR → SUMMARIZER → COMPLETED
    │                 │          │            │
    └─────────────────┴──────────┴────────────┴──→ FAILED
```

| 状态 | 说明 | 调 LLM | 产出 |
|------|------|--------|------|
| `PLANNER` | 分析资料结构，划模块，搜面经 | 用户模型 | `PlanResult`（模块+题数+难度） |
| `CREATOR` | 按模块并发：检索证据 + 起草题目 | 用户模型 | `List<DraftItem>` |
| `VALIDATOR` | 逐批事实核查，去重，回退重试 | 用户模型 | `List<DraftItem>`（通过） |
| `SUMMARIZER` | 写入 MySQL + 生成最终汇总消息 | 否 | `qa_set_id` |
| `COMPLETED` | 完成 | - | - |
| `FAILED` | 任何阶段不可恢复错误 | - | `error_code` + `error_message` |

### 4.2 DAG 拓扑图

```
PLANNER (agentBuilder, 用户模型)
  │ @Tool: RagSearchTool (V2 RAG 浅搜) + InterviewExperienceSearchTool (面经)
  │ 强制划分模块，每模块 ≤20 题
  │ 输出: PlanResult (List<PlanItem>)
  │
  ▼
CREATOR (parallelBuilder, 多模块并发)
  │
  ├─ module:Redis ───────────────────────────┐
  │   SearcherAgent (纯Java, V2 RAG深度检索)  │
  │   DrafterAgent (10题/批串行, 用户模型)     │ ← 模块内串行
  │   @Tool: RagSearchTool (补充搜索)          │
  ├─ module:JVM ─────────────────────────────┤
  │   SearcherAgent → DrafterAgent           │ ← 三条线并发
  ├─ module:Spring ──────────────────────────┘
  │   SearcherAgent → DrafterAgent
  │
  │ 汇总全部 DraftItems
  ▼
VALIDATOR (loopBuilder, 10题/批串行)
  │ 用户模型：事实核查 (PASS/REVISE/REJECT)
  │ 跨模块去重 (REVISE 重做后与已 PASS 比对)
  │ exitCondition: 无 REVISE 项
  │
  ▼
SUMMARIZER (纯Java, 不调LLM)
  │ INSERT qa_set + qa_item + qa_set_document_ref
  │ UPDATE task status=COMPLETED
  ▼
COMPLETED
```

### 4.3 PlannerAgent

**职责**：分析资料内容，按模块规划题目结构。LLM 通过 `@Tool` 浅搜资料了解模块分布，通过面经搜索获取目标公司真实考题方向。

**Agent 接口**：`domain/qa/service/generation/agent/PlannerAgent.java`

```java
public interface PlannerAgent {
    @SystemMessage(fromResource = "prompts/generation-plan.txt")
    PlanResult plan(
        @UserMessage @V("documents") String documentsSummary,
        @V("targetRole") String targetRole,
        @V("targetDomain") String targetDomain,
        @V("targetCompany") String targetCompany,
        @V("note") String note,
        @V("questionCount") int questionCount
    );
}
```

**Prompt 约束**：每个模块建议 ≤20 题，超出则新开子模块。必须按标题层级划分模块。

**输出 PlanResult**（domain 层值对象，含 JSON Schema 注解）：

```java
public record PlanResult(
    @Description("问答集标题") String title,
    @Description("问答集概述") String description,
    @Description("模块规划列表") List<PlanItem> planItems
) {}

public record PlanItem(
    @Description("模块标签，如 Redis、JVM") String moduleTag,
    @Description("该模块题目数") int questionCount,
    @Description("难度分布") DifficultyDistribution difficultyDistribution,
    @Description("重点考察话题") List<String> focusTopics,
    @Description("建议题目类型") List<String> suggestedQuestionTypes
) {}

public record DifficultyDistribution(
    @Description("简单题数") int easy,
    @Description("中等题数") int medium,
    @Description("困难题数") int hard
) {}
```

### 4.4 Creator（SearcherAgent + DrafterAgent）

**职责**：按 Planner 输出的模块划分，多模块并发执行。每个模块内部：SearcherAgent（纯 Java 调 V2 RAG 深度检索）→ DrafterAgent（用户模型 10 题/批起草）。

**parallelBuilder 结构**：

```
CREATOR
  ├─ module:Redis    SearcherAgent → DrafterAgent(batch1-10 → batch2-5)
  ├─ module:JVM      SearcherAgent → DrafterAgent(batch1-10)
  └─ module:Spring   SearcherAgent → DrafterAgent(batch1-5)
```

**跨模块去重**：同模块内 batch2 注入 batch1 的 previousQuestions；跨模块天然不重叠（模块 topic 不同），无需去重。

**SearcherAgent**（`domain/qa/service/generation/agent/SearcherAgent.java`）：纯 Java 实现，不调 LLM。按模块标签调用 `ISearchService.execute()` 深度检索。不是 AgenticServices 代理接口，而是标准的 Java 类。

**DrafterAgent 接口**（`domain/qa/service/generation/agent/DrafterAgent.java`）：

```java
public interface DrafterAgent {
    @SystemMessage(fromResource = "prompts/generation-draft.txt")
    List<DraftItem> draft(
        @UserMessage @V("moduleTag") String moduleTag,
        @V("evidenceChunks") String evidenceChunks,
        @V("targetRole") String targetRole,
        @V("targetCompany") String targetCompany,
        @V("answerStyle") String answerStyle,
        @V("difficultyDistribution") String difficultyDist,
        @V("questionCount") int questionCount,
        @V("previousQuestions") String previousQuestions,
        @V("note") String note
    );
}
```

**DraftItem 值对象**：

```java
public record DraftItem(
    @Description("面试场景的问题表述，口语化提问方式")
    String question,

    @Description("知识笔记，供学习回顾用，包含关键概念和记忆要点")
    String knowledgeNote,

    @Description("标准面试回答，逻辑清晰、有分层结构")
    String answer,

    @Description("所属模块标签")
    String moduleTag,

    @Description("题目难度等级")
    @JsonEnumSchema(enumClass = Difficulty.class)
    Difficulty difficulty,

    @Description("资料冲突或内容不完整的提示，无则留空")
    String conflictTip,

    @Description("引用的证据块 chunk_id 列表")
    List<String> sourceChunkIds
) {}

public enum Difficulty { EASY, MEDIUM, HARD }
```

### 4.5 ValidatorAgent

**职责**：10 题/批串行事实核查。只做一件事——**验证事实是否正确**（不评价质量分布）。维度包括：答案是否有证据支撑、术语原理是否准确、引用是否正确、conflictTip 是否需要标注。

**loopBuilder + exitCondition**：每批 Validator → PASS 向前 / REVISE 回退 Drafter（最多 1 次）/ REJECT 丢弃。全部批次无 REVISE 时 exit。

```java
// Validator 内部的 loopBuilder
LoopBuilder<ValidationLoop> validateLoop = AgenticServices.loopBuilder(ValidationLoop.class)
    .addSubAgent(draftAgent)           // 先 Drafter
    .addSubAgent(validateAgent)       // 再 Validator
    .exitCondition(scope -> {
        List<ValidationResult> results = scope.get("lastValidationResults");
        return results.stream().noneMatch(r -> r.verdict() == Verdict.REVISE);
    })
    .maxIterations(2)                 // 最多循环 2 次（首次 + 1 次重试）
    .build();
```

**跨批次去重**：每批 PASS + 重做通过的题，与下一批比对。最后用 embedding 余弦相似度 >0.85 的全量去重（Java 代码，不调 LLM）。

**ValidatorAgent 接口**（`domain/qa/service/generation/agent/ValidatorAgent.java`）：

```java
public interface ValidatorAgent {
    @SystemMessage(fromResource = "prompts/generation-validate.txt")
    List<ValidationResult> validate(
        @UserMessage @V("draftItems") String draftItemsJson,
        @V("evidenceChunks") String evidenceChunks
    );
}

public record ValidationResult(
    @Description("题目在数组中的索引") int itemIndex,
    @Description("校验结论")
    @JsonEnumSchema(enumClass = Verdict.class)
    Verdict verdict,
    @Description("判定原因") String reason,
    @Description("修改建议（REVISE 时提供）") String revisionSuggestion
) {}

public enum Verdict { PASS, REVISE, REJECT }
```

### 4.6 SummarizerAgent

纯 Java，不调 LLM。流程：

```
1. Java 汇总计算：总题数、模块分布、难度分布（纯内存计算 PlanResult + ValidationResult，不调 LLM）
2. INSERT qa_set (title, userId, taskId, moduleTags, questionCount=通过题数)
3. INSERT qa_set_document_ref (qa_set_id, document_id) × task.documentIds
4. INSERT qa_item (qa_set_id, userId, question, knowledgeNote, answer,
                   moduleTag, difficulty, conflictTip, sourceChunkIdsJson, sortOrder)
5. UPDATE qa_generation_task SET status=COMPLETED, qa_set_id=?, completed_at=NOW()
6. SSE 推送 COMPLETED 事件，message 示例：
   "问答集已生成，共 18 题（计划 20 题，2 题校验不通过）。
    模块分布：Redis 10 题 / JVM 5 题 / Spring 3 题。
    难度分布：EASY 5 / MEDIUM 10 / HARD 3。总消耗 19,800 tokens。"
```

---

## 五、AgenticServices 组装

### 5.1 Spring Bean 配置（application 层）

DAG 拓扑启动时构建一次，每次请求通过 `ChatModelSupplier` 延迟注入用户模型。

`QaGenDag` 和 `QaGenModule` 是 LangChain4j `AgenticServices` 的类型占位接口——不需要实现类，框架运行时生成代理。定义在 domain 层：

```java
// domain/qa/service/generation/QaGenDag.java — 顶层 DAG 标记接口
public interface QaGenDag {}

// domain/qa/service/generation/QaGenModule.java — 单模块 Creator 标记接口
public interface QaGenModule {}
```

```java
@Configuration
public class AgentConfiguration {

    @Bean
    public AgentBuilder<PlannerAgent, ?> plannerAgent() {
        return AgenticServices.agentBuilder(PlannerAgent.class);
    }

    @Bean
    public AgentBuilder<ValidatorAgent, ?> validatorAgent() {
        return AgenticServices.agentBuilder(ValidatorAgent.class);
    }

    @Bean
    public SequenceBuilder<QaGenDag> qaGenDagBuilder(
        AgentBuilder<PlannerAgent, ?> plannerAgentBuilder,
        ParallelBuilder<QaGenModule> createParallelBuilder,
        AgentBuilder<ValidatorAgent, ?> validatorAgentBuilder
    ) {
        return AgenticServices.sequenceBuilder(QaGenDag.class)
            .addSubAgent(plannerAgentBuilder)          // 1. PLANNER
            .addSubAgent(createParallelBuilder)        // 2. CREATOR
            .addSubAgent(validatorAgentBuilder)        // 3. VALIDATOR
            .addSubAgent(new SummarizerAgent())         // 4. SUMMARIZER
    }
}
```

### 5.2 ChatModelSupplier 延迟注入

```java
ChatModel userModel = OpenAiChatModel.builder()
    .baseUrl(userProfile.getLlmBaseUrl())
    .apiKey(userProfile.getLlmApiKey())
    .modelName(userProfile.getLlmModelName())
    .build();

QaGenDag dag = qaGenDagBuilder
    .chatModelSupplier(() -> userModel)
    .chatMemoryProvider(taskId -> MessageWindowChatMemory.withMaxMessages(20))
    .listener(supervisorListener)
    .errorHandler(errorHandler)
    .build();

dag.run();
```

### 5.3 AgentListener（SupervisorAgent）

每个阶段完成后，调 SupervisorAgent 模型总结 → SLF4J → SSE → DB：

```java
AgentListener listener = new AgentListener() {
    private final Clock clock = Clock.systemDefaultZone();
    private int tokensBeforeStage = 0;

    @Override
    public void onAgentStart(Agent agent) {
        tokensBeforeStage = totalTokens.get();
    }

    @Override
    public void onAgentEnd(Agent agent, Object result) {
        int currentTokens = totalTokens.get() - tokensBeforeStage;
        int total = totalTokens.get();

        // 1. 调 SupervisorAgent 模型生成自然语言总结
        String summary = supervisorModel.chat(
            SystemMessage.from(supervisorSystemPrompt),
            UserMessage.from("总结阶段产出：" + toJson(result))
        ).aiMessage().text();

        // 2. SLF4J
        log.info("[task={}] [stage={}] {}", taskId, agent.name(), summary);

        // 3. SSE 推送
        emitter.send(SseEvent.of(taskId, agent.name(), "PROCESSING", summary,
            clock.millis(), currentTokens, total));

        // 4. DB 写入
        taskMessageRepo.insert(taskId, agent.name(), summary);
    }

    @Override
    public void onAgentError(Agent agent, Throwable error) {
        emitter.send(SseEvent.of(taskId, agent.name(), "FAILED", error.getMessage(),
            clock.millis(), 0, totalTokens.get()));
    }
};
```

### 5.4 ErrorHandler + ErrorType

```java
public enum ErrorType {
    NETWORK_ERROR,        // API 不可达、超时
    RATE_LIMITED,         // 用户 API Key 被限流
    AUTH_FAILURE,         // 用户 API Key 无效
    INVALID_RESPONSE,     // LLM 输出 JSON 解析全部失败
    CONTENT_FILTERED,     // LLM 拒答（安全拦截）
    ALL_REJECTED,         // Validator 全部不通过
    LLM_NOT_CONFIGURED,   // 用户未配 LLM
    UNKNOWN               // 兜底
}
```

Creator 阶段某模块失败 → 其他模块继续，失败模块在 error 消息中说明（尽可能多产出）。

### 5.5 AgenticScope 跨节点数据传递

AgenticServices 内置的 `AgenticScope` 在节点间共享数据，无需手动管理：

```java
// Planner 产出 → Creator 拿 PlanResult
scope.put("planResult", planResult);

// Creator 中取 Planner 的输出
PlanResult plan = scope.get("planResult");

// 汇总后传递给 Validator
scope.put("allDrafts", allDraftItems);
```

---

## 六、Prompt 管理

### 6.1 文件清单

```
qa-agent-application/src/main/resources/prompts/
  generation-plan.txt         — PlannerAgent：技术面试题集规划师
  generation-draft.txt        — DrafterAgent：技术面试出题专家
  generation-validate.txt     — ValidatorAgent：技术面试题审校专家
  web-search-system.txt       — 面经搜索：搜索并结构化输出目标公司岗位面经
  supervisor-summary.txt      — SupervisorAgent 总结：将结构化产出转为人类可读的阶段总结（2-3 句中文，含关键数据）
```

### 6.2 使用方式

接口用 `@SystemMessage(fromResource = "prompts/xxx.txt")` 从 classpath 加载。用户消息用 `@UserMessage` + `@V("变量名")` 占位符替换。

---

## 七、V2 RAG 集成

### 7.1 RagSearchTool（@Tool 注入）

```java
public class RagSearchTool {
    private final ISearchService searchService;
    private final List<String> documentIds;  // 任务关联资料

    @Tool("搜索用户上传资料中的相关知识片段")
    public List<SearchResult> search(
        @P("查询关键词") String queryText,
        @P("限定模块标签") List<String> filterModuleTags
    ) {
        SearchRequest req = SearchRequest.builder()
            .queryText(queryText)
            .strategy(SearchStrategy.HYBRID)
            .filterDocumentIds(documentIds)
            .filterModuleTags(filterModuleTags)
            .topK(5)
            .build();
        return searchService.execute(req);
    }
}
```

PlannerAgent 和 DrafterAgent 阶段注入此 Tool。

### 7.2 SearcherAgent（纯 Java，不调 LLM）

Creator 阶段每个模块先做 SearcherAgent——直接调用 `ISearchService.execute()`，按模块标签深度检索资料证据块。不经过 AgenticServices 代理，纯 Java 循环。对应的 Java 类为 `domain/qa/service/generation/agent/SearcherAgent.java`。

### 7.3 面经搜索 InterviewExperienceSearchTool

```java
public class InterviewExperienceSearchTool {
    private final ChatModel webSearchModel;
    private final String systemPrompt;  // fromResource("prompts/web-search-system.txt")

    @Tool("搜索目标公司岗位的真实面试经验和面经")
    public InterviewInsights search(
        @P("目标公司") String company,
        @P("目标岗位") String role,
        @P("技术模块") String module
    ) {
        String query = String.format("搜索 %s %s %s 面试面经", company, role, module);
        Response<AiMessage> response = webSearchModel.chat(
            SystemMessage.from(systemPrompt),
            UserMessage.from(query)
        );
        return parseJson(response.aiMessage().text(), InterviewInsights.class);
    }
}
```

PlannerAgent 阶段注入，用于获取目标公司真实出题方向。

**InterviewInsights 值对象**（domain 层）：

```java
public record InterviewInsights(
    @Description("公司名") String company,
    @Description("岗位") String role,
    @Description("技术模块") String module,
    @Description("高频考点") List<String> highFrequencyTopics,
    @Description("典型面试题示例") List<String> typicalQuestions,
    @Description("面试官侧重点") String interviewerFocus,
    @Description("来源说明") String sourceHint
) {}

public record InterviewSearchResult(
    @Description("按模块分组的搜索结果") List<InterviewInsights> insights
) {}
```

---

## 八、SSE 实时反馈

### 8.1 结构体定义（types 层）

```java
public record SseEvent(
    String taskId,             // 任务 ID
    String stage,              // PLANNER / CREATOR / VALIDATOR / SUMMARIZER / COMPLETED / FAILED
    String status,             // PROCESSING / COMPLETED / FAILED
    String message,            // SupervisorAgent 生成的阶段总结，人类可读
    long timestamp,            // System.currentTimeMillis()
    SseTokens tokens           // Token 消耗
) {
    public static SseEvent of(String taskId, String stage, String status,
                               String message, long timestamp, int currentTokens, int totalTokens) {
        return new SseEvent(taskId, stage, status, message, timestamp, new SseTokens(currentTokens, totalTokens));
    }
}

public record SseTokens(int current, int total) {}
```

### 8.2 Token 累计

`GenerationAgent` 维护一个 `AtomicInteger totalTokens` 累加器。每次 LLM 调用后累加：

```java
// GenerationAgent 内部
private final AtomicInteger totalTokens = new AtomicInteger(0);

private int accumulate(ChatResponse response) {
    TokenUsage usage = response.metadata().tokenUsage();
    return totalTokens.addAndGet(usage.totalTokenCount());
}
```

SSE 消息示例：

```json
{"stage":"PLANNER","status":"PROCESSING","message":"已分析资料结构，识别出 Redis、JVM、Spring 三个模块...","tokens":{"current":4400,"total":4400},"timestamp":1717000000000}
{"stage":"CREATOR","status":"PROCESSING","message":"Redis 模块完成 10 题，覆盖跳表原理、RDB/AOF 对比等核心话题...","tokens":{"current":8200,"total":12600},"timestamp":1717000015000}
{"stage":"COMPLETED","status":"COMPLETED","message":"问答集已生成，共 18 题，通过 18 题","tokens":{"current":0,"total":19800},"timestamp":1717000030000}
```

### 8.3 Controller 入口

```java
@PostMapping("/qa-set/create")
public SseEmitter createQaSet(@RequestBody CreateTaskRequest request) {
    SseEmitter emitter = new SseEmitter(120_000L);  // 2 分钟超时
    String taskId = UUID.randomUUID().toString();
    // 异步执行 DAG——必须先返回 emitter 再执行，否则 SSE 事件被缓冲无法实时推送
    applicationTaskExecutor.execute(() ->
        generationAgent.execute(taskId, request, emitter)
    );
    return emitter;
}
```

---

## 九、Guardrails

### 9.1 入口校验（Controller 层）

```java
if (request.getDocumentIds() == null || request.getDocumentIds().isEmpty()) {
    return Result.fail(400, "请至少选择一份资料");
}
if (request.getRequestedQuestionCount() > 100) {
    return Result.fail(400, "单次最多生成 100 题");
}
if (request.getNote() != null && request.getNote().length() > 2000) {
    return Result.fail(400, "备注过长，请控制在 2000 字符以内");
}
```

### 9.2 内容分类（SupervisorAgent 模型）

Planner 开始前，一次轻量调用：

```
判断以下用户要求是否与"根据学习资料生成技术面试问答集"相关。
用户要求：{userNote}
输出 JSON: {"valid": true/false, "reason": "..."}
```

`valid=false` → 立即 FAILED。

---

## 十、ChatMemory

```java
ChatMemoryProvider memoryProvider = memoryId ->
    MessageWindowChatMemory.withMaxMessages(20);
```

- `memoryId` = `qa_generation_task.id`
- 只有 Planner/Drafter/Validator 三个 LLM 交互阶段进入 Memory（≤15 条消息，15×3000=45K < 128K 窗口的一半）
- SearcherAgent（纯 Java）、SummarizerAgent（纯 Java）不进 Memory
- Memory 在 JVM 堆内存，请求结束 GC 回收，不落库

---

## 十一、UserLlmProvider

**接口**：`domain/qa/service/generation/IUserLlmProvider.java`

```java
public interface IUserLlmProvider {
    UserLlmConfig getConfig(String userId);
}

public record UserLlmConfig(String baseUrl, String apiKey, String modelName) {}
```

**实现**：`domain/qa/service/generation/UserLlmProvider.java`

```java
public class UserLlmProvider implements IUserLlmProvider {
    private final IIdentityRepository identityRepo;

    public UserLlmConfig getConfig(String userId) {
        UserProfileEntity profile = identityRepo.findProfileByUserId(userId);
        if (profile == null
            || isBlank(profile.getLlmBaseUrl())
            || isBlank(profile.getLlmApiKey())
            || isBlank(profile.getLlmModelName())) {
            throw new ApiException(ResultCode.LLM_NOT_CONFIGURED);
        }
        return new UserLlmConfig(
            profile.getLlmBaseUrl(),
            profile.getLlmApiKey(),
            profile.getLlmModelName()
        );
    }
}
```

未配置 → 抛 `LLM_NOT_CONFIGURED`（`ResultCode` 枚举新增项，code=40902，msg="用户未配置 LLM 接入信息，请先在 Profile 中填写 base_url、api_key 和 model_name"），任务置 FAILED。

---

## 十二、代码组织（DDD 分层）

### 12.1 文件清单

```
backend/

  qa-agent-types/src/main/java/com/dasi/qa/agent/types/
    dto/request/qa/CreateTaskRequest.java              ← 新增
    dto/response/qa/TaskStatusResponse.java            ← 新增
    dto/response/qa/TaskMessageResponse.java           ← 新增
    dto/sse/SseEvent.java                              ← 新增
    dto/sse/SseTokens.java                             ← 新增
    enumeration/GenerationStatus.java                  ← 新增：PENDING/PLANNER/CREATOR/VALIDATOR/SUMMARIZER/COMPLETED/FAILED
    enumeration/GenerationStage.java                   ← 新增：同上枚举（供 stage 字段用）
    enumeration/ErrorType.java                         ← 新增

  qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/

    qa/repository/IGenerationTaskRepository.java       ← 新增
    qa/repository/IQaRepository.java                   ← 扩展：batchCreateQaItems/createQaSetDocumentRefs

    qa/service/generation/
      QaGenDag.java                                    ← DAG 顶层标记接口
      QaGenModule.java                                 ← 单模块 Creator 标记接口
      IGenerationAgent.java                            ← 接口
      GenerationAgent.java                             ← 实现：DAG 编排入口（含 Token 累加器 + AgentListener 构造）
      IUserLlmProvider.java                            ← 接口
      UserLlmProvider.java                             ← 实现

    qa/service/generation/agent/
      PlannerAgent.java                                ← AgenticServices 代理接口（PLANNER）
      DrafterAgent.java                                ← AgenticServices 代理接口（DRAFTER）
      ValidatorAgent.java                              ← AgenticServices 代理接口（VALIDATOR）
      SearcherAgent.java                               ← 纯 Java 类，调 V2 ISearchService（非 LLM Agent）

    qa/service/generation/tool/
      RagSearchTool.java                               ← @Tool：V2 RAG 检索
      InterviewExperienceSearchTool.java               ← @Tool：面经搜索

    qa/model/
      UserLlmConfig.java                               ← 值对象
      PlanItem.java / PlanResult.java                  ← Planner 输出
      DraftItem.java / Difficulty.java                 ← Drafter 输出
      ValidationResult.java / Verdict.java             ← Validator 输出
      InterviewInsights.java                           ← 面经搜索结果

  qa-agent-infrastructure/src/main/java/com/dasi/qa/agent/infrastructure/

    persistent/entity/
      UserProfileEntity.java                           ← 扩展：llmBaseUrl/llmApiKey/llmModelName
      QaGenerationTaskEntity.java                      ← 新增
      QaGenerationTaskMessageEntity.java               ← 新增
      QaSetDocumentRefEntity.java                      ← 新增
    persistent/mapper/mysql/
      QaGenerationTaskMapper.java                      ← 新增
      QaGenerationTaskMessageMapper.java               ← 新增
      QaSetDocumentRefMapper.java                      ← 新增
    repository/
      GenerationTaskRepository.java                    ← 新增
      DocumentRepository.java                          ← 扩展（V3 增量）

    properties/
      SupervisorLlmProperties.java                     ← 新增
      WebSearchLlmProperties.java                      ← 新增

  qa-agent-interfaces/src/main/java/com/dasi/qa/agent/interfaces/

    controller/QaController.java                       ← 扩展：POST /qa-set/create + GET /task-status + GET /task-messages

  qa-agent-application/src/main/java/com/dasi/qa/agent/application/

    configuration/AgentConfiguration.java              ← 新增：DAG Bean 配置
    configuration/SupervisorLlmConfiguration.java      ← 新增：Supervisor + WebSearch ChatModel Bean
    resources/prompts/
      generation-plan.txt                              ← 新增
      generation-draft.txt                             ← 新增
      generation-validate.txt                          ← 新增
      web-search-system.txt                            ← 新增
      supervisor-summary.txt                           ← 新增
```

### 12.2 分层原则

| 层 | 内容 | 不包含 |
|----|------|--------|
| **domain** | AgenticServices 代理接口（PlannerAgent/DrafterAgent/ValidatorAgent）、AgentListener、DAG 编排接口(IGenerationAgent)+实现、Tool 类、SearcherAgent 类、值对象(PlanItem/DraftItem等)、UserLlmProvider | SQL、HTTP 调用、Spring 注解 |
| **infrastructure** | Entity + Mapper + Repository 实现、Properties 类、ChatModel Bean 实现（包装 LangChain4j） | DAG 编排、Prompt 组装 |
| **interfaces** | QaController（SSE 端点 + 查询端点） | 业务逻辑 |
| **application** | AgentConfiguration（DAG Bean）、SupervisorLlmConfiguration（ChatModel Bean）、Prompt 文件 | - |

---

## 十三、API 端点

扩展已有 `QaController`：

| 方法 | 路径 | 鉴权 | 返回 | 说明 |
|------|------|------|------|------|
| POST | `/qa-set/create` | 是 | `SseEmitter` | 创建任务并同步执行 DAG，SSE 实时推送阶段消息 |
| GET | `/qa-set/task-status` | 是 | `Result<TaskStatusResponse>` | 查询任务状态（`?taskId=`） |
| GET | `/qa-set/task-messages` | 是 | `Result<List<TaskMessageResponse>>` | 查询历史阶段消息（`?taskId=`） |

---

## 十四、V3 的预期和边界

### 14.1 预期产出

1. `POST /qa-set/create` → SSE 实时推送 → 生成完整问答集（题目+知识笔记+答案+证据引用）
2. 四阶段 DAG（Planner→Creator→Validator→Summarizer）
3. 题目有来源证据（`source_chunk_ids_json`）、难度标签、冲突提示
4. 校验不通过题目自动重试或丢弃
5. error 信息可查询

### 14.2 范围外（不做）

- 不做 Kafka 异步解耦
- 不做 Redis 缓存中转
- 不做 SSE 之外的推送（WebSocket 等）
- 不做前端对接（API 通过 curl 或集成测试验证）
- 不做多轮对话式创建
- 不做自动化评测
- 不做 HumanInTheLoop
- 不做 langchain4j-spring-boot-starter
- 不做 MCP
- 不做系统默认 LLM 降级

### 14.3 边界约束

- 单次最多 100 题
- 用户模型 Context Window > 128K tokens（Drafter 单次 < 64K）
- 生成耗时 15 题约 30-60 秒

---

## 十五、验收与测试

### 15.1 前置条件

1. V2 RAG 完成，`ISearchService.execute()` 可用
2. Supervisor 模型和 WebSearch 模型有可用 API Key
3. `qa_generation_task` / `qa_generation_task_message` / `qa_set_document_ref` 表已创建
4. user_profile 已扩展 LLM 三个字段
5. seed.sql 包含 Markdown 资料

### 15.2 验收用例

| TC | 操作 | 预期 | 验证 |
|----|------|------|------|
| TC1 | `POST /qa-set/create`，2 个文档，5 题 | SSE 流式推送各阶段消息，最终返回 COMPLETED | SSE 连接正常，收到 4 条阶段事件 |
| TC2 | 检查 Planner 输出 | PlanResult 含模块划分、`moduleTag` 非空 | SSE message 包含模块分布描述 |
| TC3 | 等待 Creator 完成 | 生成 5 题，每题含 question/knowledgeNote/answer/sourceChunkIds | DraftItem 字段全部非空 |
| TC4 | 等待 Summarizer 完成 | `qa_set` + `qa_item` + `qa_set_document_ref` 写入 | `SELECT COUNT(*) FROM qa_item WHERE qa_set_id=?` = 通过题数 |
| TC5 | 检查证据追溯 | `source_chunk_ids_json` 指向有效 `document_chunk.id` | `SELECT * FROM document_chunk WHERE id IN (...)` 返回记录 |
| TC6 | 传入空 documentIds | 返回 400，任务不创建 | `Result.code != 0` |
| TC7 | 用户未配 LLM | 任务 FAILED，error_message 提示"请先配置" | `qa_generation_task.status=FAILED` |

---

## 十六、环境变量汇总

```bash
# V3 新增
SUPERVISOR_LLM_BASE_URL=https://api.openai.com
SUPERVISOR_LLM_API_KEY=sk-your-key
SUPERVISOR_LLM_MODEL=gpt-4o-mini
WEB_SEARCH_LLM_BASE_URL=https://api.openai.com
WEB_SEARCH_LLM_API_KEY=sk-your-key
WEB_SEARCH_LLM_MODEL=gpt-4o-mini

# V2 已有（保留不变）
DASHSCOPE_API_KEY=sk-your-key
DASHSCOPE_EMBEDDING_MODEL=text-embedding-v4
DASHSCOPE_RERANK_MODEL=gte-rerank
```
