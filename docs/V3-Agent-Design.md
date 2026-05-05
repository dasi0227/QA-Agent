# V3 生成 Agent DAG 设计说明

## 一、V3 技术定位

### 1.1 要解决什么问题

V1 完成了核心资产 CRUD，V2 完成了资料向量化与检索。但问答集的创建仍然是**手工操作**——用户在 RepositoryPage 逐条录入题目、答案、知识点。

V3 的目标是：**用户选择资料 → 系统自动生成完整的结构化问答集**。

这不是简单的"丢给 ChatGPT 让它出题"。PRD 明确要求：

1. 题目必须**高度贴合用户资料**，不是脱离资料自由发挥
2. 每道题目有明确的**证据边界**，能追溯到来源切片
3. 输出同时适合**知识笔记和面试回答**两种用途
4. 能进入后续反馈、评分和长期训练流程

### 1.2 在系统演进中的位置

```
V1: 核心资产 CRUD          ← 已完成
V2: RAG 证据底座           ← 已完成
V3: 生成 Agent DAG         ← 本文档（当前版本）
V4: 反馈 Agent DAG         ← 后续
V5: 评分 Agent DAG         ← 后续
V6: Memory 体系            ← 后续
```

V3 是系统从"静态资产管理"到"智能 Agent 链路"的转折点。V2 的 RAG 模块在 V3 才第一次被真正消费。

### 1.3 设计原则

1. **单 Agent 单职责**：生成 Agent 只负责"资料 → 问答集"，不负责反馈或评分
2. **DAG 而非自由对话**：不是一次 prompt 完成全部，而是 PLAN → SEARCH → DRAFT → VALIDATE → FINALIZE 五个阶段的链路式构造
3. **证据驱动**：每个题目必须有 `source_chunk_ids_json`，能追溯到资料原文
4. **可追踪**：每个阶段的状态和输出写入 `qa_generation_task` + `qa_generation_task_message`
5. **异步执行**：生成任务通过 Kafka 异步触发，不阻塞 HTTP 请求

---

## 二、技术栈选型

### 2.1 核心组件

| 组件 | 选型 | 角色 |
|------|------|------|
| Agent 编排 | LangChain4j 1.14.0 | 结构化 Prompt 调用、JSON 输出解析、阶段编排 |
| LLM | **用户自配置**（OpenAI 兼容 API） | 规划、起草、校验阶段的推理引擎，必须由用户在 Profile 中提供 base_url + api_key + model_name |
| 证据检索 | V2 RAG `POST /source-document/search` | 各阶段获取资料证据 |
| 异步任务 | Kafka + `message_job` 表 | 任务创建 → 阶段推进 |
| 阶段追踪 | `qa_generation_task` + `qa_generation_task_message` | 状态机、阶段消息持久化 |
| 输出落库 | 已有 `qa_set` / `qa_item` / `qa_set_document_ref` | 生成结果持久化 |
| 用户上下文 | `user_profile`（含新增 LLM 配置字段） | 目标岗位、领域、答案风格 + LLM 接入配置 |

### 2.2 LLM 提供商设计——用户自配置

系统不绑死任何 LLM 供应商。用户在 `user_profile` 中配置自己的 LLM 接入信息：

- `llm_base_url`：服务商 API 端点（如 `https://api.openai.com`、`https://dashscope.aliyuncs.com/compatible-mode/v1`）
- `llm_api_key`：用户自己的 API Key
- `llm_model_name`：模型名（如 `gpt-4o`、`deepseek-chat`、`qwen-plus`）

**读取链路**：

```
GenerationAgent.execute(taskId)
  → 读取 task.userId
  → 读取 user_profile(userId).llm_base_url / llm_api_key / llm_model_name
  → 如果三个字段均非空 → 创建用户指定的 ChatLanguageModel 实例
  → 如果有任一为空   → 任务直接 FAILED，error_message = "用户未配置 LLM 接入信息，请先在 Profile 中填写 base_url、api_key 和 model_name"
```

**为什么用 OpenAI 兼容 API 格式**：市面上绝大多数 LLM 供应商（OpenAI、DeepSeek、Moonshot、智谱、百川、DashScope 兼容模式、Ollama、vLLM）都兼容 OpenAI 的 `/v1/chat/completions` 协议。LangChain4j 的 `OpenAiChatModel` 支持自定义 `baseUrl`，一行配置即可接入任意兼容供应商。

**强制用户提供**：系统不提供默认 LLM。用户必须在 `user_profile` 中完成 LLM 配置后才能创建生成任务。

### 2.3 为什么是 LangChain4j 而不是手写 DAG

手写 DAG（每个阶段一个方法 + try/catch + 状态机）可以实现，但 LangChain4j 提供了三个关键能力：

1. **结构化输出（Structured Output）**：定义 Java 接口返回类型，LangChain4j 自动处理 JSON Schema 约束、响应解析、重试。不需要手写 JSON 解析和容错
2. **多供应商切换**：`OpenAiChatModel` + 自定义 `baseUrl` 可以接入几乎所有主流 LLM 供应商，无需为每个供应商写适配器
3. **记忆管理**：`ChatMemory` 可以跨阶段保持对话上下文（当前 V3 各阶段 Prompt 独立，但 V6 Memory 接入时此能力直接可用）

**选型结论**：阶段编排手写 `GenerationAgent`，LLM 调用交给 LangChain4j。不使用 `@AiService` 注解（该注解要求模型为全局单例 Bean），改用每次调用时基于用户配置动态创建 `ChatLanguageModel` 实例：

```java
// 运行时按用户配置创建模型，而非全局单例 Bean
ChatLanguageModel model = OpenAiChatModel.builder()
    .baseUrl(userProfile.getLlmBaseUrl())
    .apiKey(userProfile.getLlmApiKey())
    .modelName(userProfile.getLlmModelName())
    .build();
```

### 2.4 为何不用 LangGraph 或 Spring AI

- **LangGraph**（Python 生态）：项目是 Java 后端，跨语言调用增加运维复杂度
- **Spring AI**：DAG 编排能力弱于 LangChain4j，多供应商切换和结构化输出不如 LangChain4j 成熟
- **手写完整 DAG 编排**：LLM 调用的 JSON 输出解析和重试逻辑容易写出 bug，不如交给框架

---

## 三、DAG 链路设计

### 3.1 状态机

```
PENDING → PLANNING → SEARCHING → DRAFTING → VALIDATING → FINALIZING → COMPLETED
    │         │          │          │           │             │
    └─────────┴──────────┴──────────┴───────────┴─────────────┴──→ FAILED
```

| 状态 | 说明 | 产出 |
|------|------|------|
| `PENDING` | 任务已创建，等待执行 | - |
| `PLANNING` | LLM 分析资料结构，生成题目规划 | `List<PlanItem>`（模块分布、题数、难度） |
| `SEARCHING` | 调用 RAG 为每个 PlanItem 检索证据 | `Map<String, List<SearchResult>>`（PlanItem → 证据块） |
| `DRAFTING` | LLM 基于证据起草题目 | `List<DraftItem>`（question/knowledgeNote/answer） |
| `VALIDATING` | LLM 校验题目质量（证据引用、难度、完整性） | `List<ValidationResult>`（通过 / 需修改 / 需删除） |
| `FINALIZING` | 通过的题目写入数据库，生成 qa_set | `qa_set_id` |
| `COMPLETED` | 问答集落库成功 | - |
| `FAILED` | 任何阶段出现不可恢复错误 | `error_code` + `error_message` |

### 3.2 各阶段详述

#### 3.2.1 PLANNING 阶段

**输入**：
- 资料的 `document_ids`（通过 RAG 检索获取所有资料的内容摘要）
- `user_profile`（`targetRole`、`targetDomain`、`targetCompany`）
- 任务参数（`requested_question_count`、`note`）

**LLM Prompt 要点**：
```
你是技术面试题集规划师。用户的目标岗位是 {targetRole}，目标领域是 {targetDomain}。

请分析以下资料内容，规划一套面试问答集的结构：
1. 按模块划分：识别资料的模块结构
2. 每个模块的题目数量（总计约 {requestedQuestionCount} 题）
3. 难度分布：简单/中等/困难的比例
4. 题目类型建议：概念题 / 实践题 / 原理题 / 场景题

用户额外要求：{note}

输出 JSON 格式：
{
  "title": "题集标题",
  "description": "题集概述",
  "planItems": [
    {
      "moduleTag": "Redis",
      "questionCount": 5,
      "difficultyDistribution": {"EASY": 1, "MEDIUM": 3, "HARD": 1},
      "focusTopics": ["跳表原理", "持久化策略对比", "集群分片"],
      "suggestedQuestionTypes": ["概念题", "原理题", "场景题"]
    }
  ]
}
```

**输出**：`List<PlanItem>`，写入 `qa_generation_task_message`

#### 3.2.2 SEARCHING 阶段

**职责**：为每个 PlanItem 调用 V2 RAG 检索，获取证据块。

这一步**不需要 LLM**，直接调用已有的 `/source-document/search` 接口。

**流程**：
```
for each PlanItem:
    queryText = PlanItem.moduleTag + " " + focusTopics 拼接
    POST /source-document/search {
        queryText: ...,
        strategy: HYBRID,
        filterDocumentIds: task.documentIds,
        filterModuleTags: [PlanItem.moduleTag],
        topK: 5
    }
    → 收集 SearchResult 列表，关联到对应 PlanItem
```

**输出**：`Map<PlanItem, List<SearchResult>>`（PlanItem → 证据块列表）

#### 3.2.3 DRAFTING 阶段

**职责**：LLM 根据规划 + 证据，逐一模块起草题目。

这是最核心的生成阶段。对每个模块分别调用 LLM。

**LLM Prompt 要点**：
```
你是技术面试出题专家。请根据以下资料证据，为 {moduleTag} 模块生成面试题目。

用户画像：{targetRole} @ {targetCompany}
答案风格：{answerStyle}
难度要求：{difficultyDistribution}

资料证据（来自用户自己的笔记）：
---
{evidenceChunks}
---
用户额外要求：{note}

为 {moduleTag} 模块生成 {questionCount} 道题目。

每道题目必须：
- question: 面试场景的问题表述
- knowledgeNote: 知识笔记（学习回顾用）
- answer: 标准面试回答（口语化、逻辑清晰、有分层结构）
- moduleTag: 模块标签
- difficulty: EASY / MEDIUM / HARD
- conflictTip: 如果资料之间存在冲突或资料内容不完整，在此标注。无则留空
- sourceChunkIds: 引用的证据块 chunk_id 列表

输出 JSON：
{
  "draftItems": [
    {
      "question": "...",
      "knowledgeNote": "...",
      "answer": "...",
      "moduleTag": "Redis",
      "difficulty": "MEDIUM",
      "conflictTip": null,
      "sourceChunkIds": ["chunk-uuid-1", "chunk-uuid-2"]
    }
  ]
}
```

**输出**：`List<DraftItem>`

#### 3.2.4 VALIDATING 阶段

**职责**：LLM 校验生成的题目质量。三个维度：

1. **证据一致性**：每个答案是否能在资料证据中找到支撑？
2. **难度合理性**：难度标签是否符合内容的实际深度？
3. **格式完整性**：question / knowledgeNote / answer 是否都完整且有意义？

**LLM Prompt 要点**：
```
你是技术面试题审校专家。请逐题审核以下题目。

对每道题判断：
- PASS：题目质量合格，证据引用正确
- REVISE：内容有问题但可修改（给出修改建议）
- REJECT：严重偏离资料或质量不合格

输出 JSON：
{
  "validationResults": [
    {
      "itemIndex": 0,
      "verdict": "PASS" | "REVISE" | "REJECT",
      "reason": "简要原因",
      "revisionSuggestion": null
    }
  ]
}
```

**处理策略**：
- PASS 的题目直接进入 FINALIZE
- REVISE 的题目回退到 DRAFT 阶段，附带 revisionSuggestion 重新生成（最多重试 1 次）
- REJECT 的题目直接丢弃，不进入最终题集

**输出**：`List<ValidationResult>`

#### 3.2.5 FINALIZING 阶段

**职责**：将通过的题目写入数据库。这一步也不需要 LLM。

**流程**：
```
1. INSERT qa_set（title=Plan.title, userId, taskId, moduleTags, questionCount=通过题数）
2. for each documentId in task.documentIds:
     INSERT qa_set_document_ref(qa_set_id, document_id)
3. for each passed DraftItem:
     INSERT qa_item(qa_set_id, userId, question, knowledgeNote, answer,
                    moduleTag, difficulty, conflictTip, sourceChunkIdsJson, sortOrder)
4. UPDATE qa_generation_task SET status=COMPLETED, qa_set_id=... SET completed_at=NOW()
5. 发送 Kafka 消息通知前端（后续版本对接 SSE/WebSocket）
```

### 3.3 完整链路图

```
用户: POST /qa-set/create { title, note, documentIds[], requestedQuestionCount }
       │
       ▼
  QaController.createTask() → INSERT qa_generation_task (status=PENDING)
       │
       ▼
  Kafka: topic=qa.generation.task → Consumer
       │
       ▼
  GenerationAgent.execute(taskId)
       │
       ├─ [PLANNING]  AiService.plan(documents, profile, note) → Plan
       │     └─ 写入 qa_generation_task_message(stage=PLANNING)
       │
       ├─ [SEARCHING]  for each PlanItem → RAG search
       │     └─ 写入 qa_generation_task_message(stage=SEARCHING)
       │
       ├─ [DRAFTING]   for each module → AiService.draft(planItem, evidence) → DraftItems
       │     └─ 写入 qa_generation_task_message(stage=DRAFTING)
       │
       ├─ [VALIDATING] AiService.validate(draftItems) → ValidationResults
       │     └─ PASS → 继续 / REVISE → 重试 DRAFT / REJECT → 丢弃
       │     └─ 写入 qa_generation_task_message(stage=VALIDATING)
       │
       ├─ [FINALIZING] 写入 qa_set + qa_items + qa_set_document_ref
       │     └─ 写入 qa_generation_task_message(stage=FINALIZING)
       │
       └─ [COMPLETED]  更新 task status=COMPLETED
```

---

## 四、接口与交互

### 4.1 API 端点

扩展已有 `QaController`，新增生成任务端点。

| 方法 | 路径 | 鉴权 | 说明 |
|------|------|------|------|
| POST | `/qa-set/create-task` | 是 | 创建生成任务，返回 taskId |
| GET  | `/qa-set/task-status` | 是 | 查询任务状态和阶段（`?taskId=...`） |
| GET  | `/qa-set/task-messages` | 是 | 查询任务阶段消息列表（`?taskId=...`） |

### 4.2 请求/响应

**POST `/qa-set/create-task` 请求**：

```json
{
  "title": "Redis 面试题集",
  "note": "优先覆盖项目经历和 Redis 高频追问，答案风格偏口语化但逻辑清晰。",
  "documentIds": ["doc-uuid-1", "doc-uuid-2"],
  "requestedQuestionCount": 15,
  "allowGeneralKnowledge": false,
  "allowWebSearch": false
}
```

**响应**：

```json
{
  "code": 0,
  "data": {
    "taskId": "task-uuid-...",
    "status": "PENDING",
    "stage": null,
    "createdAt": "2026-05-05 12:00:00"
  }
}
```

**GET `/qa-set/task-status` 响应**：

```json
{
  "code": 0,
  "data": {
    "taskId": "task-uuid-...",
    "title": "Redis 面试题集",
    "status": "PROCESSING",
    "stage": "DRAFTING",
    "qaSetId": null,
    "errorCode": null,
    "errorMessage": null,
    "createdAt": "2026-05-05 12:00:00",
    "startedAt": "2026-05-05 12:00:01",
    "completedAt": null
  }
}
```

**GET `/qa-set/task-messages` 响应**：

```json
{
  "code": 0,
  "data": [
    { "stage": "PLANNING", "message": "{\"planItems\":[...]}", "createdAt": "..." },
    { "stage": "SEARCHING", "message": "{\"searchCount\":12}", "createdAt": "..." },
    { "stage": "DRAFTING", "message": "{\"moduleProgress\":\"Redis 3/5\"}", "createdAt": "..." }
  ]
}
```

---

## 五、Prompts 管理

### 5.1 设计原则

- **不在 Java 代码中硬编码 Prompt**：Prompt 模板放在 `resources/prompts/` 目录下，以 `.txt` 格式存储
- **变量替换**：使用 `{variableName}` 占位符，Java 层用简单的字符串替换（`String.replace`）
- **可独立修改**：修改 Prompt 不触发 Java 重编译
- **版本化管理**：Git 跟踪 Prompt 变更历史

### 5.2 Prompt 文件

```
qa-agent-application/src/main/resources/prompts/
  generation-plan.txt      — PLANNING 阶段 Prompt
  generation-draft.txt     — DRAFTING 阶段 Prompt
  generation-validate.txt  — VALIDATING 阶段 Prompt
```

### 5.3 运行时模型创建（替代 @AiService）

由于 LLM 提供商是用户级配置而非全局 Bean，不能使用 `@AiService` 注解（该注解要求一个全局单例 `ChatLanguageModel` Bean）。改为在 `GenerationAgent` 执行每个阶段时，运行时创建模型实例：

```java
// domain/qa/service/generation/GenerationAgent.java（编排实现）

// 1. 获取用户 LLM 配置
UserLlmConfig llmConfig = userLlmProvider.getConfig(userId);

// 2. 运行时创建 ChatLanguageModel（每次调用创建新实例，轻量开销）
ChatLanguageModel model = OpenAiChatModel.builder()
    .baseUrl(llmConfig.baseUrl())
    .apiKey(llmConfig.apiKey())
    .modelName(llmConfig.modelName())
    .build();

// 3. 通过 AiServices 绑定动态模型 + 结构化输出
IPlanAgent planAgent = AiServices.create(IPlanAgent.class, model);

// 4. 调用
PlanResult result = planAgent.plan(documentsSummary, targetRole, targetDomain, note, questionCount);
```

`AiServices.create()` 是 LangChain4j 的运行时工厂方法，接收任意 `ChatLanguageModel` 实例，不需要全局 Bean。每次调用创建一个轻量代理（无网络连接，只做类型映射），开销可忽略。

**IPlanAgent / IDraftAgent / IValidateAgent 接口定义不变**（去 `@AiService` 注解）：

```java
// domain/qa/service/generation/agent/IPlanAgent.java
public interface IPlanAgent {
    @SystemMessage(fromResource = "prompts/generation-plan.txt")
    PlanResult plan(
        @V("documents") String documentsSummary,
        @V("targetRole") String targetRole,
        @V("targetDomain") String targetDomain,
        @V("note") String note,
        @V("questionCount") int questionCount
    );
}

// domain/qa/service/generation/agent/IDraftAgent.java
public interface IDraftAgent {
    @SystemMessage(fromResource = "prompts/generation-draft.txt")
    List<DraftItem> draft(
        @V("moduleTag") String moduleTag,
        @V("evidenceChunks") String evidenceChunks,
        @V("profileContext") String profileContext,
        @V("questionCount") int questionCount,
        @V("difficultyDistribution") String difficultyDist,
        @V("note") String note
    );
}

// domain/qa/service/generation/agent/IValidateAgent.java
public interface IValidateAgent {
    @SystemMessage(fromResource = "prompts/generation-validate.txt")
    List<ValidationResult> validate(
        @V("draftItems") String draftItemsJson
    );
}
```

### 5.4 UserLlmProvider——用户 LLM 配置读取

**职责**：从 `user_profile` 读取用户的 LLM 接入配置，未配置时抛出可读的业务异常。

**接口**：`domain/qa/service/generation/IUserLlmProvider.java`

```java
public interface IUserLlmProvider {
    UserLlmConfig getConfig(String userId);
}
```

**实现**：`domain/qa/service/generation/UserLlmProvider.java`

```java
public class UserLlmProvider implements IUserLlmProvider {

    private final IIdentityRepository identityRepo;

    public UserLlmConfig getConfig(String userId) {
        UserProfileEntity profile = identityRepo.findProfileByUserId(userId);
        if (profile == null
            || profile.getLlmBaseUrl() == null || profile.getLlmBaseUrl().isBlank()
            || profile.getLlmApiKey() == null || profile.getLlmApiKey().isBlank()
            || profile.getLlmModelName() == null || profile.getLlmModelName().isBlank()) {
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

**UserLlmConfig（值对象）**：`domain/qa/model/UserLlmConfig.java`

```java
public record UserLlmConfig(String baseUrl, String apiKey, String modelName) {}
```

用户未配置时抛出 `LLM_NOT_CONFIGURED` 错误码。`GenerationAgent` 捕获此异常，将任务状态置为 `FAILED`，`error_message` 写入明确提示："用户未配置 LLM 接入信息，请先在 Profile 中填写 base_url、api_key 和 model_name"。

---

## 六、代码在 DDD 架构中的组织

### 6.1 完整文件清单

```
backend/

  qa-agent-types/src/main/java/com/dasi/qa/agent/types/
    dto/request/qa/CreateTaskRequest.java              ← 新增：创建任务请求
    dto/response/qa/TaskStatusResponse.java            ← 新增：任务状态响应
    dto/response/qa/TaskMessageResponse.java           ← 新增：阶段消息响应
    enumeration/GenerationTaskStatus.java              ← 新增：PENDING/PLANNING/SEARCHING/DRAFTING/VALIDATING/FINALIZING/COMPLETED/FAILED
    enumeration/GenerationStage.java                   ← 新增：同上枚举（供 stage 字段用）

  qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/

    identity/repository/IIdentityRepository.java       ← 扩展：findProfileByUserId(userId) → UserProfileEntity

    qa/repository/IQaRepository.java                   ← 扩展：batchCreateQaItems、createQaSetDocumentRefs
    qa/repository/IGenerationTaskRepository.java       ← 新增：生成任务读写（crud + 状态/阶段更新）

    qa/service/generation/
      IGenerationAgent.java                            ← 接口：DAG 编排入口
      GenerationAgent.java                             ← 实现：阶段编排 + 状态机 + 失败处理
      IUserLlmProvider.java                            ← 接口：用户 LLM 配置读取
      UserLlmProvider.java                             ← 实现：读 user_profile，未配置抛异常

    qa/service/generation/agent/
      IPlanAgent.java                                  ← 接口：PLANNING 阶段 LLM 调用
      IDraftAgent.java                                 ← 接口：DRAFTING 阶段 LLM 调用
      IValidateAgent.java                              ← 接口：VALIDATING 阶段 LLM 调用
      (这三个是 LangChain4j AiServices 代理目标，运行时通过 AiServices.create() 实例化)

    qa/model/
      UserLlmConfig.java                               ← 值对象：baseUrl + apiKey + modelName
      PlanItem.java                                    ← PLANNING 输出
      PlanResult.java                                  ← PLANNING 输出封装
      DraftItem.java                                   ← DRAFTING 输出
      ValidationResult.java                            ← VALIDATING 输出

  qa-agent-infrastructure/src/main/java/com/dasi/qa/agent/infrastructure/

    persistent/entity/UserProfileEntity.java           ← 扩展：新增 llmBaseUrl / llmApiKey / llmModelName 字段
    persistent/entity/QaGenerationTaskEntity.java       ← 新增：qa_generation_task 表
    persistent/entity/QaGenerationTaskMessageEntity.java← 新增：qa_generation_task_message 表
    persistent/entity/QaSetDocumentRefEntity.java       ← 新增：qa_set_document_ref 表
    persistent/mapper/mysql/
      QaGenerationTaskMapper.java                       ← 新增
      QaGenerationTaskMessageMapper.java                ← 新增
      QaSetDocumentRefMapper.java                       ← 新增

    repository/GenerationTaskRepository.java             ← IGenerationTaskRepository 实现

  qa-agent-interfaces/src/main/java/com/dasi/qa/agent/interfaces/

    controller/QaController.java                        ← 扩展：create-task / task-status / task-messages
    consumer/GenerationTaskConsumer.java                ← 新增：Kafka 消费者

  qa-agent-application/src/main/java/com/dasi/qa/agent/application/

    resources/prompts/
      generation-plan.txt                               ← 新增：PLANNING Prompt
      generation-draft.txt                              ← 新增：DRAFTING Prompt
      generation-validate.txt                           ← 新增：VALIDATING Prompt
```

### 6.2 分层说明

| 层 | 放什么 | 不放什么 |
|----|--------|---------|
| **domain** | `IGenerationAgent` + `GenerationAgent`（DAG 编排）、`@AiService` 接口（IPlanAgent/IDraftAgent/IValidateAgent，LangChain4j 代理目标）、值对象 | LLM HTTP 调用、SQL 执行、Kafka 消费 |
| **infrastructure** | Entity + Mapper + GenerationTaskRepository 实现（MyBatis-Plus 常规操作） | DAG 编排逻辑、Prompt 组装 |
| **interfaces** | QaController 新增端点 + GenerationTaskConsumer（Kafka） | Prompt 管理、LLM 调用 |
| **application** | LangChain4j Bean 配置（ChatLanguageModel）、Prompt 模板文件 | - |

### 6.3 依赖关系

```
QaController.createTask()
  → IQaRepository.createGenerationTask()
  → Kafka send

GenerationTaskConsumer (infrastructure/consumer)
  → IGenerationAgent.execute(taskId) (domain)

GenerationAgent.execute(taskId):
  → IPlanAgent.plan()          (LangChain4j @AiService)
  → ISearchService.execute()   (V2 RAG)
  → IDraftAgent.draft()        (LangChain4j @AiService)
  → IValidateAgent.validate()  (LangChain4j @AiService)
  → IQaRepository APIs         (落库)
  → IGenerationTaskRepository  (状态更新)
```

### 6.4 user_profile 表扩展

```sql
-- 在 user_profile 表新增三个字段（新增 DDL 或写在 seed 脚本中）
ALTER TABLE user_profile
    ADD COLUMN llm_base_url VARCHAR(500) NULL,
    ADD COLUMN llm_api_key VARCHAR(255) NULL,
    ADD COLUMN llm_model_name VARCHAR(100) NULL;
```

前端 ProfilePage 已包含表单，后续 V3 收尾或 V4 时增加这三个字段的输入框。当前阶段通过数据库直接写入或 API 更新。

### 6.5 依赖关系

```
QaController.createTask()
  → IQaRepository.createGenerationTask()
  → Kafka send

GenerationTaskConsumer (infrastructure/consumer)
  → IGenerationAgent.execute(taskId) (domain)

GenerationAgent.execute(taskId):
  → IUserLlmProvider.getConfig(userId)
     → IIdentityRepository.findProfileByUserId()
     → 返回 UserLlmConfig（用户配置，未配置抛异常 → task FAILED）
  → AiServices.create(IPlanAgent.class, openAiModel)      ← 运行时创建，用 UserLlmConfig
  → IPlanAgent.plan()          (LangChain4j 结构化输出)
  → ISearchService.execute()   (V2 RAG，Java 内部调用)
  → AiServices.create(IDraftAgent.class, openAiModel)
  → IDraftAgent.draft()        (LangChain4j 结构化输出)
  → AiServices.create(IValidateAgent.class, openAiModel)
  → IValidateAgent.validate()  (LangChain4j 结构化输出)
  → IQaRepository APIs         (落库)
  → IGenerationTaskRepository  (状态更新)
```

---

## 七、与现有模块的关系

### 7.1 与 V2 RAG 的关系

GenerationAgent 的 SEARCHING 阶段直接调用 V2 RAG。调用方式有两种：

- **方案 A（推荐）**：内部 Java 调用。`GenerationAgent` 注入 `ISearchService`，直接调 `execute(request)`
- **方案 B**：HTTP 调用 `/source-document/search`。增加网络延迟和序列化开销

选方案 A——同一个 JVM 内直接调用，零网络开销，类型安全。

### 7.2 与 V1 CRUD 的关系

FINALIZING 阶段写入 `qa_set`、`qa_item`、`qa_set_document_ref` 使用的是已有的 `IQaRepository` 和 `IDocumentRepository`。不新建表，不重复造 CRUD。

### 7.3 与前端的关系

V3 不要求前端新增页面（与 V2 一致）。API 可通过 curl/Postman 验证。前端 `CreatePage` 已有的 UI 延后到 V3 收尾阶段对接（`alert("接口尚未实现")` → 调 `/qa-set/create-task`）。

---

## 八、工作流规划

### 8.1 实施顺序

| 步骤 | 内容 | 依赖 |
|------|------|------|
| 1 | 新建 `qa_generation_task` / `qa_generation_task_message` / `qa_set_document_ref` 的 Entity + Mapper + Repository | 无 |
| 2 | 新建 LangChain4j ChatLanguageModel Bean 配置 + `application-dev.yml` 扩展 | 无 |
| 3 | 新建 `GenerationTaskStatus` / `GenerationStage` 枚举 + DTO | 无 |
| 4 | 编写三个阶段的 Prompt 模板文件 | 2 |
| 5 | 实现 `IPlanAgent` / `IDraftAgent` / `IValidateAgent`（@AiService 接口） | 2, 4 |
| 6 | 实现 `IGenerationAgent` + `GenerationAgent`（DAG 编排 + 状态机） | 1, 3, 5 |
| 7 | 实现 `GenerationTaskConsumer`（Kafka 消费者） | 6 |
| 8 | 扩展 `QaController`（create-task / task-status / task-messages） | 1, 3 |
| 9 | seed.sql 中新增 `qa_generation_task` / `qa_generation_task_message` 的测试数据 | 1 |
| 10 | 集成测试：准备 1-2 篇示例 Markdown → 创建任务 → 验证各阶段输出 → 验证 `qa_set` + `qa_items` 落库 | 7, 8 |

### 8.2 预计产出

1. 可用的问答集生成链路：`POST /qa-set/create-task` → 等待 → 完成
2. 5 个标准 DAG 阶段（PLANNING → SEARCHING → DRAFTING → VALIDATING → FINALIZING）
3. 每个阶段的阶段消息可查询（`GET /qa-set/task-messages`）
4. 生成结果可追溯（`qa_item.source_chunk_ids_json` 指向 RAG 召回的证据）
5. 状态机和异常处理完备（FAILED 状态 + error_code / error_message）

---

## 九、V3 的预期和边界

### 9.1 预期产出

1. 用户选择资料 → 创建生成任务 → 系统自动生成完整问答集（题目 + 知识笔记 + 答案 + 证据引用）
2. 生成过程分阶段推进，每个阶段状态可通过 API 查询
3. 题目有证据引用、难度标签、冲突提示
4. 校验不通过的题目自动重试或丢弃
5. 生成失败的 error 信息可查询

### 9.2 V3 范围外（不做）

1. **不做 SSE/WebSocket 实时推流**：前端通过轮询 `task-status` 查看进度。推流延后到 V3 收尾
2. **不做多轮对话式题目创建**：不是聊天界面逐题交互。在 PRD 规划中，这是系统主路径——用户创建任务，系统自动完成
3. **不做 Web 搜索**：`allow_web_search` 字段已预留但 V3 不实现。通用知识补充延后
4. **不做前端对接**：API 通过集成测试验证，前端延后
5. **不做题目质量自动化评测**：V3 阶段人工抽检生成结果
6. **不做 Task 重试 UI**：后端支持重试逻辑，但无前端界面

### 9.3 边界约束

- 单次生成任务最多 100 道题目（超出返回 400 错误）
- LLM Context Window：Qwen-Plus 支持 128K tokens。单次 DRAFT 调用原则上不超过 64K tokens（预留验证和汇总空间）
- 生成耗时：15 题约 30-60 秒（取决于 LLM API 延迟和资料量），后端执行不阻塞 HTTP

---

## 十、验收与测试

### 10.1 前置条件

1. V2 RAG 已完成，`POST /source-document/search` 可正常返回结果
2. DashScope LLM API 可用
3. `qa_generation_task` / `qa_generation_task_message` / `qa_set_document_ref` 表已创建
4. seed.sql 包含至少 1 篇 Markdown 资料（已有 seed 数据）

### 10.2 验收用例

#### TC1：任务创建

| 前置 | 系统中存在 2 篇资料 |
|------|-------------------|
| 操作 | `POST /qa-set/create-task`，指定 2 个 documentIds，`requestedQuestionCount=5` |
| 预期 | 返回 `taskId`，status=PENDING |
| 验证 | `SELECT * FROM qa_generation_task WHERE id = ?` 记录存在 |

#### TC2：PLANNING 阶段

| 前置 | TC1 已通过 |
|------|-----------|
| 操作 | 等待 Kafka Consumer 消费任务 |
| 预期 | task.stage → PLANNING，生成模块规划写入 task_message |
| 验证 | `qa_generation_task_message` 中有 `stage=PLANNING` 的记录，message JSON 可解析 |

#### TC3：DRAFTING + VALIDATING

| 前置 | TC2 已通过 |
|------|-----------|
| 操作 | 等待阶段推进 |
| 预期 | 生成题目草案，经过校验，VALIDATE 通过率 > 70%（PASS + REVISE） |
| 验证 | 检查 task_messages 中 DRAFTING 和 VALIDATING 阶段的 message |

#### TC4：FINALIZING 落库

| 前置 | TC3 已通过 |
|------|-----------|
| 操作 | 等待 FINALIZING 完成 |
| 预期 | `qa_set`、`qa_item`、`qa_set_document_ref` 写入；task.status=COMPLETED; task.qa_set_id 非空 |
| 验证 | `SELECT COUNT(*) FROM qa_item WHERE qa_set_id = ?` 等价于 VALIDATING 中 PASS 的数量 |

#### TC5：证据追溯

| 前置 | TC4 已通过 |
|------|-----------|
| 操作 | 检查任意一道生成的 `qa_item` |
| 预期 | `source_chunk_ids_json` 非空且指向有效的 `document_chunk.id` |
| 验证 | `SELECT * FROM document_chunk WHERE id IN (...)` 返回真实切片记录 |

#### TC6：异常处理

| 前置 | PLANNING 阶段可执行 |
|------|-------------------|
| 操作 | 传入不存在的 documentId 创建任务 |
| 预期 | 任务进入 FAILED 状态，error_code + error_message 非空 |
| 验证 | task.status=FAILED，error_message 有明确错误描述 |

---

## 十一、需要新增的配置

### 11.1 不需要新增环境变量

V3 的 LLM 由用户自行在 `user_profile` 中配置，不走系统级环境变量。不需要新增任何 `.env` 条目或 `application-dev.yml` 配置项。

### 11.2 用户自配置接入示例

用户通过前端（或 API）在 `user_profile` 中填写：

| 字段 | 示例值 | 说明 |
|------|--------|------|
| `llm_base_url` | `https://api.deepseek.com` | DeepSeek 的 OpenAI 兼容端点 |
| `llm_api_key` | `sk-xxxx` | 用户的 DeepSeek API Key |
| `llm_model_name` | `deepseek-chat` | DeepSeek 模型名 |

或：

| 字段 | 示例值 | 说明 |
|------|--------|------|
| `llm_base_url` | `https://api.openai.com` | OpenAI 官方端点 |
| `llm_api_key` | `sk-xxxx` | 用户的 OpenAI API Key |
| `llm_model_name` | `gpt-4o` | OpenAI 模型名 |

