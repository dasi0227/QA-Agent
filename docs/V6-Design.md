## 1. 个人设置与密码管理、资源来源管理、批量创建题目（68254733）

### 修改内容分析

**个人设置与密码管理**

- 新增 `allowFallback` 用户配置项，控制 PlanAgent 在最终失败时是否走兜底规划，前后端 DTO 同步新增该字段
- 新增 `POST /identity/account/password` 修改密码接口，独立于账号更新，要求提供当前密码校验（不能与新密码相同）、新密码至少 8 位，新增 `PASSWORD_INVALID(40006)` 错误码
- `updateUserAccount` 不再处理密码修改，`password` 字段强制置 null，避免误改密码

**检索链路重构（资源来源管理）**

- `PlanResult.focusTopics`（逗号分隔字符串）改为 `retrievalQueries`（`List<String>`），每个检索词会自动拼接 `module` 前缀（`module + " " + topic`）。Plan prompt 相应更新
- `RagEvidenceProvider.searchByPlanItem()` 从按逗号拆分 `focusTopics` 改为遍历 `retrievalQueries` 列表逐条检索
- `WebEvidenceProvider` 从 `generate.support` 包移至 `shared` 包，同样适配 `retrievalQueries`，查询拼接改为 `company + role + module + topic` 四段式
- `DraftAgent` prompt 新增 `sourceChunkIds` 输出字段，要求 LLM 按相关性降序选择直接支撑题目的 chunkId，只从输入证据块中选择，不准编造
- `AmendAgent` prompt 移除 `sourceReliable` 输出要求，修订不再修改 `sourceChunkIds` 和 `sourceReliable`

**sourceChunkIds 精准清洗机制**

- `DraftContext` 拆分为 `allowedSourceChunkIds`（白名单，模块检索全部 chunkId）和 `fallbackSourceChunkIds`（top2 兜底）
- 新增 `cleanSourceChunkIds()`：LLM 返回的 `sourceChunkIds` 必须在白名单内 → 去重 → 最多保留 5 个；若清洗后为空，用 top2 兜底
- `AgentRepository.saveGeneratedQaSet()` 新增 `limitSourceChunkIds()` 做落库二次截断（去重、最多 5 个）
- AmendAgent 修订后明确保留原始的 `sourceReliable` 和 `sourceChunkIds`，不作修改

**批量创建题目**

- 新增 `POST /qa/item/create/batch`，接收 `{ qaSetId, questions: List<String> }`，单次 1~50 道，批量写入后一次性 UPDATE `question_count`，每道题仍异步触发 CompleteAgent
- 原 `/qa/item/create` 保留，新增 `/qa/item/create/single` 别名

**空题集创建**

- 新增 `POST /qa/set/empty`，接收 `{ title, description }`，不关联任何资料，`taskId=null`，`moduleTagsJson=[]`，`questionCount=0`
- `CompleteAgent` 处理空引用题集：`documentIds` 为空时不给 RAG 传入空列表（之前会退化为全量检索），直接返回空 evidence

**其他**

- 新增 `clean_logs.sh` 脚本
- `fallbackPlan()` 适配 `retrievalQueries` 新结构

### 重点查看文件

- `backend/qa-agent-types/.../dto/request/qa/CreateQaItemBatchRequest.java`
- `backend/qa-agent-types/.../dto/request/qa/CreateEmptyQaSetRequest.java`
- `backend/qa-agent-types/.../dto/request/identity/ChangePasswordRequest.java`
- `backend/qa-agent-domain/.../agent/service/generate/GenerateAgent.java`
- `backend/qa-agent-domain/.../agent/service/generate/model/context/DraftContext.java`
- `backend/qa-agent-domain/.../agent/service/generate/model/result/PlanResult.java`
- `backend/qa-agent-domain/.../agent/service/shared/RagEvidenceProvider.java`
- `backend/qa-agent-domain/.../agent/service/shared/WebEvidenceProvider.java`
- `backend/qa-agent-domain/.../agent/service/complete/CompleteAgent.java`
- `backend/qa-agent-domain/.../qa/service/item/QaItemService.java`
- `backend/qa-agent-domain/.../qa/service/set/QaSetService.java`
- `backend/qa-agent-infrastructure/.../repository/QaRepository.java`
- `backend/qa-agent-infrastructure/.../repository/AgentRepository.java`
- `backend/qa-agent-interfaces/.../controller/QaController.java`
- `backend/qa-agent-interfaces/.../controller/IdentityController.java`
- `backend/qa-agent-application/.../prompt/generate/generate-draft.txt`
- `backend/qa-agent-application/.../prompt/generate/generate-amend.txt`
- `backend/qa-agent-application/.../prompt/generate/generate-plan.txt`

---

## 2. 新增用户记忆模块，记录练习知识点掌握状态（6969ddf4）

### 修改内容分析

**新增两张业务表**

- `user_memory`：按 `(user_id, memory_type, target_type, target_key)` 唯一约束。字段包括 `content`（客观画像正文）、`support_count`（支撑次数）、`status`（ACTIVE/HIDDEN）、`first_seen_at`/`last_seen_at`/`hidden_at`、关联的 `latest_session_id`/`latest_qa_set_id`
- `user_memory_evidence`：记录每条记忆的每次支撑证据，关联 `practice_session_item`，保存 `questionSnapshot`、`result`、`score`、`sourceChunkIdsJson`、`evidenceSummary`

**新增 Memory 领域模块（domain/memory/）**

- 领域模型：`Memory`、`MemoryEvidence`、`IngestContext`
- 枚举：`MemoryProficientType`（AWFUL/UNCLEAR/MASTER）、`MemoryTargetType`（MODULE/BEHAVIOR/GENERAL）、`MemoryBehaviorKey`（6 种行为画像）、`ModuleTag`（固定模块池白名单）、`MemoryStatus`（ACTIVE/HIDDEN）
- 仓储接口：`IAgentRepository` 承载 MemoryAgent 沉淀所需的上下文读取、按 key 查询、写 Memory 和 evidence；`IMemoryRepository` 只保留 list/detail/hide
- 领域服务：`MemoryService` 只负责前端列表、详情和隐藏，不再承载异步沉淀入口

**新增 MemoryAgent（Agent 层）**

- `MemoryAgent`：完整执行入口，读取本轮评估上下文，调用 `InvestAgent` 提取候选画像，必要时调用 `MergeAgent` 合并正文，并负责写入 Memory 与 evidence
- `InvestAgent`：LangChain4j AI Service，prompt 定义在 `prompt/memory/memory-extract.txt`，输入本轮单题作答证据，输出最多 5 条最能体现用户发挥、最值得用户注意的候选画像
- `MergeAgent`：LangChain4j AI Service，prompt 定义在 `prompt/memory/memory-merge.txt`，输入旧 content 与新 content，输出 1-3 段合并后的客观画像
- `MemoryResultCleaner`：校验 memoryType/targetType/targetKey 合法性（含模块白名单校验）、content 非空、去重 evidenceRefs、最多 5 条

**记忆触发链路**

`AssessSaver.save()` → `mqUtil.sendMemoryMessage()` → Kafka topic `qa.memory.ingest` → `MemoryConsumer.onMemoryIngest()` → `MemoryAgent.execute(sessionId, userId)` → `InvestAgent` / `MergeAgent` → 写入 `user_memory` 和 `user_memory_evidence`

**对外接口**

- `GET /memory/list`：当前用户 ACTIVE 记忆列表（按 `lastSeenAt` 倒序）
- `GET /memory/detail?memoryId=xxx`：单条记忆 + 全部证据历史
- `POST /memory/hide`：隐藏记忆（状态改为 HIDDEN）

**生成链路接入记忆画像**

- `GenerateAgent` 在 `allowReferMemory=true` 时通过 `UserMemoryProvider` 查询当前用户 ACTIVE 记忆，将精简后的 `memoryProfileJson` 传入 `PlanContext` 和 `PlanAgent` 的 prompt
- Plan prompt 新增"长期记忆画像"输入字段和规则：只允许影响训练策略（提高薄弱模块题量、降低基础薄弱模块难度等），不能引入资料外事实

**其他**

- `WebEvidenceProvider` 正式从 `generate.support` 迁移到 `shared` 包
- `IMqUtil` 新增 `sendMemoryMessage()`
- `MqUtil` 新增 `memoryTopic` 和对应发送逻辑
- 配置文件新增 `topic-memory-ingest` / `topic-memory-ingest-dlq`
- `StringConstant` 新增 `MEMORY_JOB_ID_PREFIX`

### 重点查看文件

- `backend/qa-agent-domain/.../memory/service/MemoryService.java`
- `backend/qa-agent-domain/.../memory/repository/IMemoryRepository.java`
- `backend/qa-agent-domain/.../memory/model/`（全部枚举和模型）
- `backend/qa-agent-domain/.../agent/service/memory/MemoryAgent.java`
- `backend/qa-agent-domain/.../agent/service/memory/subagent/InvestAgent.java`
- `backend/qa-agent-domain/.../agent/service/memory/subagent/MergeAgent.java`
- `backend/qa-agent-domain/.../agent/service/memory/support/MemoryResultCleaner.java`
- `backend/qa-agent-infrastructure/.../repository/MemoryRepository.java`
- `backend/qa-agent-infrastructure/.../persistent/entity/UserMemory.java`
- `backend/qa-agent-infrastructure/.../persistent/entity/UserMemoryEvidence.java`
- `backend/qa-agent-interfaces/.../consumer/MemoryConsumer.java`
- `backend/qa-agent-interfaces/.../controller/MemoryController.java`
- `backend/qa-agent-domain/.../agent/service/assess/support/AssessSaver.java`
- `backend/qa-agent-domain/.../agent/service/generate/GenerateAgent.java`
- `backend/qa-agent-domain/.../agent/service/shared/UserMemoryProvider.java`
- `backend/qa-agent-application/.../prompt/memory/memory-extract.txt`
- `backend/qa-agent-application/.../prompt/memory/memory-merge.txt`
- `backend/qa-agent-application/.../resources/sql/table.sql`

---

## 3. 全局异常处理重构与错误码体系优化（80633beb）

### 修改内容分析

**错误码体系重构**

- `ResultCode` 新增 8 个错误码，按语义分类重组：
  - 文件/格式类：`FILE_INVALID(40010)`、`QA_SET_FILE_INVALID(40011)`
  - 业务状态类：`PRACTICE_NOT_READY(40020)`、`LLM_NOT_CONFIGURED(40030)`
  - 权限/状态类：`ACCOUNT_DISABLED(40301)`、`CONFLICT(40900)`、`RESOURCE_IN_USE(40910)`
  - Agent/外部类：`AGENT_RESPONSE_INVALID(50002)`、`EXTERNAL_SERVICE_UNAVAILABLE(50300)`
- 新增 `ResultCode.of(AgentErrorType)` 映射方法，将 7 种 AgentErrorType 映射到对应的 ResultCode
- 删除了 `DOCUMENT_REFERENCED`（用 `RESOURCE_IN_USE` 替代）

**全局异常处理器全面扩展**

原有 4 个 handler → 扩展为 9 个 handler：

| 异常类型 | 返回码 | 行为 |
|---------|--------|------|
| `ApiException` | 原 code | 使用 `exception.getMessage()` 作为前端消息 |
| `ConvertException` | 原 code | 使用 `exception.getMessage()` |
| `AgentException` | `ResultCode.of(agentErrorType)` | 使用 `exception.getMessage()` |
| `MethodArgumentNotValidException` | `BAD_REQUEST` | 提取第一条 `@Valid` 校验失败消息 |
| `BindException` | `INVALID_PARAM` | 提取第一条绑定失败消息 |
| `ConstraintViolationException` | `INVALID_PARAM` | 提取第一条约束违反消息 |
| `IllegalArgumentException` | `INVALID_PARAM` | 使用 `exception.getMessage()` |
| `HttpMessageNotReadableException` | `BAD_REQUEST` | 固定中文提示 |
| `MissingServletRequestParameterException` | `BAD_REQUEST` | 提示缺少的参数名 |
| `MethodArgumentTypeMismatchException` | `BAD_REQUEST` | 提示参数格式错误 |
| `HttpRequestMethodNotSupportedException` | `BAD_REQUEST` | 固定中文提示 |
| `Exception` | `INTERNAL_ERROR` | 通用兜底 |

**全量异常消息中文化**

涉及约 40+ 处 `throw new ApiException` 调用，全部补充了中文错误消息：

- `FORBIDDEN` 权限校验统一改为 `NOT_FOUND`（"资源不存在"），防止通过权限错误码泄露资源存在性
- 登录链路：区分"用户名或密码错误""账号已禁用""登录已失效"等场景
- 练习链路：区分"还有题目未提交""还有题目未作答"等具体状态
- 资料链路：上传、删除、格式校验均有独立中文提示
- Agent 异常消息统一脱敏，不再暴露原始异常栈（如 "XXX failed: java.lang.xxx" → "XXX失败，请稍后重试"）
- 参数校验异常现在能透传 `@Valid` 注解中的 `message` 到前端（如 "新密码至少 8 位"）

### 重点查看文件

- `backend/qa-agent-types/.../enumeration/ResultCode.java`
- `backend/qa-agent-interfaces/.../handler/GlobalExceptionHandler.java`
- `backend/qa-agent-interfaces/.../interceptor/JwtInterceptor.java`
- `backend/qa-agent-domain/.../agent/service/assist/AssistAgent.java`
- `backend/qa-agent-domain/.../agent/service/complete/CompleteAgent.java`
- `backend/qa-agent-domain/.../agent/service/feedback/FeedbackAgent.java`
- `backend/qa-agent-domain/.../agent/service/generate/GenerateAgent.java`
- `backend/qa-agent-domain/.../agent/service/memory/MemoryAgent.java`
- `backend/qa-agent-domain/.../identity/service/auth/AuthService.java`
- `backend/qa-agent-domain/.../identity/service/crud/ProfileCrudService.java`
- `backend/qa-agent-domain/.../qa/service/convert/QaSetConverter.java`
- `backend/qa-agent-domain/.../qa/service/item/QaItemService.java`
- `backend/qa-agent-domain/.../qa/service/set/QaSetService.java`
- `backend/qa-agent-domain/.../practice/service/flow/PracticeFlowService.java`
- `backend/qa-agent-infrastructure/.../repository/AgentRepository.java`
- `backend/qa-agent-infrastructure/.../repository/QaRepository.java`
- `backend/qa-agent-infrastructure/.../repository/DocumentRepository.java`
- `backend/qa-agent-infrastructure/.../repository/PracticeRepository.java`
- `backend/qa-agent-infrastructure/.../repository/MemoryRepository.java`
- `backend/qa-agent-infrastructure/.../repository/IdentityRepository.java`
- `backend/qa-agent-domain/.../agent/model/enumeration/AgentType.java`
- `backend/qa-agent-domain/.../practice/model/enumeration/PracticeFeedbackMode.java`
