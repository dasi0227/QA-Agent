# V7 快速迭代设计说明

本文以当前代码实现为准，核心文件包括：

- `CompleteAgent` / `CompleteSubAgentWithAnswer` / `CompleteSubAgentWithoutAnswer`
- `PracticeFlowService` / `PracticeInitRequest` / `PracticeRestartRequest`
- `ResultPage` / `PracticeSessionResponse`
- `VoiceAnswerButton` / `useSpeechInput`
- `ChatService` / `TempChatMemoryProvider` / `ChatController`
- `DasiChatWidget`

## 1. 当前目标

V7 是一次快速迭代版本，以多个独立的小范围改动聚合交付，覆盖四个方向：

1. CompleteAgent 链路拆分：支持用户提供标准答案时的差异化补全
2. 练习题目筛选：统一单题/多题/全量的练习会话创建入口
3. 结果页可视化：将评估数据以图表形式呈现
4. 语音输入：前端答题区支持语音转文字

以及一个新增能力：

5. Dasi 临时对话助手：跨页面常驻的 AI 对话入口

## 2. CompleteAgent 链路拆分

### 2.1 改动动机

原 CompleteAgent 统一走"无答案"补全链路，AI 从零生成答案 + 知识点 + 元信息。用户无法在补全时提供自己裁定的标准答案作为锚点。

### 2.2 拆分方案

`CompleteAgent.doComplete()` 根据 `CompleteContext.answer` 是否为空，分叉为两条链路：

| 链路 | SubAgent | 输入 | AI 产出 |
| --- | --- | --- | --- |
| 无答案 | `CompleteSubAgentWithoutAnswer` | question + evidence + profile | answer + knowledgeNote + moduleTag + difficulty |
| 有答案 | `CompleteSubAgentWithAnswer` | question + **answer** + evidence + profile | knowledgeNote + moduleTag + difficulty |

有答案链路的关键行为：

1. `answer` 字段由用户提供，AI 不修改，直接作为最终答案落库
2. AI 仅推断剩余字段：`knowledgeNote`、`moduleTag`、`difficulty`、`sourceReliable`、`sourceChunkIds`
3. 返回模型 `CompleteResultWithoutAnswer` 不含 `answer` 字段，Java 层组装完整 `CompleteResult`

### 2.3 接口变更

`QaItemCompleteRequest` 新增可选字段：

```json
{
  "qaItemId": "string",
  "question": "string",
  "answer": "string (optional)"
}
```

`answer` 为空时走无答案链路，非空时走有答案链路。

### 2.4 前端

`QuestionPage` 补全弹窗增加"标准答案"输入框（可选），用户填入后调用补全接口，AI 仅补充其余字段。

## 3. 练习题目筛选

### 3.1 改动动机

原练习会话初始化时纳入题集全部题目，用户无法只练习其中部分题目。

### 3.2 统一接口

`PracticeInitRequest` 和 `PracticeRestartRequest` 各新增 `itemIds`（`List<String>`）：

```json
{
  "qaSetId": "string",
  "mode": "SEQUENTIAL",
  "feedbackMode": "ITEM_BY_ITEM",
  "itemIds": ["id1", "id2"]
}
```

行为：

| `itemIds` | 含义 |
| --- | --- |
| `null` 或空 | 加载题集全部题目（保持现有行为） |
| 单个 ID | 单题练习 |
| 多个 ID | 只练习指定题目 |

`PracticeFlowService.initSession()` 在 `itemIds` 非空时按指定 ID 列表加载题目并校验所属题集；为空时走原全量逻辑。

### 3.3 前端

`QuizPage` 在开始练习前增加题目勾选步骤，用户可手动选择题目，选中 ID 列表传给接口。

## 4. 结果页可视化

### 4.1 改动范围

纯前端改动，数据来自现有 `/practice/session/detail` 接口。`ResultPage` 新增四个可视化区域：

| 图表 | 说明 |
| --- | --- |
| 五维结果堆叠条 | PERFECT / CORRECT / DEFICIENT / WRONG / UNKNOWN 横向堆叠比例条 + 图例 |
| 模块表现柱状图 | 按 `moduleTag` 聚合的得分率横向柱状图（取前 8 个模块） |
| 历史趋势图 | 最近 8 次练习得分的纵向柱状趋势 |
| 题目结果矩阵 | 每题一个色块按钮，颜色对应判分结果，点击跳转回顾页 |

### 4.2 数据来源

| 图表 | 数据 |
| --- | --- |
| 堆叠条 | `session.perfectCount / correctCount / deficientCount / wrongCount / unknownCount` |
| 模块柱状图 | 前端对 `items` 按 `moduleTag` 聚合计算平均分和分布 |
| 历史趋势 | `usePracticeHistoryQuery`（`GET /practice/session/history`） |
| 结果矩阵 | `items` 的 `result` / `unknown` 字段 |

## 5. 语音输入

### 5.1 改动范围

纯前端改动，零后端影响。

### 5.2 实现

- `useSpeechInput` hook：封装 Web Speech API（`SpeechRecognition`），管理监听状态、错误处理、浏览器兼容检测
- `VoiceAnswerButton` 组件：语音按钮，置于答题文本区旁，点击开始/停止录音
- 集成到 `QuestionWorkspace`：转写结果追加到文本区，支持语音与键盘混合输入

约束：

1. 仅在 Chrome 系浏览器完整支持中文识别
2. 需要 HTTPS 或 localhost 环境
3. 不支持的浏览器显示禁用态提示

## 6. Dasi 临时对话助手

### 6.1 定位

Dasi 是系统内置的临时对话助手，以悬浮看板娘形态常驻于页面左下角，用户可在任意页面发起自由 AI 对话。

关键约束：

1. 临时会话：不持久化，刷新或离开页面后清空
2. 不感知页面上下文：仅基于用户在消息中提供的信息回答，不读取数据库
3. 不执行系统操作：只能对话，不能修改题目、删除资料等
4. 与长期记忆体系完全隔离：对话内容不写入 `user_memory`

### 6.2 后端

#### ChatController

```
POST /chat/temp
```

请求：

```json
{
  "tempChatId": "string (前端生成的临时会话 ID)",
  "message": "string"
}
```

响应：

```json
{
  "role": "assistant",
  "content": "string"
}
```

#### ChatService

使用 LangChain4j AiServices 构建 `TempChatBot`：

1. 加载 `prompt/chat/temp-chat.txt` 作为 SystemMessage
2. 使用用户配置的 LLM 模型
3. 通过 `TempChatMemoryProvider` 管理多轮对话记忆（最近 12 条消息窗口）

#### TempChatMemoryProvider

基于 `ConcurrentHashMap` 的内存存储：

| 参数 | 值 |
| --- | --- |
| 最大消息数 | 12 条（`MessageWindowChatMemory`） |
| 会话 TTL | 30 分钟 |
| 最大会话数 | 1000 个 |
| 清理策略 | 每 1 分钟检查过期 + 超限时移除最久未访问 |

不依赖 Redis、MySQL 等外部存储。

### 6.3 前端

`DasiChatWidget` 组件：

1. 悬浮图标：页面左下角常驻圆形头像按钮
2. 对话面板：点击展开，包含消息列表 + 输入框
3. 折叠模式：可最小化为仅图标
4. 临时会话 ID：由前端 `crypto.randomUUID()` 生成，页面级生命周期

前端按需调用 `POST /chat/temp`，不做消息持久化。

### 6.4 无状态约束

Dasi chat 的 prompt 明确禁止 AI 声称能读取页面内容或执行系统操作。用户若希望 AI 基于当前页面内容作答，需主动将相关文本粘贴到消息中。这个设计确保临时对话始终是"用户主导、AI 辅助"的问答模式，不越界为代理操作。

## 7. Prompt 文件

```text
prompt/chat/temp-chat.txt
prompt/complete/complete-with-answer.txt
```

## 8. 当前代码口径

1. CompleteAgent 的拆分为向后兼容——`answer` 为空时行为与 V6 完全一致。
2. 练习选题 `itemIds` 的校验由 `PracticeFlowService` 统一处理：ID 不属于当前题集时抛出 `BAD_REQUEST`。
3. 结果页图表为纯 CSS 实现（堆叠条、柱状图、趋势图），未引入第三方图表库，避免增加包体积。
4. 语音输入依赖浏览器能力，不做服务端语音识别。
5. Dasi 对话的 `tempChatId` 由前端管理，后端不校验格式，仅作为 ChatMemory 的隔离 key。
6. `ChatService` 与 `ChatController` 位于独立的 `domain/chat` 和 `interfaces/controller` 包，不耦合现有 Agent 体系。
