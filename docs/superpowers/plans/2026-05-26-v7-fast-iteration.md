# V7 Fast Iteration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 一次性实现 V7 四个快速迭代需求：结果页可视化、指定标准答案补全、指定题目练习、语音输入答题。

**Architecture:** 后端以兼容扩展为主：`/qa/item/complete` 增加可选 `answer` 并在 `CompleteAgent` 内拆分自动补全和基于答案补全；练习创建协议增加 `itemIds`，不改表结构。前端复用现有 React Query API 层和页面结构：Repository/题目详情提供指定题目测试入口，ResultPage 用本轮 detail 和 history 聚合图表，PracticePage 增加浏览器语音输入。

**Tech Stack:** Spring Boot + DDD 多模块、LangChain4j AI Service、MyBatis-Plus、React 18、React Router、React Query、TypeScript、CSS/SVG 图表、Web Speech API。

---

## Scope And Constraints

本计划基于 [docs/V7.md](/Users/wyw/Desktop/Project/QA_Agent/docs/V7.md)。

必须遵守：

1. 不改数据库表结构。
2. 不改 Quiz 页的整本题集练习语义。
3. 不过滤 `completeStatus=PROCESSING` 的题目。
4. 指定标准答案时，Java 保存必须强制使用用户输入的 `answer`。
5. 语音输入不上传音频，不新增后端接口。
6. 项目 AGENTS 规则高于计划模板：不要在执行中 commit/push，除非用户明确要求。

---

## File Structure

### Backend

- Modify: `backend/qa-agent-types/src/main/java/com/dasi/qa/agent/types/dto/request/qa/QaItemCompleteRequest.java`
  - 增加可选 `answer`。
- Modify: `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/complete/model/context/CompleteContext.java`
  - 增加 `answer`，供 CompleteAgent 判断是否走答案驱动补全。
- Modify: `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/complete/model/result/CompleteResult.java`
  - 保持完整补全结果结构。
- Create: `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/complete/model/result/AnswerBasedCompleteResult.java`
  - 只包含 `knowledgeNote/moduleTag/difficulty/sourceReliable/sourceChunkIds`。
- Modify: `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/complete/subagent/CompleteSubAgent.java`
  - 作为自动补全 SubAgent 使用。
- Create: `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/complete/subagent/AnswerBasedCompleteSubAgent.java`
  - 用户给出标准答案时使用。
- Create: `backend/qa-agent-application/src/main/resources/prompt/complete/complete-with-answer.txt`
  - 专门约束“不得改写用户标准答案”。
- Modify: `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/complete/CompleteAgent.java`
  - 根据 `CompleteContext.answer` 选择 SubAgent。
- Modify: `backend/qa-agent-infrastructure/src/main/java/com/dasi/qa/agent/infrastructure/repository/QaRepository.java`
  - `markQaItemCompleteProcessing()` 保存用户指定 answer。
- Modify: `backend/qa-agent-infrastructure/src/main/java/com/dasi/qa/agent/infrastructure/repository/AgentRepository.java`
  - `getCompleteContext()` 读取当前 item answer；`saveCompleteResult()` 继续统一保存。

- Modify: `backend/qa-agent-types/src/main/java/com/dasi/qa/agent/types/dto/request/practice/PracticeInitRequest.java`
  - 增加 `List<String> itemIds`。
- Modify: `backend/qa-agent-types/src/main/java/com/dasi/qa/agent/types/dto/request/practice/PracticeRestartRequest.java`
  - 增加 `List<String> itemIds`。
- Modify: `backend/qa-agent-types/src/main/java/com/dasi/qa/agent/types/dto/response/practice/PracticeSessionResponse.java`
  - 增加 `perfectCount`。
- Modify: `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/practice/service/flow/PracticeFlowService.java`
  - restart 时透传 `itemIds`。
- Modify: `backend/qa-agent-infrastructure/src/main/java/com/dasi/qa/agent/infrastructure/repository/PracticeRepository.java`
  - 支持 `itemIds` 优先选题，并让 history 返回 `perfectCount`。

### Frontend

- Modify: `frontend/src/lib/api/types.ts`
  - `RetryCompleteQuestionItemInput.answer?`、`StartPracticeInput.itemIds?`、`PracticeFlowSession.perfectCount`。
- Modify: `frontend/src/lib/api/hooks.ts`
  - normalize `perfectCount`；补全 mutation 传 `answer`；start/restart 传 `itemIds`。
- Modify: `frontend/src/pages/QuestionPage.tsx`
  - 补全弹窗增加标准答案输入；题目详情和列表增加开始测试入口；列表支持勾选多题开始测试。
- Modify: `frontend/src/pages/ResultPage.tsx`
  - 增加数据聚合、五维堆叠条、模块表现条形图、题目矩阵、历史趋势。
- Modify: `frontend/src/styles/pages/result.css`
  - 增加 V7 图表样式。
- Create: `frontend/src/hooks/useSpeechInput.ts`
  - 封装 Web Speech API。
- Create: `frontend/src/components/practice/VoiceAnswerButton.tsx`
  - 语音按钮与识别状态。
- Modify: `frontend/src/components/practice/QuestionWorkspace.tsx`
  - 接收语音按钮区域或内置 `VoiceAnswerButton`。
- Modify: `frontend/src/pages/PracticePage.tsx`
  - 语音识别结果写入 `answer`。
- Modify: `frontend/src/styles/pages/practice.css`
  - 增加语音按钮状态样式。

### Docs

- Modify: `docs/API.md`
  - 记录 `/qa/item/complete.answer`、`PracticeInitRequest.itemIds`、`PracticeRestartRequest.itemIds`、`PracticeSessionResponse.perfectCount`。
- Modify: `docs/V7.md`
  - 若实现中字段命名与设计不一致，必须同步修订。

---

## Task 1: Backend Complete API Adds Optional Standard Answer

**Files:**
- Modify: `backend/qa-agent-types/src/main/java/com/dasi/qa/agent/types/dto/request/qa/QaItemCompleteRequest.java`
- Modify: `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/complete/model/context/CompleteContext.java`
- Modify: `backend/qa-agent-infrastructure/src/main/java/com/dasi/qa/agent/infrastructure/repository/QaRepository.java`
- Modify: `backend/qa-agent-infrastructure/src/main/java/com/dasi/qa/agent/infrastructure/repository/AgentRepository.java`

- [ ] **Step 1: Extend `QaItemCompleteRequest`**

Add optional `answer`; do not add validation because empty means auto-complete.

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QaItemCompleteRequest {

    @NotBlank(message = "题目 ID 不能为空")
    private String id;

    @NotBlank(message = "问题不能为空")
    private String question;

    private String answer;
}
```

- [ ] **Step 2: Extend `CompleteContext`**

Add `answer` so the async `CompleteAgent.execute(qaItemId, userId)` can recover the requested answer from DB.

```java
private String answer;
```

- [ ] **Step 3: Save requested answer while marking PROCESSING**

In `QaRepository.markQaItemCompleteProcessing(...)`, normalize `request.getAnswer()` and persist it with the question.

Use this exact behavior:

```java
String question = request.getQuestion().trim();
String answer = request.getAnswer() == null ? "" : request.getAnswer().trim();
LocalDateTime now = LocalDateTime.now();
int updated = qaItemMapper.update(null,
        new LambdaUpdateWrapper<QaItem>()
                .set(QaItem::getQuestion, question)
                .set(QaItem::getAnswer, answer)
                .set(QaItem::getCompleteStatus, CompleteStatus.PROCESSING.name())
                .set(QaItem::getUpdatedAt, now)
                .eq(QaItem::getId, request.getId())
                .eq(QaItem::getUserId, userId)
                .ne(QaItem::getCompleteStatus, CompleteStatus.PROCESSING.name()));
```

Then update the returned object:

```java
item.setQuestion(question);
item.setAnswer(answer);
item.setKnowledgeNote("");
item.setDifficulty("");
item.setKeywords("");
item.setHint("");
item.setModuleTag("");
item.setSourceChunkIdsJson("[]");
item.setSourceReliable(Boolean.TRUE);
item.setCompleteStatus(CompleteStatus.PROCESSING.name());
item.setUpdatedAt(now);
```

- [ ] **Step 4: Read answer into complete context**

In `AgentRepository.getCompleteContext(...)`, add:

```java
.answer(qaItem.getAnswer())
```

- [ ] **Step 5: Compile backend quickly**

Run:

```bash
cd /Users/wyw/Desktop/Project/QA_Agent/backend && mvn -DskipTests package
```

Expected: build reaches `BUILD SUCCESS`. If it fails because later tasks are not done yet, record the compile error and continue only if it is caused by the not-yet-created result/subagent classes.

---

## Task 2: Split CompleteAgent Into Auto And Answer-Based SubAgents

**Files:**
- Modify: `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/complete/subagent/CompleteSubAgent.java`
- Create: `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/complete/subagent/AnswerBasedCompleteSubAgent.java`
- Create: `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/complete/model/result/AnswerBasedCompleteResult.java`
- Create: `backend/qa-agent-application/src/main/resources/prompt/complete/complete-with-answer.txt`
- Modify: `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/agent/service/complete/CompleteAgent.java`

- [ ] **Step 1: Keep `CompleteSubAgent` as auto-complete path**

No signature change is required. Keep:

```java
String complete(@V("question") String question,
                @V("evidence") String evidence,
                @V("userProfile") String userProfile,
                @V("answerStyle") String answerStyle,
                @V("retryHint") String retryHint);
```

- [ ] **Step 2: Create answer-based result DTO**

Create `AnswerBasedCompleteResult.java`:

```java
package com.dasi.qa.agent.domain.agent.service.complete.model.result;

import dev.langchain4j.model.output.structured.Description;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerBasedCompleteResult {

    @Description("复习知识笔记，必须围绕用户指定标准答案提炼")
    private String knowledgeNote;

    @Description("题目分类标签，从候选标签池选取 1-2 个，逗号分隔")
    private String moduleTag;

    @Description("题目难度，必须是 EASY / MEDIUM / HARD 之一")
    private String difficulty;

    @Description("资料证据是否足以支撑用户指定标准答案")
    private Boolean sourceReliable;

    @Description("来源切片 ID 列表")
    private List<String> sourceChunkIds;
}
```

- [ ] **Step 3: Create `AnswerBasedCompleteSubAgent`**

```java
package com.dasi.qa.agent.domain.agent.service.complete.subagent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AnswerBasedCompleteSubAgent {

    @SystemMessage(fromResource = "prompt/complete/complete-with-answer.txt")
    @UserMessage("""
            用户问题：{{question}}
            用户指定标准答案：{{answer}}
            RAG 证据：{{evidence}}
            用户资料：{{userProfile}}
            答案风格：{{answerStyle}}

            输出要求：
            1. 只输出一个合法 JSON 对象，以 { 开头，以 } 结尾。
            2. 不要输出 Markdown，不要使用 ```json 代码块。
            3. 不要输出解释文字或任何非 JSON 内容。
            4. 必须包含所有指定字段，数组字段无内容时输出 []，字符串字段无内容时输出 ""。
            5. 不允许添加未定义字段，尤其不要输出 answer 字段。

            重试提示（首次为空）：{{retryHint}}
            """)
    String complete(@V("question") String question,
                    @V("answer") String answer,
                    @V("evidence") String evidence,
                    @V("userProfile") String userProfile,
                    @V("answerStyle") String answerStyle,
                    @V("retryHint") String retryHint);
}
```

- [ ] **Step 4: Create `complete-with-answer.txt`**

Create `backend/qa-agent-application/src/main/resources/prompt/complete/complete-with-answer.txt`:

```text
你是 QA_Agent 的手动题目补全 Agent。用户已经提供了人工指定的标准答案，你的职责是围绕这个标准答案补齐题目资产的辅助字段。

边界（你不做什么）：
1. 不改写用户问题。
2. 不改写、替换、润色或纠正用户指定标准答案。
3. 不输出 answer 字段。
4. 不生成 keywords，不生成 hint。
5. 不编造资料中的具体参数、版本行为、公司真题或源码细节。
6. 不输出 Markdown，不输出解释文字。

候选标签池（moduleTag 必须从中选取 1-2 个最符合的，逗号分隔，禁止自创）：
JavaSE,OOP,JVM,IO,JUC,JCF,MCP,SKILL,AGENT,Harness,SpringAI,LangChain4J,SpringFramework,SpringMVC,SpringBoot,SpringCloud,MyBatis,MySQL,PostgreSQL,Redis,MQ,Linux,Docker,Maven,Git,Zookeeper,Elasticsearch,K8s,Grafana,分布式,高并发,微服务,设计模式,数据结构与算法,计算机网络,操作系统,测试,运维,安全

输入含义：
- 用户问题：用户手动维护的面试问题。
- 用户指定标准答案：人工裁定的 ground truth，你必须尊重它，不得修改。
- RAG 证据：题集关联资料检索结果，每项包含 chunkId、content、summary、headingPath（章节路径）。
- 用户资料：用户岗位、领域、目标公司等背景，用于控制难度和表达。
- 答案风格：用户偏好的口头回答风格，只用于 knowledgeNote 的表达参考。
- 重试提示：上一次调用失败的错误信息，首次为空；非空时优先修正输出格式。

生成规则：
1. knowledgeNote 面向复习，围绕用户标准答案提炼概念、机制、关键步骤、边界和易混淆点。
2. moduleTag 从候选标签池选取 1-2 个最符合标签，逗号分隔。
3. difficulty 只能是 EASY / MEDIUM / HARD。
4. sourceReliable 为 true 表示 RAG 证据足以支撑用户指定标准答案；证据弱、无证据或用户答案主要来自通用知识时必须为 false。
5. sourceChunkIds 只填写实际支撑用户标准答案的证据 chunkId；无可靠证据时输出 []。

示例输出：
{
  "knowledgeNote": "RDB 关注快照，适合备份和快速恢复；AOF 关注命令日志，适合降低数据丢失风险。复习时要从记录对象、刷盘策略、恢复速度、文件体积和数据完整性几个角度对比。",
  "moduleTag": "Redis",
  "difficulty": "MEDIUM",
  "sourceReliable": true,
  "sourceChunkIds": ["chunk-id"]
}

字符串值内的双引号必须用反斜杠转义为 \"，禁止输出未转义双引号。
输出 JSON 结构（所有字段必须存在；字符串无内容时输出 ""；数组无内容时输出 []；sourceReliable 必须是 true 或 false）：
{
  "knowledgeNote": "string",
  "moduleTag": "string，从候选标签池选取 1-2 个，逗号分隔",
  "difficulty": "EASY|MEDIUM|HARD",
  "sourceReliable": true,
  "sourceChunkIds": ["string"]
}
```

- [ ] **Step 5: Add branch logic in `CompleteAgent`**

In `CompleteAgent.doComplete(...)`, build both subagents and branch by `context.getAnswer()`.

Use imports:

```java
import com.dasi.qa.agent.domain.agent.service.complete.model.result.CompleteResultWithoutAnswer;
import com.dasi.qa.agent.domain.agent.service.complete.subagent.CompleteSubAgentWithAnswer;
import org.springframework.util.StringUtils;
```

Implement helpers:

```java
private CompleteResult doComplete(CompleteContext context, String userId) {
    ChatModel userModel = userLlmModelProvider.getUserLlmModel(userId);
    List<RagEvidenceProvider.RagEvidenceItem> ragEvidenceItems = context.getDocumentIds() == null || context.getDocumentIds().isEmpty()
            ? List.of()
            : ragEvidenceProvider.searchByQuestion(userId, context.getDocumentIds(), context.getQuestion());
    String evidence = jsonUtil.toJsonString(ragEvidenceItems);
    if (StringUtils.hasText(context.getAnswer())) {
        AnswerBasedCompleteSubAgent answerAgent = AiServices.builder(AnswerBasedCompleteSubAgent.class)
                .chatModel(userModel)
                .build();
        return doCompleteWithAnswer(answerAgent, context, evidence);
    }
    CompleteSubAgent completeAgent = AiServices.builder(CompleteSubAgent.class)
            .chatModel(userModel)
            .build();
    return doAutoComplete(completeAgent, context, evidence);
}
```

Add:

```java
private CompleteResult doCompleteWithAnswer(AnswerBasedCompleteSubAgent completeAgent, CompleteContext context, String evidence) {
    String retryHint = "";
    for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
        try {
            String response = completeAgent.complete(
                    context.getQuestion(),
                    context.getAnswer(),
                    evidence,
                    jsonUtil.toJsonString(context.getUserProfile()),
                    context.getAnswerStyle(),
                    retryHint
            );
            AnswerBasedCompleteResult result = jsonUtil.parseJsonObject(response, AnswerBasedCompleteResult.class);
            return CompleteResult.builder()
                    .answer(context.getAnswer().trim())
                    .knowledgeNote(result.getKnowledgeNote())
                    .moduleTag(result.getModuleTag())
                    .difficulty(result.getDifficulty())
                    .sourceReliable(result.getSourceReliable())
                    .sourceChunkIds(result.getSourceChunkIds())
                    .build();
        } catch (Exception exception) {
            retryHint = exception.getMessage();
            if (attempt == MAX_RETRY) {
                throw new CompleteException(AgentErrorType.fromException(exception), "题目基于标准答案补全返回格式异常，请重试");
            }
            log.warn("【题目创建补全】AnswerBasedCompleteSubAgent 调用失败，重试: attempt={}, qaItemId={}", attempt + 1, context.getQaItemId(), exception);
        }
    }
    throw new CompleteException(AgentErrorType.INVALID_RESPONSE, "题目基于标准答案补全未返回有效结果，请重试");
}
```

Rename old retry loop to `doAutoComplete(...)` and keep behavior unchanged.

- [ ] **Step 6: Compile backend**

Run:

```bash
cd /Users/wyw/Desktop/Project/QA_Agent/backend && mvn -DskipTests package
```

Expected: `BUILD SUCCESS`.

---

## Task 3: Backend Practice Session Supports Explicit Item IDs

**Files:**
- Modify: `backend/qa-agent-types/src/main/java/com/dasi/qa/agent/types/dto/request/practice/PracticeInitRequest.java`
- Modify: `backend/qa-agent-types/src/main/java/com/dasi/qa/agent/types/dto/request/practice/PracticeRestartRequest.java`
- Modify: `backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/practice/service/flow/PracticeFlowService.java`
- Modify: `backend/qa-agent-infrastructure/src/main/java/com/dasi/qa/agent/infrastructure/repository/PracticeRepository.java`

- [ ] **Step 1: Add `itemIds` to request DTOs**

In both request classes:

```java
private List<String> itemIds;
```

Add import:

```java
import java.util.List;
```

- [ ] **Step 2: Pass `itemIds` through restart**

In `PracticeFlowService.restart(...)`, add:

```java
.itemIds(request.getItemIds())
```

to the `PracticeInitRequest.builder()`.

- [ ] **Step 3: Add repository helpers for normalized IDs**

In `PracticeRepository`, add imports:

```java
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
```

Add helper:

```java
private List<String> normalizedItemIds(PracticeInitRequest request) {
    if (request.getItemIds() == null) {
        return List.of();
    }
    return request.getItemIds().stream()
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(StringUtils::hasText)
            .collect(Collectors.collectingAndThen(
                    Collectors.toCollection(LinkedHashSet::new),
                    List::copyOf
            ));
}
```

- [ ] **Step 4: Make `countPracticeItems()` use explicit IDs**

Replace existing count logic:

```java
List<String> itemIds = normalizedItemIds(request);
if (!itemIds.isEmpty()) {
    return itemIds.size();
}
return Math.toIntExact(qaItemMapper.selectCount(startPracticeItemWrapper(request, userId)));
```

- [ ] **Step 5: Make `startPracticeItems()` use explicit IDs first**

Replace the body:

```java
private List<QaItem> startPracticeItems(PracticeInitRequest request, String userId) {
    List<String> itemIds = normalizedItemIds(request);
    if (itemIds.isEmpty()) {
        return qaItemMapper.selectList(startPracticeItemWrapper(request, userId));
    }
    List<QaItem> items = qaItemMapper.selectList(new LambdaQueryWrapper<QaItem>()
            .eq(QaItem::getQaSetId, request.getQaSetId())
            .eq(QaItem::getUserId, userId)
            .in(QaItem::getId, itemIds));
    if (items.size() != itemIds.size()) {
        throw new ApiException(ResultCode.NOT_FOUND, "题目不存在");
    }
    Map<String, QaItem> itemMap = items.stream().collect(Collectors.toMap(QaItem::getId, Function.identity()));
    return itemIds.stream()
            .map(itemMap::get)
            .filter(Objects::nonNull)
            .toList();
}
```

Do not filter `completeStatus`.

- [ ] **Step 6: Keep random mode behavior**

`initPractice(...)` already shuffles `qaItems` when `mode == RANDOM`. Keep that code after `startPracticeItems(...)`. This means explicit `itemIds` preserve incoming order for `SEQUENTIAL` and shuffle for `RANDOM`.

- [ ] **Step 7: Compile backend**

Run:

```bash
cd /Users/wyw/Desktop/Project/QA_Agent/backend && mvn -DskipTests package
```

Expected: `BUILD SUCCESS`.

---

## Task 4: Backend Practice History Returns `perfectCount`

**Files:**
- Modify: `backend/qa-agent-types/src/main/java/com/dasi/qa/agent/types/dto/response/practice/PracticeSessionResponse.java`
- Modify: `backend/qa-agent-infrastructure/src/main/java/com/dasi/qa/agent/infrastructure/repository/PracticeRepository.java`

- [ ] **Step 1: Add field**

In `PracticeSessionResponse`:

```java
private Integer perfectCount;
```

Place it before `correctCount` to match `PracticeStateResponse`.

- [ ] **Step 2: Verify mapping fills the field**

`PracticeRepository.queryPracticeHistory(...)` uses `toResponse(session, PracticeSessionResponse.class)`. If the common mapper copies matching fields, no explicit code is needed.

If compile or runtime inspection shows `perfectCount` remains null, add explicit mapping in the same branch where `PracticeRepository.enrich(...)` handles session fields:

```java
sessionResponse.setPerfectCount(session.getPerfectCount());
```

- [ ] **Step 3: Compile backend**

Run:

```bash
cd /Users/wyw/Desktop/Project/QA_Agent/backend && mvn -DskipTests package
```

Expected: `BUILD SUCCESS`.

---

## Task 5: Frontend API Types And Hooks

**Files:**
- Modify: `frontend/src/lib/api/types.ts`
- Modify: `frontend/src/lib/api/hooks.ts`

- [ ] **Step 1: Update TypeScript types**

In `RetryCompleteQuestionItemInput`:

```ts
export type RetryCompleteQuestionItemInput = {
    id: string;
    question: string;
    answer?: string;
};
```

In `PracticeFlowSession`, add:

```ts
perfectCount: number;
```

In `StartPracticeInput`:

```ts
export type StartPracticeInput = {
    qaSetId: string;
    mode: PracticeMode;
    feedbackMode: PracticeFeedbackMode;
    selectedModule?: string;
    itemIds?: string[];
};
```

- [ ] **Step 2: Normalize `perfectCount`**

In `normalizePracticeFlowSession(...)`, add:

```ts
perfectCount: toNumberValue(pick(raw, "perfectCount", "perfect_count")),
```

Place before `correctCount`.

- [ ] **Step 3: Keep mutation payloads passthrough**

`useStartPracticeMutation()` and `useRestartPracticeMutation()` already send `body: input`. No special transformation is needed once type includes `itemIds`.

`useRetryCompleteQuestionItemMutation()` already sends `body: input`. No special transformation is needed once type includes `answer`.

- [ ] **Step 4: Typecheck frontend**

Run:

```bash
cd /Users/wyw/Desktop/Project/QA_Agent/frontend && npm run typecheck
```

Expected: typecheck passes or fails only because later UI tasks have not been updated yet.

---

## Task 6: Frontend QuestionPage Completes With Optional Standard Answer

**Files:**
- Modify: `frontend/src/pages/QuestionPage.tsx`
- Modify: `frontend/src/styles/pages/repository.css`

- [ ] **Step 1: Add answer draft state**

In `QuestionPage` state section:

```ts
const [completeAnswerDraft, setCompleteAnswerDraft] = useState("");
```

- [ ] **Step 2: Initialize and reset answer draft**

In `openCompleteDialog()`:

```ts
setCompleteQuestionDraft(activeItem.question);
setCompleteAnswerDraft(activeItem.answer || "");
setCompleteDialogOpen(true);
```

In `closeCompleteDialog()`:

```ts
setCompleteQuestionDraft("");
setCompleteAnswerDraft("");
setCompleteDialogOpen(false);
```

In the `useEffect` that reacts to `activeItemId`, add:

```ts
setCompleteAnswerDraft("");
```

- [ ] **Step 3: Send answer to complete endpoint**

In `retryCompleteItem()`:

```ts
const answer = completeAnswerDraft.trim();
await retryCompleteQuestionItemMutation.mutateAsync({
    id: activeItemId,
    question,
    answer: answer || undefined,
});
```

- [ ] **Step 4: Add standard answer field to dialog**

Find the existing “重新补全” dialog JSX. Add a textarea below question:

```tsx
<Field label="标准答案（可选）" hint="填写后 AI 不会改写该答案，只补充知识点、难度、模块和来源。清空则由 AI 自动生成答案。">
    <TextArea
        value={completeAnswerDraft}
        onChange={(event) => setCompleteAnswerDraft(event.target.value)}
        rows={7}
        placeholder="可以粘贴你认可的标准答案；留空则由 AI 自动补全答案"
    />
</Field>
```

- [ ] **Step 5: Typecheck frontend**

Run:

```bash
cd /Users/wyw/Desktop/Project/QA_Agent/frontend && npm run typecheck
```

Expected: pass.

---

## Task 7: Frontend Start Practice From Selected Question Items

**Files:**
- Modify: `frontend/src/pages/QuestionPage.tsx`
- Modify: `frontend/src/lib/api/hooks.ts` if hook invalidation needs adjustment.
- Modify: `frontend/src/styles/pages/repository.css`

- [ ] **Step 1: Import start practice mutation and checkbox icons**

In `QuestionPage.tsx` imports:

```ts
import { useStartPracticeMutation } from "@/lib/api/hooks";
```

Also add icons:

```ts
import { Play, CheckSquare, Square } from "lucide-react";
```

Merge with the existing lucide import.

- [ ] **Step 2: Add selection and mutation state**

```ts
const startPracticeMutation = useStartPracticeMutation();
const [selectedPracticeItemIds, setSelectedPracticeItemIds] = useState<string[]>([]);
```

- [ ] **Step 3: Add selection helpers**

```ts
const selectedPracticeItemSet = useMemo(() => new Set(selectedPracticeItemIds), [selectedPracticeItemIds]);

const togglePracticeItem = (itemId: string) => {
    setSelectedPracticeItemIds((current) => (
        current.includes(itemId)
            ? current.filter((id) => id !== itemId)
            : [...current, itemId]
    ));
};

const clearPracticeSelection = () => setSelectedPracticeItemIds([]);

const startPracticeWithItems = async (itemIds: string[]) => {
    if (!qaSetId || itemIds.length === 0) return;
    const detail = await startPracticeMutation.mutateAsync({
        qaSetId,
        mode: "SEQUENTIAL",
        feedbackMode: "ITEM_BY_ITEM",
        itemIds,
    });
    navigate(`/practice/${detail.session.id}`);
};
```

- [ ] **Step 4: Add current item start button**

In the right-side question info/actions card, under existing edit/recomplete actions, add:

```tsx
<BaseButton
    variant="soft"
    leadingIcon={<Play size={16} />}
    onClick={() => activeItemId && startPracticeWithItems([activeItemId])}
    disabled={!activeItemId || startPracticeMutation.isPending}
>
    开始测试
</BaseButton>
```

- [ ] **Step 5: Add checkbox controls to item list**

In the question list row/button rendering, add a small checkbox button that does not trigger row navigation:

```tsx
<button
    type="button"
    className="question-list__select"
    onClick={(event) => {
        event.stopPropagation();
        togglePracticeItem(item.id);
    }}
    aria-label={selectedPracticeItemSet.has(item.id) ? "取消选择题目" : "选择题目"}
>
    {selectedPracticeItemSet.has(item.id) ? <CheckSquare size={16} /> : <Square size={16} />}
</button>
```

Use the actual list item variable name in the file.

- [ ] **Step 6: Add batch start action**

Near the list toolbar or list header, add:

```tsx
{selectedPracticeItemIds.length ? (
    <div className="question-practice-toolbar">
        <span>已选择 {selectedPracticeItemIds.length} 题</span>
        <BaseButton
            variant="primary"
            leadingIcon={<Play size={16} />}
            onClick={() => startPracticeWithItems(selectedPracticeItemIds)}
            disabled={startPracticeMutation.isPending}
        >
            开始测试
        </BaseButton>
        <BaseButton variant="ghost" onClick={clearPracticeSelection}>
            清空
        </BaseButton>
    </div>
) : null}
```

- [ ] **Step 7: Add CSS**

In `frontend/src/styles/pages/repository.css` add:

```css
.question-practice-toolbar {
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 12px;
    padding: 10px 12px;
    border: 1px solid rgba(185, 142, 84, 0.24);
    border-radius: 8px;
    background: rgba(255, 249, 237, 0.78);
}

.question-practice-toolbar span {
    color: rgba(47, 42, 36, 0.72);
    font-size: 13px;
}

.question-list__select {
    display: inline-grid;
    place-items: center;
    width: 30px;
    height: 30px;
    border: 1px solid rgba(47, 42, 36, 0.12);
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.72);
    color: #8a6f47;
}
```

Adjust class names if the existing list uses different naming, but keep the same visual behavior.

- [ ] **Step 8: Typecheck frontend**

Run:

```bash
cd /Users/wyw/Desktop/Project/QA_Agent/frontend && npm run typecheck
```

Expected: pass.

---

## Task 8: ResultPage Data Aggregation Helpers

**Files:**
- Modify: `frontend/src/pages/ResultPage.tsx`

- [ ] **Step 1: Import practice history hook**

Change import:

```ts
import { usePracticeDetailQuery, usePracticeHistoryQuery, useRestartPracticeMutation } from "@/lib/api/hooks";
```

- [ ] **Step 2: Add result order and count helpers**

Near `resultMeta(...)`, add:

```ts
const RESULT_KEYS = ["perfect", "correct", "deficient", "wrong", "unknown"] as const;

type ResultTone = typeof RESULT_KEYS[number];

const resultLabels: Record<ResultTone, string> = {
    perfect: "完美",
    correct: "正确",
    deficient: "缺漏",
    wrong: "错误",
    unknown: "不会",
};

function resultTone(value?: string, unknown?: boolean): ResultTone | "pending" {
    const raw = unknown ? "UNKNOWN" : (value || "").toUpperCase();
    if (raw === "PERFECT") return "perfect";
    if (raw === "CORRECT") return "correct";
    if (raw === "DEFICIENT") return "deficient";
    if (raw === "WRONG") return "wrong";
    if (raw === "UNKNOWN") return "unknown";
    return "pending";
}
```

- [ ] **Step 3: Add module stats helper**

```ts
type ModuleStat = {
    moduleTag: string;
    total: number;
    avgScore: number;
    perfect: number;
    correct: number;
    deficient: number;
    wrong: number;
    unknown: number;
};

function buildModuleStats(items: PracticeFlowItem[]): ModuleStat[] {
    const map = new Map<string, { total: number; scoreTotal: number; perfect: number; correct: number; deficient: number; wrong: number; unknown: number }>();
    items.forEach((item) => {
        const key = item.moduleTag || "未分类";
        const stat = map.get(key) ?? { total: 0, scoreTotal: 0, perfect: 0, correct: 0, deficient: 0, wrong: 0, unknown: 0 };
        stat.total += 1;
        stat.scoreTotal += item.score ?? 0;
        const tone = resultTone(item.result, item.unknown);
        if (tone !== "pending") {
            stat[tone] += 1;
        }
        map.set(key, stat);
    });
    return [...map.entries()]
        .map(([moduleTag, stat]) => ({
            moduleTag,
            total: stat.total,
            avgScore: stat.total ? Math.round(stat.scoreTotal / stat.total) : 0,
            perfect: stat.perfect,
            correct: stat.correct,
            deficient: stat.deficient,
            wrong: stat.wrong,
            unknown: stat.unknown,
        }))
        .sort((a, b) => a.avgScore - b.avgScore || b.total - a.total);
}
```

- [ ] **Step 4: Query history**

Inside `ResultPage()` after `session` is defined:

```ts
const historyQuery = usePracticeHistoryQuery(session?.qaSetId, { enabled: Boolean(session?.qaSetId) });
const history = historyQuery.data ?? [];
```

- [ ] **Step 5: Derive chart data**

Inside `ResultPage()` after status guards or before return, add:

```ts
const distribution = {
    perfect: session.perfectCount ?? 0,
    correct: session.correctCount ?? 0,
    deficient: session.deficientCount ?? 0,
    wrong: session.wrongCount ?? 0,
    unknown: session.unknownCount ?? 0,
};
const distributionTotal = RESULT_KEYS.reduce((sum, key) => sum + distribution[key], 0) || 1;
const moduleStats = buildModuleStats(items);
const trend = history
    .filter((record) => record.status === "FINISHED")
    .slice(-8);
```

- [ ] **Step 6: Typecheck frontend**

Run:

```bash
cd /Users/wyw/Desktop/Project/QA_Agent/frontend && npm run typecheck
```

Expected: pass.

---

## Task 9: ResultPage Visual Components And CSS

**Files:**
- Modify: `frontend/src/pages/ResultPage.tsx`
- Modify: `frontend/src/styles/pages/result.css`

- [ ] **Step 1: Add distribution strip JSX**

After score hero and before existing `result-distribution`, add:

```tsx
<section className="result-visual-grid" aria-label="结果可视化">
    <article className="result-chart-panel result-chart-panel--wide">
        <div className="result-section-head">
            <h2>结果结构</h2>
            <span className="result-soft-badge">{distributionTotal} 题</span>
        </div>
        <div className="result-stack-bar" aria-label="五维结果分布">
            {RESULT_KEYS.map((key) => (
                <span
                    key={key}
                    className={`result-stack-bar__seg result-stack-bar__seg--${key}`}
                    style={{ width: `${(distribution[key] / distributionTotal) * 100}%` }}
                    title={`${resultLabels[key]} ${distribution[key]} 题`}
                />
            ))}
        </div>
        <div className="result-stack-legend">
            {RESULT_KEYS.map((key) => (
                <span key={key}><i className={`result-legend-dot result-legend-dot--${key}`} />{resultLabels[key]} {distribution[key]}</span>
            ))}
        </div>
    </article>
</section>
```

- [ ] **Step 2: Add module bars JSX**

Inside the same visual grid:

```tsx
<article className="result-chart-panel">
    <div className="result-section-head">
        <h2>模块表现</h2>
        <span className="result-soft-badge">{moduleStats.length} 类</span>
    </div>
    <div className="result-module-bars">
        {moduleStats.length ? moduleStats.map((stat) => (
            <div key={stat.moduleTag} className="result-module-bar">
                <div className="result-module-bar__head">
                    <strong>{stat.moduleTag}</strong>
                    <span>{stat.avgScore} 分 · {stat.total} 题</span>
                </div>
                <div className="result-module-bar__track">
                    <span style={{ width: `${Math.max(4, Math.min(100, stat.avgScore))}%` }} />
                </div>
            </div>
        )) : <div className="result-empty">暂无模块统计。</div>}
    </div>
</article>
```

- [ ] **Step 3: Add item matrix JSX**

```tsx
<article className="result-chart-panel">
    <div className="result-section-head">
        <h2>题目矩阵</h2>
        <span className="result-soft-badge">{items.length} 题</span>
    </div>
    <div className="result-question-matrix">
        {items.map((item, index) => {
            const tone = resultTone(item.result, item.unknown);
            return (
                <button
                    key={item.sessionItemId}
                    type="button"
                    className={`result-question-cell result-question-cell--${tone}`}
                    onClick={() => navigate(`/practice/${session.id || sessionId}/review?index=${index}`)}
                    title={`第 ${index + 1} 题：${item.score ?? "-"} 分`}
                >
                    {index + 1}
                </button>
            );
        })}
    </div>
</article>
```

- [ ] **Step 4: Add history trend JSX**

```tsx
<article className="result-chart-panel result-chart-panel--wide">
    <div className="result-section-head">
        <h2>历史趋势</h2>
        <span className="result-soft-badge">最近 {trend.length} 次</span>
    </div>
    {trend.length ? (
        <div className="result-trend">
            {trend.map((record, index) => (
                <div key={record.id} className="result-trend__point">
                    <span style={{ height: `${Math.max(8, Math.min(100, record.score ?? 0))}%` }} title={`${record.score ?? "-"} 分`} />
                    <small>{index + 1}</small>
                </div>
            ))}
        </div>
    ) : <div className="result-empty">暂无历史练习趋势。</div>}
</article>
```

- [ ] **Step 5: Add CSS**

Append to `frontend/src/styles/pages/result.css`:

```css
.result-visual-grid {
    display: grid;
    grid-template-columns: minmax(0, 1.15fr) minmax(0, 0.85fr);
    gap: 16px;
}

.result-chart-panel {
    border: 1px solid rgba(47, 42, 36, 0.1);
    border-radius: 8px;
    background: rgba(255, 252, 246, 0.86);
    padding: 18px;
    box-shadow: 0 18px 50px rgba(58, 45, 30, 0.08);
}

.result-chart-panel--wide {
    grid-column: 1 / -1;
}

.result-stack-bar {
    display: flex;
    height: 18px;
    overflow: hidden;
    border-radius: 999px;
    background: rgba(47, 42, 36, 0.08);
}

.result-stack-bar__seg--perfect,
.result-legend-dot--perfect,
.result-question-cell--perfect {
    background: #c8853b;
}

.result-stack-bar__seg--correct,
.result-legend-dot--correct,
.result-question-cell--correct {
    background: #4f8a67;
}

.result-stack-bar__seg--deficient,
.result-legend-dot--deficient,
.result-question-cell--deficient {
    background: #d7b957;
}

.result-stack-bar__seg--wrong,
.result-legend-dot--wrong,
.result-question-cell--wrong {
    background: #b55a4c;
}

.result-stack-bar__seg--unknown,
.result-legend-dot--unknown,
.result-question-cell--unknown {
    background: #7b8ca8;
}

.result-stack-legend {
    display: flex;
    flex-wrap: wrap;
    gap: 10px 14px;
    margin-top: 12px;
    color: rgba(47, 42, 36, 0.72);
    font-size: 13px;
}

.result-stack-legend span {
    display: inline-flex;
    align-items: center;
    gap: 6px;
}

.result-legend-dot {
    width: 8px;
    height: 8px;
    border-radius: 999px;
}

.result-module-bars {
    display: grid;
    gap: 14px;
}

.result-module-bar__head {
    display: flex;
    align-items: baseline;
    justify-content: space-between;
    gap: 12px;
    margin-bottom: 7px;
}

.result-module-bar__head strong {
    color: #2f2a24;
    font-size: 14px;
}

.result-module-bar__head span {
    color: rgba(47, 42, 36, 0.58);
    font-size: 12px;
}

.result-module-bar__track {
    height: 9px;
    overflow: hidden;
    border-radius: 999px;
    background: rgba(47, 42, 36, 0.08);
}

.result-module-bar__track span {
    display: block;
    height: 100%;
    border-radius: inherit;
    background: linear-gradient(90deg, #d7b957, #4f8a67);
}

.result-question-matrix {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(34px, 1fr));
    gap: 8px;
}

.result-question-cell {
    min-width: 34px;
    height: 34px;
    border: 0;
    border-radius: 8px;
    color: #fff;
    font-weight: 700;
    cursor: pointer;
}

.result-question-cell--pending {
    background: rgba(47, 42, 36, 0.24);
}

.result-trend {
    display: grid;
    grid-template-columns: repeat(auto-fit, minmax(34px, 1fr));
    align-items: end;
    height: 140px;
    gap: 10px;
    padding-top: 10px;
}

.result-trend__point {
    display: grid;
    grid-template-rows: 1fr auto;
    gap: 7px;
    height: 100%;
    text-align: center;
}

.result-trend__point span {
    align-self: end;
    display: block;
    min-height: 8px;
    border-radius: 999px 999px 4px 4px;
    background: #6f8498;
}

.result-trend__point small {
    color: rgba(47, 42, 36, 0.52);
}
```

- [ ] **Step 6: Update responsive CSS**

Add to `frontend/src/styles/responsive.css` so the visual grid remains usable on narrower screens:

```css
@media (max-width: 860px) {
  .result-visual-grid {
    grid-template-columns: 1fr;
  }
}
```

- [ ] **Step 7: Typecheck frontend**

Run:

```bash
cd /Users/wyw/Desktop/Project/QA_Agent/frontend && npm run typecheck
```

Expected: pass.

---

## Task 10: Speech Input Hook And Button

**Files:**
- Create: `frontend/src/hooks/useSpeechInput.ts`
- Create: `frontend/src/components/practice/VoiceAnswerButton.tsx`

- [ ] **Step 1: Create speech hook**

Create `frontend/src/hooks/useSpeechInput.ts`:

```ts
import { useCallback, useEffect, useRef, useState } from "react";

type SpeechRecognitionConstructor = new () => SpeechRecognition;

type SpeechRecognitionWindow = Window & {
    SpeechRecognition?: SpeechRecognitionConstructor;
    webkitSpeechRecognition?: SpeechRecognitionConstructor;
};

export function useSpeechInput(onText: (text: string) => void) {
    const recognitionRef = useRef<SpeechRecognition | null>(null);
    const [supported, setSupported] = useState(false);
    const [listening, setListening] = useState(false);
    const [error, setError] = useState("");

    useEffect(() => {
        const speechWindow = window as SpeechRecognitionWindow;
        setSupported(Boolean(speechWindow.SpeechRecognition || speechWindow.webkitSpeechRecognition));
    }, []);

    const stop = useCallback(() => {
        recognitionRef.current?.stop();
        recognitionRef.current = null;
        setListening(false);
    }, []);

    const start = useCallback(() => {
        const speechWindow = window as SpeechRecognitionWindow;
        const Recognition = speechWindow.SpeechRecognition || speechWindow.webkitSpeechRecognition;
        if (!Recognition) {
            setError("当前浏览器不支持语音输入");
            setSupported(false);
            return;
        }
        if (recognitionRef.current) {
            stop();
        }
        const recognition = new Recognition();
        recognition.lang = "zh-CN";
        recognition.continuous = true;
        recognition.interimResults = true;
        recognition.onresult = (event) => {
            let text = "";
            for (let index = event.resultIndex; index < event.results.length; index += 1) {
                text += event.results[index][0]?.transcript ?? "";
            }
            if (text.trim()) {
                onText(text.trim());
            }
        };
        recognition.onerror = () => {
            setError("语音识别失败，请检查麦克风权限");
            setListening(false);
        };
        recognition.onend = () => {
            setListening(false);
            recognitionRef.current = null;
        };
        recognitionRef.current = recognition;
        setError("");
        setListening(true);
        recognition.start();
    }, [onText, stop]);

    useEffect(() => () => stop(), [stop]);

    return { supported, listening, error, start, stop };
}
```

- [ ] **Step 2: If TypeScript lacks Web Speech types, add local declarations**

If `npm run typecheck` reports `Cannot find name 'SpeechRecognition'`, add these declarations at the top of `useSpeechInput.ts`:

```ts
type SpeechRecognitionResultLike = {
    [index: number]: { transcript: string };
};

type SpeechRecognitionEventLike = {
    resultIndex: number;
    results: {
        length: number;
        [index: number]: SpeechRecognitionResultLike;
    };
};

type SpeechRecognition = {
    lang: string;
    continuous: boolean;
    interimResults: boolean;
    onresult: ((event: SpeechRecognitionEventLike) => void) | null;
    onerror: (() => void) | null;
    onend: (() => void) | null;
    start: () => void;
    stop: () => void;
};
```

- [ ] **Step 3: Create voice button**

Create `frontend/src/components/practice/VoiceAnswerButton.tsx`:

```tsx
import { Mic, MicOff } from "lucide-react";
import { useSpeechInput } from "@/hooks/useSpeechInput";
import { cn } from "@/lib/cn";

type VoiceAnswerButtonProps = {
    disabled?: boolean;
    onText: (text: string) => void;
};

export function VoiceAnswerButton({ disabled, onText }: VoiceAnswerButtonProps) {
    const speech = useSpeechInput(onText);
    const blocked = disabled || !speech.supported;
    return (
        <div className="voice-answer">
            <button
                type="button"
                className={cn("voice-answer__button", speech.listening && "voice-answer__button--active")}
                disabled={blocked}
                onClick={speech.listening ? speech.stop : speech.start}
                title={speech.supported ? "语音输入" : "当前浏览器不支持语音输入"}
            >
                {speech.listening ? <MicOff size={16} /> : <Mic size={16} />}
                <span>{speech.listening ? "停止语音" : "语音输入"}</span>
            </button>
            {speech.error ? <span className="voice-answer__error">{speech.error}</span> : null}
        </div>
    );
}
```

- [ ] **Step 4: Typecheck frontend**

Run:

```bash
cd /Users/wyw/Desktop/Project/QA_Agent/frontend && npm run typecheck
```

Expected: pass. If Web Speech DOM types are missing, add the local declarations from Step 2 and run typecheck again.

---

## Task 11: Wire Speech Input Into PracticePage

**Files:**
- Modify: `frontend/src/components/practice/QuestionWorkspace.tsx`
- Modify: `frontend/src/pages/PracticePage.tsx`
- Modify: `frontend/src/styles/pages/practice.css`

- [ ] **Step 1: Inspect QuestionWorkspace props**

Open:

```bash
sed -n '1,260p' /Users/wyw/Desktop/Project/QA_Agent/frontend/src/components/practice/QuestionWorkspace.tsx
```

Confirm it owns the answer textarea and receives `answer`, `readonly`, `onAnswerChange`.

- [ ] **Step 2: Add `onVoiceText` prop**

In `QuestionWorkspace.tsx` props:

```ts
onVoiceText?: (text: string) => void;
```

Import:

```ts
import { VoiceAnswerButton } from "@/components/practice/VoiceAnswerButton";
```

Render near the answer textarea toolbar:

```tsx
<VoiceAnswerButton disabled={readonly} onText={(text) => onVoiceText?.(text)} />
```

- [ ] **Step 3: Append voice text in PracticePage**

In `PracticePage.tsx`, define:

```ts
const handleVoiceText = (text: string) => {
    setAnswer((current) => {
        const prefix = current.trim() ? `${current.trimEnd()} ` : "";
        return `${prefix}${text}`;
    });
};
```

Pass into `QuestionWorkspace`:

```tsx
onVoiceText={handleVoiceText}
```

- [ ] **Step 4: Add CSS**

Append to `frontend/src/styles/pages/practice.css`:

```css
.voice-answer {
    display: inline-flex;
    align-items: center;
    gap: 8px;
}

.voice-answer__button {
    display: inline-flex;
    align-items: center;
    gap: 7px;
    min-height: 36px;
    padding: 0 12px;
    border: 1px solid rgba(47, 42, 36, 0.14);
    border-radius: 8px;
    background: rgba(255, 255, 255, 0.72);
    color: #2f2a24;
    cursor: pointer;
}

.voice-answer__button--active {
    border-color: rgba(181, 90, 76, 0.42);
    background: rgba(181, 90, 76, 0.12);
    color: #8e3f35;
}

.voice-answer__button:disabled {
    cursor: not-allowed;
    opacity: 0.48;
}

.voice-answer__error {
    color: #b55a4c;
    font-size: 12px;
}
```

- [ ] **Step 5: Typecheck frontend**

Run:

```bash
cd /Users/wyw/Desktop/Project/QA_Agent/frontend && npm run typecheck
```

Expected: pass.

---

## Task 12: API Documentation

**Files:**
- Modify: `docs/API.md`
- Modify: `docs/V7.md` only if implementation deviates from the design.

- [ ] **Step 1: Update `/qa/item/complete`**

In `docs/API.md`, find `/qa/item/complete` and update request:

```json
{
  "id": "qa-item-id",
  "question": "重新补全时使用的问题",
  "answer": "用户指定标准答案，可选；为空时由 AI 自动生成答案"
}
```

Add note:

```text
当 answer 非空时，服务端把 answer 视为人工标准答案，AI 只补充 knowledgeNote、moduleTag、difficulty、sourceReliable、sourceChunkIds。
```

- [ ] **Step 2: Update practice init/restart**

For `/practice/session/init` and `/practice/session/restart`, add:

```json
{
  "itemIds": ["qa-item-id-1", "qa-item-id-2"]
}
```

Add note:

```text
itemIds 为空或不传时按题集创建练习；itemIds 非空时只创建指定题目的练习。itemIds 与 selectedModule 同时存在时，itemIds 优先。
```

- [ ] **Step 3: Update practice history response**

Add `perfectCount` to history/session response examples.

- [ ] **Step 4: Diff docs**

Run:

```bash
cd /Users/wyw/Desktop/Project/QA_Agent && git diff -- docs/API.md docs/V7.md
```

Expected: only V7-related API/design updates.

---

## Task 13: Final Verification

**Files:**
- No new files unless fixing verification failures.

- [ ] **Step 1: Backend compile**

Run:

```bash
cd /Users/wyw/Desktop/Project/QA_Agent/backend && mvn -DskipTests package
```

Expected: `BUILD SUCCESS`.

- [ ] **Step 2: Frontend typecheck**

Run:

```bash
cd /Users/wyw/Desktop/Project/QA_Agent/frontend && npm run typecheck
```

Expected: TypeScript exits successfully.

- [ ] **Step 3: Frontend build**

Run:

```bash
cd /Users/wyw/Desktop/Project/QA_Agent/frontend && npm run build
```

Expected: Vite production build completes.

- [ ] **Step 4: Static search for accidental scope creep**

Run:

```bash
cd /Users/wyw/Desktop/Project/QA_Agent && rg -n "SpeechRecognition|webkitSpeechRecognition|itemIds|complete-with-answer|AnswerBasedComplete" backend frontend docs -S
```

Expected: matches only in the V7 implementation files and docs.

- [ ] **Step 5: Check worktree summary**

Run:

```bash
cd /Users/wyw/Desktop/Project/QA_Agent && git status --short && git diff --stat
```

Expected: modified files match the plan. Do not commit unless the user explicitly asks.

---

## Manual Verification Checklist

Do these after compile/build, either by the implementer or during later frontend/backend integration:

1. `/qa/item/complete` without `answer` still produces an AI answer.
2. `/qa/item/complete` with `answer` preserves the exact user answer in final item detail.
3. Question detail “开始测试” creates a session with exactly one item.
4. Repository selected items create a session with exactly selected items.
5. Quiz page start still creates full-set practice and does not send `itemIds`.
6. Result page charts match existing numeric cards.
7. Result matrix click opens the correct review index.
8. Supported browser can voice-input into answer textarea.
9. Unsupported browser shows disabled voice input instead of breaking the page.
