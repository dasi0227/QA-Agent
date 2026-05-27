# Dasi 临时对话看板娘设计

## 1. 背景

`README.md` 中的“全局 AI 对话助手（看板娘）”定位为轻量、临时、低打扰的应用内 AI 问答入口。

本次设计收敛后，它不承担业务 Copilot 职责，不理解当前页面上下文，不执行题目修改、补全、删除、练习启动等写操作。它只解决一个问题：用户停留在当前页面时，可以直接在应用内临时问 AI，不需要切到另一个浏览器页面。

## 2. 核心原则

1. 看板娘是“临时对话”，不是长期会话，也不是 Memory。
2. 后端不落库，不把对话写入用户画像、练习记录或任务记录。
3. 前端切换页面或刷新页面后，本轮临时对话结束。
4. 后端使用 LangChain4j `@MemoryId` 和窗口记忆能力维护短期上下文。
5. 前端仍维护当前窗口消息列表，用于 UI 展示。
6. 不传页面上下文给后端，后端只接收用户输入和临时会话 ID。
7. 第一版不做流式输出，先用普通请求响应。

## 3. 展示范围

第一版只在适合边看边问的页面展示：

- 资料库页：`/repository/document`
- 题目详情页：`/repository/question`
- 练习答题页：`/practice/:sessionId`
- 结果页：`/practice/:sessionId/result`、`/result/:sessionId`

不展示在首页、创建页、Profile、登录注册页、404 页。

## 4. 前端交互设计

### 4.1 看板娘入口

入口固定在页面左下角，不允许拖动。

视觉采用轻量 SVG 分层人物，不引入真正 Live2D 或 Three.js：

- 人物正对坐着敲电脑。
- 左侧放一杯冒热气的咖啡。
- 人物有轻微呼吸、眨眼、敲键盘、咖啡蒸汽动画。
- hover 时人物轻微看向电脑方向。
- 不显示常驻“问问我”胶囊。

选择 SVG 分层而不是真 Live2D 的原因：

- 效果足够接近“会动的人物入口”。
- 不需要 Live2D 模型资产、运行时 SDK、纹理文件和动作文件。
- 性能和维护成本更可控。
- 更容易匹配当前项目的暖纸色、深墨色、理工感视觉风格。

### 4.2 提示气泡

人物脑袋右上角显示短时气泡。

首次进入支持页面时：

1. 延迟约 300-600ms 显示气泡。
2. 气泡内容：`你好，我是 Dasi，有什么问题可以问我～`
3. 显示 3 秒。
4. 随后 800-1000ms 慢慢淡出。

后续低频随机气泡：

- 仅在聊天窗口关闭时触发。
- 每 1-3 分钟随机出现一次。
- 用户打开临时对话后暂停气泡。
- 用户关闭临时对话后重新计时。

建议文案池：

- `累了吗？要不先喝口水。`
- `卡住也正常，慢慢拆。`
- `哪里没想通？可以直接问我。`
- `答案太长？我可以帮你拆结构。`
- `不确定怎么表达？丢给我看看。`
- `还是不理解？没关系，我会出手。`
- `这波可以先稳一手。`
- `问题不大，我们逐层分析。`
- `啊，是关中王来了。`

文案应轻松，但不能过密、过吵或过度玩梗。

### 4.3 临时对话窗口

点击人物打开临时对话窗口，再次点击人物关闭窗口。

窗口行为：

- 固定在左下角人物上方。
- 与人物垂直距离较近，视觉上属于同一组件。
- 打开后不自动关闭。
- 手动点击人物或关闭按钮才关闭。
- 打开窗口后，提示气泡立即消失。

窗口标题：

- `临时对话`

窗口头部：

- 不显示 logo。
- 右侧保留关闭按钮。

输入区：

- 支持 Enter 发送。
- 发送按钮使用回车 icon，不使用“发”字。
- 第一版不要求支持 Shift+Enter 多行；如果 textarea 需要多行，Shift+Enter 可作为自然补充。

消息展示：

- 用户消息使用纯文本气泡。
- AI 消息支持 Markdown 渲染。

## 5. 前端状态设计

前端需要维护两类状态：

### 5.1 UI 展示消息

`DasiChatWidget` 内部维护：

- `messages`
- `input`
- `isOpen`
- `isLoading`
- `tempChatId`
- `bubbleVisible`
- `bubbleText`

`messages` 只用于当前窗口 UI 展示，不写入 localStorage、sessionStorage 或后端数据库。

### 5.2 临时会话 ID

前端生成 `tempChatId`，例如：

```text
dasi_${Date.now()}_${random}
```

生命周期：

- 首次进入支持页面时生成。
- 同一路由内打开/关闭窗口时复用。
- 路由变化时重新生成，并清空 `messages`。
- 刷新页面后自然重新生成。

这样可以满足：

- 页面不刷新时保留当前对话。
- 切换页面后结束本轮对话。
- 后端可以通过 `tempChatId` 使用 LangChain4j 窗口记忆。

## 6. 前端实现设计

### 6.1 组件拆分

建议新增独立组件目录：

```text
frontend/src/components/dasi/
```

组件结构：

```text
DasiChatWidget
  -> DasiMascot
  -> DasiBubble
  -> DasiChatPanel
      -> DasiMessageList
      -> DasiMessageBubble
      -> DasiChatInput
```

职责说明：

| 组件 | 职责 |
| --- | --- |
| `DasiChatWidget` | 总容器，控制展示范围、状态、请求、路由切换清空 |
| `DasiMascot` | 左下角 SVG 分层人物入口，处理点击开关 |
| `DasiBubble` | 首次问候和低频随机提示气泡 |
| `DasiChatPanel` | 临时对话浮层，包含标题、关闭按钮、消息区、输入区 |
| `DasiMessageList` | 消息滚动区，消息更新后滚动到底部 |
| `DasiMessageBubble` | 用户 / AI 消息气泡，AI 消息渲染 Markdown |
| `DasiChatInput` | 输入框、Enter 发送、回车 icon 按钮 |

第一版可以把子组件放在同一个文件中实现，但建议保留上述边界，避免后续继续扩展时 `DasiChatWidget` 过大。

### 6.2 挂载位置

推荐在 `AppShell` 内挂载：

```tsx
<Topbar />
<div className="app-shell__content">
    <Outlet />
</div>
<DasiChatWidget />
<SiteFooter />
```

原因：

- 资料库、题目详情等页面都在 `AppShell` 下。
- `DasiChatWidget` 可以通过 `useLocation()` 判断当前路由是否展示。
- 不需要每个页面单独引入。

练习答题页和结果页不在 `AppShell` 内，当前路由结构使用独立 `RequireAuth` / `FlowShell`。因此有两种挂载方式：

1. 在各自页面局部挂载 `DasiChatWidget`。
2. 在更外层路由布局中增加一个公共 authenticated shell。

第一版推荐方案 1：

- `DocumentPage` / `QuestionPage` 由 `AppShell` 内的全局 `DasiChatWidget` 覆盖。
- `PracticePage`、`ResultPage` 单独挂载 `DasiChatWidget`。

这样改动最小，不需要重构路由布局。

### 6.3 展示范围判断

前端维护允许展示的路由规则：

```ts
function shouldShowDasi(pathname: string) {
    return pathname === "/repository/document"
        || pathname === "/repository/question"
        || pathname.startsWith("/practice/") && !pathname.endsWith("/review")
        || pathname.startsWith("/result/");
}
```

实际实现时需要注意：

- `/practice/:sessionId/result` 应显示。
- `/result/:sessionId` 应显示。
- `/practice/:sessionId/review` 第一版不显示，避免只读回看页被浮层干扰。
- 如果 `QuestionPage` 通过 query string 区分题集和题目，`pathname` 不变，但切换 `search` 时也应视为页面变化，清空本轮对话。

### 6.4 路由变化清空

监听：

```ts
const routeKey = `${location.pathname}${location.search}`;
```

当 `routeKey` 变化时：

- 清空 `messages`
- 清空 `input`
- 关闭 `isOpen`
- 重新生成 `tempChatId`
- 重置首次气泡状态
- 重新启动气泡计时

这可以满足“没有刷新或切换页面时保留；刷新或切换页面后清空”。

### 6.5 请求 Hook

API 类型：

```ts
export type TempChatInput = {
    tempChatId: string;
    message: string;
};

export type TempChatResponse = {
    role: "assistant";
    content: string;
};
```

Hook：

```ts
useTempChatMutation()
```

请求：

```http
POST /chat/temp
```

前端发送时只传本轮用户输入和 `tempChatId`，不传完整 messages，不传页面上下文。

### 6.6 消息发送流程

```text
用户输入 message
  -> 点击回车 icon 或按 Enter
  -> 前端追加 user message 到 messages
  -> 清空输入框
  -> 调用 /chat/temp
  -> 成功后追加 assistant message
  -> 失败后追加 error message 或显示错误态
```

发送时限制：

- 空输入不发送。
- 请求中禁止重复发送，或允许排队但第一版建议禁止。
- message 长度前端先限制，例如 4000 字符。

失败处理：

- 保留用户刚刚发送的消息。
- 追加一条轻量错误消息：`Dasi 暂时没有回复，请稍后再试。`
- 用户可以重新输入或复制上一条继续问。

### 6.7 Markdown 渲染

建议新增依赖：

```text
react-markdown
remark-gfm
```

渲染规则：

- 只有 assistant 消息走 Markdown。
- user 消息按纯文本展示。
- 不使用 `rehype-raw`。
- 代码块使用项目现有深色墨色体系做基础样式。
- 链接打开新窗口，附带 `target="_blank"` 和 `rel="noreferrer"`。

### 6.8 SVG 人物和动效

人物建议直接用 React 组件内联 SVG，而不是图片资源：

- 可维护性更高。
- 五官、头部、手、电脑、咖啡、蒸汽都可以单独加 class。
- 不需要处理透明背景、裁切和图片压缩。
- 与当前 UI 色彩变量更容易统一。

已确认视觉要点：

- 人物整体固定左下角。
- 人物坐着敲电脑。
- 咖啡放在左侧，杯把也在左侧。
- 人物默认视线偏向右下电脑。
- 鼻子略上移，嘴巴略左移。
- 气泡从整个头部右上方冒出，不指向耳朵。
- 临时对话窗口与人物垂直间距较近。

动效：

- `breathe`：身体轻微呼吸。
- `blink`：眼睛眨动。
- `typeLeft` / `typeRight`：双手轻微敲键盘。
- `steam`：咖啡蒸汽上浮。
- `lookToLaptop`：hover 时轻微看向电脑。
- `bubbleIn` / `bubbleOut`：气泡出现和 3 秒后淡出。

### 6.9 样式文件

建议新增：

```text
frontend/src/styles/components/dasi-chat.css
```

避免把样式塞进页面级 CSS。

样式边界：

- `dasi-widget`
- `dasi-mascot`
- `dasi-bubble`
- `dasi-chat-panel`
- `dasi-message`
- `dasi-chat-input`

不修改顶部导航样式，不影响页面主布局。

## 7. 后端设计

### 7.1 模块位置

新增 Chat 领域，建议目录：

```text
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/chat
```

后端保持 DDD 分层：

- `interfaces`：Controller
- `types`：request / response DTO
- `domain/chat`：Chat service、subagent 接口
- `infrastructure`：如需配置 ChatMemoryProvider，可放配置或基础设施适配

### 7.2 API

新增接口：

```http
POST /chat/temp
```

请求：

```json
{
  "tempChatId": "dasi_1770000000000_abcd",
  "message": "帮我解释一下 JVM 类加载"
}
```

响应：

```json
{
  "role": "assistant",
  "content": "Markdown 格式回复"
}
```

请求字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `tempChatId` | `string` | 是 | 前端生成的临时对话 ID |
| `message` | `string` | 是 | 用户本轮输入 |

响应字段说明：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `role` | `string` | 固定为 `assistant` |
| `content` | `string` | AI 回复，允许 Markdown |

### 7.3 后端调用链路

```text
ChatController
  -> IChatService.tempChat(request)
      -> 从 IContextUtil 获取 userId
      -> 校验 tempChatId / message
      -> UserLlmModelProvider.getUserLlmModel(userId)
      -> DasiTempChatAgent.chat(tempChatId, message)
      -> 返回 ChatTempResponse
```

### 7.4 LangChain4j Agent

新增轻量 AI Service：

```java
public interface DasiTempChatAgent {

    @SystemMessage(fromResource = "prompt/chat/temp-chat.txt")
    String chat(@MemoryId String tempChatId, @UserMessage String message);
}
```

Prompt 定位：

- 你是 QA_Agent 内置的临时对话助手 Dasi。
- 只回答用户当前输入。
- 不声称自己能读取当前页面。
- 不声称自己能修改题目、资料或练习记录。
- 不保存记忆。
- 可以使用 Markdown 输出。
- 对不确定内容要说明不确定。
- 回答应简洁、有条理，适合学习和技术问答。

### 7.5 ChatMemoryProvider

后端使用 LangChain4j 的窗口记忆能力。

建议窗口大小：

```text
MemoryWindowSize = 12
```

含义：

- 同一个 `tempChatId` 下保留最近约 12 条消息。
- 超出窗口自动丢弃更早内容。
- 不落库，只保存在服务端内存中。

需要注意：

- 后端重启后临时上下文丢失，这是符合“临时对话”的。
- 多实例部署时，如果不引入 Redis，同一个 `tempChatId` 打到不同实例会丢上下文。
- 第一版可以接受单实例内存方案。
- 后续如需多实例稳定上下文，再替换为 Redis backed memory。

### 7.6 过期清理

虽然不落库，但后端内存仍需要防止无限增长。

建议实现一个带 TTL 的内存容器：

- key：`tempChatId`
- value：`ChatMemory`
- TTL：30 分钟无请求后过期
- 最大容量：可选，例如 1000 个临时会话

如果第一版不想引入额外缓存库，可以用简单的内存 Map + 定时清理。

如果项目已有 Caffeine 依赖，可优先使用 Caffeine：

```text
expireAfterAccess(30 minutes)
maximumSize(1000)
```

### 7.7 校验与限制

后端需要限制：

- `tempChatId` 不能为空。
- `message` 不能为空。
- `message` 最大长度建议 4000 字符。
- 超长返回参数错误。
- 不接收 `pageType`、`question`、`qaSetId`、`sessionId` 等页面上下文字段。

第一版不需要：

- 保存对话记录。
- 返回历史消息。
- 清空接口。
- 会话列表。
- SSE 流式回复。
- 对话审计表。

## 8. Markdown 渲染

支持 Markdown。

后端：

- 只返回字符串 `content`。
- 不返回 HTML。
- 不做 Markdown 解析。

前端：

- AI 消息用 `react-markdown` 渲染。
- 建议配合 `remark-gfm` 支持列表、表格、代码块。
- 不启用原始 HTML 渲染。
- 用户消息按纯文本展示。

安全边界：

- 不使用 `rehype-raw`。
- 外链打开新窗口时加 `rel="noreferrer"`。
- 代码块第一版只做样式，不做复制按钮。

## 9. 错误处理

前端：

- 请求中显示 loading 状态。
- 失败时在消息列表中插入一条错误提示，如 `Dasi 暂时没有回复，请稍后再试。`
- 输入内容不丢失，用户可重试。

后端：

- 模型配置缺失：返回明确错误信息，引导用户到 Profile 配置模型。
- LLM 调用失败：使用现有异常体系返回可读 message。
- 参数错误：使用全局参数校验返回。

## 10. 后续扩展

后续可以扩展，但第一版不做：

1. `/chat/temp/stream` 流式输出。
2. 多实例 Redis 临时记忆。
3. 页面上下文注入。
4. 题目修改、重新补全等写操作。
5. 与 V6 Memory 联动。
6. 按页面类型提供快捷提示词。

## 11. 推荐实施顺序

1. 后端实现 `/chat/temp` 和 `DasiTempChatAgent`。
2. 后端接入 `@MemoryId` 与窗口记忆。
3. 前端实现 `DasiChatWidget` 及展示范围控制。
4. 前端接入 Markdown 渲染。
5. 前端实现 SVG 看板人物、气泡策略、临时对话窗口。
6. 编译验证前后端。

## 12. 已确认结论

- 前端设计已确认：左下角 SVG 分层人物，人物敲电脑，左侧咖啡，提示气泡短时出现，点击人物开关临时对话。
- 后端方案选择 `@MemoryId + MemoryWindowSize`。
- 前端传 `tempChatId + message`，不传完整 messages。
- 对话不落库。
- 页面刷新或切换路由后对话结束。
- AI 回复支持 Markdown，由前端渲染。
