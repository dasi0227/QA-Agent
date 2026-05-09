# V3 GenerateAgent 实现规格

本文档是 V3 "资料 → 问答集"生成链路的完整实现规格，涵盖 DAG 结构、Scope 数据流、Agent 输入输出、Result POJO 设计和待执行修改清单。

## 1. DAG 拓扑

```
generate = sequence(
    DECIDE     (DecideAgent)
    ROUTE      (conditional: valid → CREATE, invalid → ABORT)
)

CREATE = sequence(
    PLAN       (PlanAgent)
    WRITE      (parallel: 按模块并发)
    VALIDATE   (loop: Evaluate → [Amend] → Evaluate)
    SUMMARIZE  (SummarizeAgent)
)
```

- WRITE 内部：每个 PlanItem → RagEvidenceProvider.search() → DraftAgent.draft()，模块间并行，模块内分批（每批 ≤10 题）
- VALIDATE 内部：按 10 题一批，EvaluateAgent 审校 → 分拣 PASS/REJECT/AMEND → AMEND 项进 loop(AmendAgent → EvaluateAgent)，maxIterations=2
- ABORT：从 scope 读 decideResult.reason，生成终止消息，写 FAILED 状态

## 2. Scope 数据流

Scope 是 DAG 各阶段间的唯一共享状态通道。当前定义 5 个 scope key：

| Key | 类型 | 写入阶段 | 读取阶段 | 说明 |
|---|---|---|---|---|
| `decideResult` | DecideResult | DECIDE（框架 outputKey 自动写入） | ROUTE、ABORT | 入口判定结果 |
| `planResult` | PlanResult | PLAN（手动写入） | WRITE、SUMMARIZE | 模块规划和题量分配 |
| `draftItem` | List\<DraftItem\> | WRITE（手动写入） | VALIDATE | 所有模块起草的题目 |
| `validatedResult` | List\<DraftItem\> | VALIDATE（手动写入） | SUMMARIZE | 审校修订后通过的最终题目 |
| `qaSetId` | String | SUMMARIZE（手动写入） | DAG output | 最终问答集 ID |

可选 key：

| Key | 类型 | 写入阶段 | 读取阶段 | 说明 |
|---|---|---|---|---|
| `writeFailedModules` | List\<String\> | WRITE（手动写入） | SUMMARIZE | 失败的模块名列表，无失败则不写 |

### 读写约束

- 只能写入自己阶段对应的 key，只能读取上游已写入的 key
- 读取必须做 fallback 处理：读不到时使用安全的默认值（空 List、fallback Plan 等）
- `decideResult` 由框架 `@Agent(outputKey)` 自动写入，runDecide 内无需再次 `scope.writeState`

### 已取消的旧 key

- `allEvidence`：取消，evidence 已内聚到 DraftItem.evidence 字段

## 3. Agent 接口设计

### 3.1 DecideAgent

```java
@Agent(name = "DECIDE", description = "判断生成请求是否可以进入问答集生成 DAG", outputKey = "decideResult")
DecideResult decide(@MemoryId @V("taskId") String taskId,
                    @V("userPrompt") String userPrompt);
```

### 3.2 AbortAgent

```java
@Agent(name = "ABORT", description = "根据拒绝原因生成终止消息并结束生成任务")
String abort(@MemoryId @V("taskId") String taskId,
             @V("userPrompt") String userPrompt,
             @V("reason") String reason);
```

### 3.3 PlanAgent

```java
@Agent(name = "PLANNER", description = "分析资料目录结构并规划问答集模块", outputKey = "planResult")
PlanResult plan(@MemoryId @V("taskId") String taskId,
                @V("documents") String documents,
                @V("userProfile") String userProfile,
                @V("userPrompt") String userPrompt,
                @V("questionCount") int questionCount);
```

输入说明：
- `documents`：资料目录结构文本（文件名 + 模块标签 + 摘要，不含正文全文）
- `userProfile`：UserProfileVO → JSON，包含岗位、领域、公司、answerStyle 等
- `userPrompt`：用户自由文本输入
- `questionCount`：目标题数

### 3.4 DraftAgent

返回 String（非 POJO），原因：框架的 PojoListOutputParser 在 beta24 不支持 prompt-based JSON 格式说明生成，且对集合型 POJO 解析不稳定。

```java
@Agent(name = "DRAFTER", description = "基于检索证据起草结构化问答题目")
String draft(@MemoryId @V("taskId") String taskId,
             @V("moduleTag") String moduleTag,
             @V("evidence") String evidence,
             @V("userProfile") String userProfile,
             @V("questionCount") int questionCount,
             @V("previousQuestions") String previousQuestions,
             @V("userPrompt") String userPrompt);
```

输入说明：
- `moduleTag`：模块标签，对应 PlanItem.moduleTag
- `evidence`：RagEvidenceProvider 检索结果 → JSON
- `userProfile`：UserProfileVO → JSON
- `previousQuestions`：同一模块前面批次的题目列表（仅模块内跨批次去重，不传跨模块）
- `userPrompt`：用户自由文本

Prompt 中必须包含 DraftItem 的 JSON 格式说明（因 String 返回框架不自动追加）：
```
You must answer as a JSON array. Each element must follow this structure:
{
"question": (string, 面试场景的口语化问题),
"knowledgeNote": (string, 知识笔记，供学习回顾用),
"answer": (string, 标准面试回答，逻辑清晰、有分层结构),
"tag": (string, 从候选标签池选取的标签),
"difficulty": (string, must be one of: EASY, MEDIUM, HARD),
"conflictTip": (string, 证据不足或冲突提示，无则留空),
"evidence": (string, 从提供的证据块中引用的原文句子，不要改写或捏造)
}
```

### 3.5 EvaluateAgent

```java
@Agent(name = "EVALUATOR", description = "审校题目事实准确性和证据边界")
String evaluate(@MemoryId @V("taskId") String taskId,
                @V("draftItemsJson") String draftItemsJson);
```

Prompt 中必须包含 EvaluateItem 的 JSON 格式说明：
```
You must answer as a JSON array. Each element must follow this structure:
{
"verdict": (string, must be one of: PASS, AMEND, REJECT),
"reason": (string, 判定原因),
"suggestion": (string, 修改建议，AMEND 时提供，其余留空)
}
```

### 3.6 AmendAgent

```java
@Agent(name = "AMENDER", description = "按审校意见最小修订问答题目")
String amend(@MemoryId @V("taskId") String taskId,
             @V("amendItemsJson") String amendItemsJson,
             @V("userPrompt") String userPrompt);
```

输入说明：
- `amendItemsJson`：AmendItem[] → JSON，仅包含 verdict=AMEND 的题目
- `userPrompt`：用户自由文本

Prompt 中必须包含 DraftItem 的 JSON 格式说明（同 DraftAgent）——AmendAgent 返回的是修订后的 DraftItem[]，结构一致。

### 3.7 SummarizeAgent

```java
@Agent(name = "SUMMARIZER", description = "汇总生成结果并输出完成说明")
String summarize(@MemoryId @V("taskId") String taskId,
                 @V("userPrompt") String userPrompt,
                 @V("userProfile") String userProfile,
                 @V("title") String title,
                 @V("description") String description,
                 @V("requiredCount") int requiredCount,
                 @V("generatedCount") int generatedCount,
                 @V("totalTokens") int totalTokens,
                 @V("modules") String modules,
                 @V("tags") String tags,
                 @V("qa") String qa);
```

输入说明：
- `userPrompt`、`userProfile`：上下文参考
- `title`、`description`：来自 PlanResult
- `requiredCount`：请求题数
- `generatedCount`：最终通过题数（Java 侧从 validatedResult.size() 计算）
- `totalTokens`：累计 token（Java 侧统计）
- `modules`：plan 模块名汇总（Java 侧从 PlanResult.planItems 提取，如 "Redis, JVM, Spring"）
- `tags`：题目标签汇总（Java 侧从 validatedResult 提取，如 "跳表, 持久化, GC"）
- `qa`：validatedResult → JSON，LLM 据此写文案

## 4. POJO/VO 设计

所有类统一风格：普通 class + Lombok。Result 类使用 `@Data @NoArgsConstructor @AllArgsConstructor @Builder`，字段使用 `@Description` 注解。VO 类使用 `@Data @NoArgsConstructor @AllArgsConstructor`。

### 4.0 UserProfileVO（共享输入）

`domain/agent/shared/vo/UserProfileVO.java`，从 `user_profile` 表读取后序列化为 JSON，作为多个 Agent 的 `@V("userProfile")` 输入。

```java
@Data @NoArgsConstructor @AllArgsConstructor
public class UserProfileVO {
    private String targetRole;           // 目标岗位
    private String targetDomain;         // 目标领域
    private String targetCompany;        // 目标公司
    private Boolean allowGeneralKnowledge; // 是否允许补充通用知识
    private Boolean allowWebSearch;      // 是否允许使用Web搜索
    private String answerStyle;          // 答案风格
    private String feedbackStyle;        // 反馈风格
    private String age;                  // 年龄
    private String grade;                // 年级
    private String major;                // 专业
    private String stage;                // 当前准备阶段
}
```

不包含 `llmBaseUrl`/`llmApiKey`/`llmModelName`（API 配置，非 LLM 上下文），不包含 `userId`/时间戳。

### 4.1 DecideResult

```java
@Description("是否与生成问答集相关") boolean valid;
@Description("判定原因")             String reason;
```

### 4.2 PlanResult

```java
@Description("问答集标题")     String title;
@Description("问答集概述")     String description;
@Description("模块规划列表")   List<PlanItem> planItems;
```

### 4.3 PlanItem

```java
@Description("模块标签，如 Redis、JVM")          String moduleTag;
@Description("该模块题目数")                     int questionCount;
@Description("重点考察话题，逗号分隔")             String focusTopics;
@Description("建议题目类型，逗号分隔")             String suggestedQuestionTypes;
```

注意：已去掉 `DifficultyDistribution difficultyDistribution`。`focusTopics` 和 `suggestedQuestionTypes` 已从 `List<String>` 改为逗号分隔 `String`。

### 4.4 DraftItem

```java
@Description("面试场景的口语化问题")             String question;
@Description("知识笔记，供学习回顾用")             String knowledgeNote;
@Description("标准面试回答，逻辑清晰有分层")       String answer;
@Description("题目分类标签，从候选标签池选取")     String tag;
@Description("难度，必须是 EASY / MEDIUM / HARD 之一") String difficulty;
@Description("证据不足或冲突提示，无则留空")       String conflictTip;
@Description("从证据块中引用的原文句子")           String evidence;
```

### 4.5 EvaluateItem

```java
@Description("必须是 PASS / AMEND / REJECT 之一") String verdict;
@Description("判定原因")                          String reason;
@Description("修改建议，AMEND 时提供，其余留空")    String suggestion;
```

Java 侧按数组下标对应 EvaluateItem 与 DraftItem，itemIndex 由 LLM 输出不可靠。

### 4.6 AmendItem

```java
@Description("原题对象")             DraftItem draftItem;
@Description("审校不通过的原因")     String reason;
@Description("修改建议")             String suggestion;
```

### 4.7 DifficultyDistribution（已废弃）

不再使用，从 PlanItem 中移除。每道题的具体难度由 DraftAgent 直接标注在 DraftItem.difficulty 字段。

## 5. 枚举处理策略

项目不使用 `responseFormat` API 级强制 JSON Schema。框架对 POJO 返回自动生成 prompt-based JSON 格式说明；String 返回需在 prompt 中手写格式说明。

对枚举类字段统一处理策略：
- 所有 Result POJO 中原本使用枚举的字段改为 `String` 类型
- `@Description` 中写明可选值（如 "must be one of: EASY, MEDIUM, HARD"）
- Java 侧解析后使用 `Enum.valueOf()` 校验并转换
- 涉及的枚举：`Difficulty`、`VerdictType`

VerdictType 枚举值映射：
- `PASS` — 审校通过
- `AMEND` — 需修订（旧名 REVISE，已修改）
- `REJECT` — 直接丢弃

## 6. 待执行修改清单

以下修改与 scope 设计无关，在 scope 实现完成后统一执行：

1. **note → userPrompt**：全项目重命名 `note` 为 `userPrompt`（CreateQaSetRequest、DraftAgent、AmendAgent、ValidationCoordinator、QaGenerationTaskEntity 等）
2. **PlanItem 简化**：去掉 `DifficultyDistribution` 字段，`List<String>` 改为 `String`（逗号分隔）
3. **DraftItem 简化**：`moduleTag` → `tag`，`Difficulty` → `String`，删除 `sourceChunkIds`，新增 `evidence`
4. **EvaluateItem 重命名与简化**：`ValidationResult` → `EvaluateItem`，`VerdictType` → `String`，`revisionSuggestion` → `suggestion`
5. **AmendItem 重命名**：`RevisionItem` → `AmendItem`，`revisionSuggestion` → `suggestion`
6. **Amend 循环中 AMEND 判定更新**：`VerdictType.AMEND` → 字符串 `"AMEND"`（或常量）
7. **PlanAgent.documents 数据源优化**：`getDocumentsSummary()` 改为只传目录结构（fileName + summary + moduleTags），不传正文全文
8. **DraftAgent.answerStyle 查询与合并**：从 user_profile 独立查询后作为单独参数传入
