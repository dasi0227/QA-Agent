# V3 GenerateAgent 后端落地规格

本文档是 V3 “资料 -> 问答集”生成链路的当前落地规格。它以 PRD 的业务目标为准，但实现方式以当前 Java 依赖和已验证代码为准：每次请求读取用户 LLM 配置后，动态构建本次任务专用的 LangChain4j AgenticServices DAG。

## 1. 目标与边界

V3 只负责一件事：用户选择学习资料后，自动生成结构化技术面试问答集。

必须满足：

1. 题目围绕用户选择的资料生成，保留证据边界和 `sourceChunkIds`。
2. 生成链路固定为 `Planner -> Creator -> Validator -> Summarizer`。
3. Creator 内部按模块并发，每个模块执行 `SearcherAgent -> DrafterAgent`。
4. Validator 支持 `REVISE -> Drafter -> Validator` 最多一次修订。
5. 用户模型只从 `user_profile.llm_base_url`、`llm_api_key`、`llm_model_name` 读取；缺失时抛 `LLM_NOT_CONFIGURED` 并置任务失败。
6. Controller 只扩展已有 `QaController`，不新增 Controller。
7. SSE 事件实时推送阶段消息，并持久化到 `qa_generation_task_message`。

本阶段不做真实 LLM 的 2 文档 5 题端到端验收，不做前端联调，不做语义相似度去重，不引入默认模型降级。

## 2. 当前正式实现模式

### 2.1 动态 DAG 工厂

`AgentConfiguration` 暴露 `IQaGenerationDagFactory`，不在 Spring 启动期构建全局用户模型 Agent。

运行时流程：

1. `QaController` 校验请求、获取 `userId`、创建 `SseEmitter`、提交异步任务。
2. `GenerationAgent` 创建任务记录，读取用户 LLM 配置。
3. `GenerationAgent` 用用户 LLM 构建本次任务的 `ChatModel`。
4. `GenerationAgent` 执行 Supervisor Guardrails 分类。
5. `GenerationAgent` 创建 `RagSearchTool`，当 `allowWebSearch=true` 时再创建 `InterviewExperienceSearchTool`。
6. `GenerationAgent` 调用 `IQaGenerationDagFactory.build(QaGenerationDagContext)` 动态构建 DAG。
7. DAG 执行 Planner、Creator、Validator、Summarizer 四阶段。

这种方式保证用户模型隔离，不依赖启动期全局模型。

### 2.2 AgenticServices 拓扑

顶层 DAG 使用 untyped `sequenceBuilder()`：

```text
QA_GENERATION_DAG
  PLANNER action
  CREATOR action
  VALIDATOR action
  SUMMARIZER action
```

Creator 内部使用 `parallelBuilder()`：

```text
CREATOR
  module A: SearcherAgent -> DrafterAgent
  module B: SearcherAgent -> DrafterAgent
  module C: SearcherAgent -> DrafterAgent
```

Validator 修订使用 `loopBuilder()`：

```text
VALIDATION_LOOP
  DrafterAgent redraft
  ValidatorAgent validate
exitCondition: 无 REVISE，或已完成一次修订
maxIterations: 2
```

`QaGenDag`、`QaGenModule`、`ValidationLoop` 保留为语义标记接口，帮助代码和文档表达边界；当前不强制作为 typed builder 的运行时入口。

### 2.3 LLM Agent 输出策略

`PlannerAgent` 返回 `PlanResult`。

`DrafterAgent` 和 `ValidatorAgent` 返回 JSON `String`，由 `GenerationAgent` 使用 fastjson2 解析。当前 LangChain4j 版本对集合型 POJO 输出解析存在不稳定风险，直接返回 JSON 字符串更可控。

## 3. 目录与文件约定

新增 V3 代码严格使用以下目录：

```text
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/
  model/
  repository/IAgentRepository.java
  service/generate/agentic/
  service/generate/subagent/
  service/generate/tool/

backend/qa-agent-infrastructure/src/main/java/com/dasi/qa/agent/infrastructure/
  persistent/entity/
  persistent/mapper/mysql/
  properties/
  repository/AgentRepository.java

backend/qa-agent-application/src/main/java/com/dasi/qa/agent/application/configuration/
backend/qa-agent-application/src/main/resources/prompt/
backend/qa-agent-interfaces/src/main/java/com/dasi/qa/agent/interfaces/controller/QaController.java
backend/qa-agent-types/src/main/java/com/dasi/qa/agent/types/
```

Prompt 目录固定为：

```text
backend/qa-agent-application/src/main/resources/prompt/
  generation-plan.txt
  generation-draft.txt
  generation-validate.txt
  supervisor-classify.txt
  supervisor-summary.txt
  web-search-system.txt
```

## 4. API 约定

基础路径由网关或全局配置提供：`/qa-agent/api/v1`。

V2 检索保持：

```text
POST /qa-agent/api/v1/document/source/search
```

V3 生成接口：

```text
POST /qa-agent/api/v1/qa/set/create
GET  /qa-agent/api/v1/qa/set/task-status?taskId=...
GET  /qa-agent/api/v1/qa/set/task-messages?taskId=...
```

`POST /qa/set/create` 返回 `SseEmitter`。请求线程只做参数校验、用户识别、emitter 创建和异步任务提交，不执行 LLM、RAG 或 DAG。

SSE 事件结构：

```json
{
  "taskId": "uuid",
  "stage": "PLANNER",
  "status": "PROCESSING",
  "message": "已分析资料结构，识别出 Redis、JVM 两个模块。",
  "timestamp": 1717000000000,
  "tokens": {
    "current": 1200,
    "total": 1200
  }
}
```

## 5. 数据与配置

`user_profile` 已扩展：

```sql
llm_base_url
llm_api_key
llm_model_name
```

V3 任务表：

```text
qa_generation_task
qa_generation_task_message
qa_set_document_ref
```

系统模型配置：

```yaml
qa-agent:
  llm:
    supervisor:
      base-url: ${SUPERVISOR_LLM_BASE_URL}
      api-key: ${SUPERVISOR_LLM_API_KEY}
      model: ${SUPERVISOR_LLM_MODEL}
    web-search:
      base-url: ${WEB_SEARCH_LLM_BASE_URL}
      api-key: ${WEB_SEARCH_LLM_API_KEY}
      model: ${WEB_SEARCH_LLM_MODEL}
```

`.env` 只维护：

```bash
SUPERVISOR_LLM_BASE_URL=
SUPERVISOR_LLM_API_KEY=
SUPERVISOR_LLM_MODEL=
WEB_SEARCH_LLM_BASE_URL=
WEB_SEARCH_LLM_API_KEY=
WEB_SEARCH_LLM_MODEL=
```

## 6. 阶段职责

### 6.1 Planner

职责：分析资料摘要和用户备注，输出 `PlanResult`。

要求：

1. 只规划，不生成题目。
2. `planItems.questionCount` 总和必须等于请求题数。
3. 难度分布必须满足 `easy + medium + hard = questionCount`。
4. 资料不足时仍输出计划，但把风险写入模块信息。

### 6.2 Creator

职责：按模块并发起草题目。

每个模块：

1. `SearcherAgent` 调 V2 `ISearchService` 深度检索证据。
2. `DrafterAgent` 按 10 题一批生成 JSON。
3. 模块失败时记录 `creatorFailedModules`，其他模块继续。

`allowGeneralKnowledge=false` 时，Drafter 提示必须严格基于资料证据；证据不足只能写 `conflictTip`，不能把资料外事实写成确定结论。

### 6.3 Validator

职责：审校题目是否能进入最终问答集。

规则：

1. 每 10 题一批校验。
2. `PASS` 保留。
3. `REJECT` 丢弃并计入拒绝数。
4. `REVISE` 带 `revisionSuggestion` 回到 Drafter 修订一次，再校验。
5. 最终做基础清洗：空问题、空答案、重复问题、非法 `sourceChunkIds` 不落库。
6. 本阶段只做精确问题去重；语义相似度去重放到后续增强。

### 6.4 Summarizer

职责：纯 Java 写库和生成最终完成消息，不调用 LLM。

写入：

1. `qa_set`
2. `qa_item`
3. `qa_set_document_ref`
4. `qa_generation_task.status=COMPLETED`

完成消息包含：

1. 计划题数。
2. 实际通过题数。
3. 未通过或丢弃题数。
4. 模块分布。
5. 难度分布。
6. Creator 失败模块数。
7. 累计 token。

## 7. Guardrails、Listener、Token

Guardrails 分两层：

1. Controller 基础校验：`documentIds` 非空、`requestedQuestionCount <= 100`、`note.length <= 2000`。
2. Planner 前调用 Supervisor 模型分类：判断用户要求是否与“根据学习资料生成技术面试问答集”相关；无关则任务失败。

LLM 阶段统一注册 `AgentListener`：

1. 读取 `AgentResponse.chatResponse().tokenUsage()`。
2. 累计 `AtomicInteger totalTokens`。
3. 调 Supervisor 总结阶段输出。
4. 写 SLF4J。
5. 推 SSE。
6. 写 `qa_generation_task_message`。

Java action 阶段允许 `current=0`，但不能清空累计 token。

错误处理采用 `GenerationAgent` 集中式捕获和映射：

```text
NETWORK_ERROR
RATE_LIMITED
AUTH_FAILURE
INVALID_RESPONSE
CONTENT_FILTERED
ALL_REJECTED
LLM_NOT_CONFIGURED
UNKNOWN
```

## 8. 当前验收标准

本阶段只做后端验证，不跑真实 LLM 端到端。

按顺序执行：

```bash
cd /Users/wyw/Desktop/Project/QA_Agent
rg -n "prompt/" backend/qa-agent-domain backend/qa-agent-application docs/V3_TODO.md
rg -n "/qa/set|/document/source" backend/qa-agent-interfaces docs/API.md docs/V3_TODO.md
```

说明：确认当前代码和文档使用 `resources/prompt/`、`/qa/set/...`、`/document/source/...` 这些现行路径。

```bash
cd /Users/wyw/Desktop/Project/QA_Agent/backend
mvn test
mvn compile
mvn install -DskipTests
```

```bash
cd /Users/wyw/Desktop/Project/QA_Agent
backend/script/init_sql.sh
```

启动验证：

```bash
cd /Users/wyw/Desktop/Project/QA_Agent/backend
mvn spring-boot:run -pl qa-agent-application -Dspring-boot.run.arguments="--server.port=18080 --qa-agent.xxl-job.port=19999"
```

预期：

1. 编译通过。
2. 测试通过。
3. SQL 初始化成功。
4. Spring Boot 正常启动。
5. V2 `/document/source/search` 不受影响。
6. V3 `/qa/set/create`、`/qa/set/task-status`、`/qa/set/task-messages` 映射注册成功。
