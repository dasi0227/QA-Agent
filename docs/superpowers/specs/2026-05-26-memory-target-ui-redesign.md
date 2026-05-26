# Memory 目标对象与 Profile 记忆页重构设计

## 背景

V6 Memory 的定位是“基于真实练习评估沉淀的长期用户学习画像”。它不是传统 Agent 的自由记忆，也不是聊天上下文摘要，而是从用户做题、评分、反馈和证据链中生成的客观判断。

当前 Memory 已具备基础沉淀能力，但存在两个明显问题：

1. 后端目标对象语义不够稳定：`BEHAVIOR` 的 `BehaviorKey` 多数是负向缺陷枚举，与 `MASTER / UNCLEAR / AWFUL` 组合时语义尴尬；`GENERAL` 容易生成空泛整体总结，和模块画像、能力画像重复。
2. 前端 Memory 页展示像“设置页里塞列表”，分类、卡片、证据记录层级混杂；左侧列表滚动影响右侧观感，证据记录也缺少判断来源的时间线感。

本设计目标是同时收敛后端 Memory 语义，并重构 Profile Memory 页为清晰的学习画像工作台。

## 设计目标

- Memory 的目标对象必须可校验、可解释、适合长期合并。
- 删除空泛目标对象，减少 LLM 输出歧义。
- 前端按“表现状态 -> 画像对象 -> 证据链”组织信息。
- 用户能快速看出哪些地方稳定、哪些需要巩固、哪些存在明显缺口。
- 证据记录要展示 Memory 为什么存在，而不是只展示一组普通列表项。

## 不做范围

- 不改变 Memory 的异步沉淀架构。
- 不改变 `user_memory_evidence` 作为真实性证据链的定位。
- 不实现移动端专项适配；本次优先桌面 Web 侧。
- 不把 Memory 传给 DraftAgent。
- 不新增 Memory 编辑能力。
- 不恢复物理删除，仍保留隐藏语义。

## 后端目标对象重构

### 现状问题

当前目标对象为：

```text
MODULE
BEHAVIOR
GENERAL
```

其中 `MODULE` 语义清晰，表示固定模块 tag 下的掌握情况。

`BEHAVIOR` 的问题在于 `BehaviorKey` 是负向问题枚举：

```text
MISSING_TRADEOFF
DEFINITION_ONLY
UNSTRUCTURED_ANSWER
SCENARIO_WEAK
CAUSE_ANALYSIS_WEAK
TERMINOLOGY_INACCURATE
```

这些值本身已经带有负面判断，和 `MASTER` 组合会出现不自然语义。例如：

```text
MASTER + BEHAVIOR + MISSING_TRADEOFF
```

这不是一个稳定的画像表达。

`GENERAL` 的问题是过宽，容易变成“整体表现不稳定”这类宽泛总结。它很难用于 GenerateAgent 的 Plan 阶段，也不适合和其它 Memory 平级展示。

### 新目标对象

目标对象收敛为两类：

```text
MODULE_TAG
ANSWER_SKILL
```

含义：

| targetType | 中文含义 | targetKey 来源 | 用途 |
| --- | --- | --- | --- |
| `MODULE_TAG` | 知识模块 | `ModuleTag` 固定池 | 描述用户在某个知识模块上的掌握状态 |
| `ANSWER_SKILL` | 回答能力 | `AnswerSkill` 固定池 | 描述用户跨模块的表达、分析和迁移能力 |

删除：

```text
BEHAVIOR
GENERAL
BehaviorKey
```

### AnswerSkill 常量池

新增 `AnswerSkill` 常量类，放在：

```text
backend/qa-agent-types/src/main/java/com/dasi/qa/agent/types/constant/AnswerSkill.java
```

它和现有 `ModuleTag` 同级，值本身就是展示文案和落库值，提供固定值和合法性校验。

第一版固定值：

| 值 | 含义 |
| --- | --- |
| `内容/结构回答的完整性` | 是否能完整覆盖核心内容，并按清晰结构组织回答 |
| `原因/场景分析的逻辑性` | 是否能解释原因机制，并迁移到真实业务或工程场景 |
| `概念/术语表达的精确度` | 是否能准确使用概念和术语，避免混淆或模糊表达 |

这些值是中性能力维度，可以和三类 `memoryType` 形成稳定组合：

```text
MASTER + ANSWER_SKILL + 内容/结构回答的完整性
用户回答结构稳定，能按结论、原因和边界组织内容。

UNCLEAR + ANSWER_SKILL + 原因/场景分析的逻辑性
用户能说出部分原因，但对机制链路和场景迁移说明不稳定。

AWFUL + ANSWER_SKILL + 概念/术语表达的精确度
用户在基础概念表达上出现明显混淆或空答。
```

### memoryType 保持三类

`memoryType` 保持：

```text
MASTER
UNCLEAR
AWFUL
```

中文展示建议：

| memoryType | 中文展示 | UI 分类 |
| --- | --- | --- |
| `MASTER` | 稳定掌握 | 表现稳定 |
| `UNCLEAR` | 理解不稳 | 需要巩固 |
| `AWFUL` | 严重薄弱 | 明显缺口 |

第一版不恢复 `EXPRESSION`，表达类问题由 `ANSWER_SKILL` 的具体维度承载。

### 合并键

Memory 合并键调整为：

```text
user_id + memory_type + target_type + target_key
```

其中：

- `targetType=MODULE_TAG` 时，`targetKey` 必须命中 `ModuleTag`。
- `targetType=ANSWER_SKILL` 时，`targetKey` 必须命中 `AnswerSkill`。

这保证同一用户、同一表现状态、同一对象的画像持续合并，不产生多条重复 Memory。

### Prompt 调整

`memory-extract.txt` 需要同步调整：

1. `targetType` 只允许：

```text
MODULE_TAG
ANSWER_SKILL
```

2. 删除 `GENERAL`。
3. 删除 `BEHAVIOR` 和旧行为枚举。
4. `MODULE_TAG` 的 `targetKey` 必须来自 `ModuleTag` 固定池。
5. `ANSWER_SKILL` 的 `targetKey` 必须来自 `AnswerSkill` 固定池。
6. 继续强调候选画像最多 5 条，宁少勿滥。
7. 继续禁止训练建议、安慰话术、知识百科。

输出结构保持：

```json
[
  {
    "memoryType": "MASTER|UNCLEAR|AWFUL",
    "targetType": "MODULE_TAG|ANSWER_SKILL",
    "targetKey": "string",
    "content": "客观画像内容",
    "evidenceRefs": ["sessionItemId"]
  }
]
```

### Cleaner 与 Repository 映射

`MemoryResultCleaner` 负责：

- 校验 `memoryType`。
- 校验 `targetType` 只为 `MODULE_TAG / ANSWER_SKILL`。
- `MODULE_TAG` 使用 `ModuleTag.contains(...)`。
- `ANSWER_SKILL` 使用 `AnswerSkill.contains(...)`。
- 过滤非法 `targetKey`。
- 过滤空 `content`。
- 过滤空或非法 `evidenceRefs`。

后端响应仍返回：

```text
memoryType
memoryTypeText
targetType
targetTypeText
targetKey
targetKeyText
content
supportCount
lastSeenAt
```

`targetKeyText` 映射规则：

- `MODULE_TAG`：返回模块 tag 原值。
- `ANSWER_SKILL`：返回 `AnswerSkill` 中文展示。

## 数据库设计影响

不需要新增表，也不需要新增字段。

`user_memory.target_type` 和 `user_memory.target_key` 字段继续复用，但语义变化为：

```text
target_type: MODULE_TAG / ANSWER_SKILL
target_key: ModuleTag 值或 AnswerSkill 值
```

需要同步更新：

- `table.sql`
- `seed_mysql.sql`
- `docs/TABLE.md`
- `docs/API.md`
- `docs/V6-Design.md`

开发期可以直接调整 seed 中的假数据：

- 原 `BEHAVIOR + UNSTRUCTURED_ANSWER` 应迁移为 `ANSWER_SKILL + 内容/结构回答的完整性`。
- 原 `GENERAL + GENERAL` 不再保留，可删除或改写为更具体的 `MODULE_TAG` / `ANSWER_SKILL`。

## 前端 Memory 页重构

### 页面定位

`/profile/memory` 不再表现为普通设置页列表，而是“学习画像工作台”。

外层保持现状：

- 顶部导航不修改。
- Profile 左侧目录仍为 `个人 / 记忆 / 智能体`。
- 只改 Memory 子页面主内容。

### 桌面端布局

Memory 页内部采用三栏：

```text
左侧：学习画像分类
中间：记忆卡片列表
右侧：证据时间线
```

三栏职责：

| 区域 | 职责 | 滚动 |
| --- | --- | --- |
| 左侧分类 | 按表现状态筛选 Memory | 固定，不参与滚动 |
| 中间列表 | 展示当前分类下 Memory | 独立滚动 |
| 右侧详情 | 展示选中 Memory 的内容和 evidence 时间线 | 独立滚动 |

这能避免当前“左侧滚动带动右侧”的观感问题。

### 左侧分类

左侧只显示三个学习画像分类：

```text
表现稳定
需要巩固
明显缺口
```

映射：

| 分类 | memoryType | 色彩 |
| --- | --- | --- |
| 表现稳定 | `MASTER` | 低饱和绿色 |
| 需要巩固 | `UNCLEAR` | 主题黄色 / 琥珀色 |
| 明显缺口 | `AWFUL` | 克制红色 |

不再单独展示 `全部记忆`，避免增加一层弱语义入口。用户进入页面默认选中 `需要巩固`；如果该分类为空，则选中第一组有数据的分类。

### 中间 Memory 卡片

中间卡片保持当前柔和卡片风格，不再用左侧状态色边线。

默认卡片：

- 半透明白色或白色。
- 边框轻。
- 保持当前项目的圆角和玻璃质感。

选中卡片：

- 使用统一主题淡黄色。
- 不按 `memoryType` 变化颜色。

卡片内容：

```text
targetKeyText · memoryTypeText
content 摘要
证据 N 条
最近出现时间
```

灰色标签只保留低优先级元信息，不承担主分类表达。

### 右侧证据时间线

右侧展示选中 Memory 的详情：

1. 顶部标题：

```text
targetKeyText · memoryTypeText
```

2. Memory 正文：

```text
content
```

3. 证据时间线：

每条 evidence 显示：

```text
分数 + result 中文
题目快照
evidenceSummary
moduleTag
createdAt
```

结果颜色必须对齐测试页和结果页：

| result | 中文 | 颜色 |
| --- | --- | --- |
| `PERFECT` | 完美 | `#c8853b` |
| `CORRECT` | 正确 | `#4f8a67` |
| `DEFICIENT` | 缺漏 | `#d7b957` |
| `WRONG` | 错误 | `#b55a4c` |
| `UNKNOWN` | 不会 | `#7b8ca8` |
| 其它 | 原值或未评分 | 中性灰 |

时间线节点也使用对应结果色，而不是全部使用 Memory 分类色。

### 空状态

空状态分两类：

1. 全部 Memory 为空：

```text
暂无长期记忆
完成练习评估后，系统会基于真实作答和评分沉淀学习画像。
```

2. 当前分类为空：

```text
当前分类暂无画像
可以切换其它分类查看已有记忆。
```

### 隐藏能力

隐藏能力保留。

入口建议放在右侧详情区顶部或底部，避免出现在每张卡片上造成视觉噪声。

隐藏后：

- 当前 Memory 从列表消失。
- 如果当前分类还有其它 Memory，自动选中下一条。
- 如果当前分类为空，显示分类空状态。

## 前端数据处理

前端不再维护 Memory 枚举中文映射。

后端返回：

```text
memoryTypeText
targetTypeText
targetKeyText
```

前端只负责：

- 根据 `memoryType` 分组。
- 根据 `memoryType` 决定左侧分类数量和颜色。
- 根据 evidence `result` 决定结果 badge 配色。
- 使用后端返回的中文字段展示标题和元信息。

## GenerateAgent 使用边界

Memory 仍只允许影响 `PlanAgent`。

`UserMemoryProvider` 输出给 Plan 的精简 JSON 应同步 targetType 语义：

```json
{
  "memoryType": "UNCLEAR",
  "memoryTypeText": "理解不稳",
  "targetType": "MODULE_TAG",
  "targetKey": "SpringFramework",
  "targetKeyText": "SpringFramework",
  "content": "用户在 SpringFramework 机制题上理解不稳定。",
  "supportCount": 2
}
```

PlanAgent 可以据此调整：

- 模块规划。
- 题量分配。
- 难度起点。
- `retrievalQueries` 的倾向。

Memory 不传给 DraftAgent，不参与答案内容、资料证据、`sourceChunkIds` 或 `sourceReliable`。

## 风险与边界

### 旧数据兼容

如果本地已有旧 `BEHAVIOR / GENERAL` 数据，前端可以短期降级显示原始值，但开发期 seed 应直接改成新语义。

生产环境如已有数据，需要单独迁移：

- `BEHAVIOR + UNSTRUCTURED_ANSWER` 可迁移到 `ANSWER_SKILL + 内容/结构回答的完整性`。
- `BEHAVIOR + MISSING_TRADEOFF` 可迁移到 `ANSWER_SKILL + 原因/场景分析的逻辑性`。
- `GENERAL` 不建议机械迁移，应该隐藏、删除或人工映射到具体对象。

### 分类数量

左侧只有三个分类，意味着 `ANSWER_SKILL` 不作为第一层导航。这样更符合用户认知：先看表现状态，再看具体对象。

### Prompt 输出质量

`ANSWER_SKILL` 是中性能力维度，仍需 prompt 明确要求：

- 不要把所有表达问题都塞进 `内容/结构回答的完整性`。
- 能归入模块时优先使用 `MODULE_TAG`。
- 只有跨多个模块反复出现的能力问题才使用 `ANSWER_SKILL`。

## 最终方案摘要

后端：

- `targetType` 收敛为 `MODULE_TAG / ANSWER_SKILL`。
- 新增 `AnswerSkill` 常量池。
- 删除 `GENERAL / BEHAVIOR / BehaviorKey`。
- Prompt、Cleaner、Repository 映射同步更新。

前端：

- Memory 页改为三栏工作台。
- 左侧按 `MASTER / UNCLEAR / AWFUL` 显示三类学习画像。
- 中间卡片保持统一白色/主题黄风格，不用状态色边线。
- 右侧证据时间线按测试页结果配色展示真实答题结果。
- 只做桌面 Web 侧体验。
