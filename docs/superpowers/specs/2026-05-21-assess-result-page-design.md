# Assess Result Page Design

## 1. 目标

提交整轮练习后，结果页不再展示为一组平铺卡片，而是形成一份可阅读的练习报告。

页面需要同时满足两类信息诉求：

1. 做题统计：用户能快速知道本轮分数、达标率、完成数、正确 / 缺漏 / 错误 / 不会分布。
2. 整轮分析：用户能理解自己为什么失分、优势在哪里、下一步应该怎么复习。

本页定位为“统计看板 + 诊断报告”，不是后台表单页，也不是单纯成绩单。

## 2. 设计方向

采用已确认的 A 方向：诊断报告型。

信息顺序固定为：

1. 总评与核心统计。
2. 结果分布。
3. 整轮分析。
4. 复习行动。
5. 题目明细。

视觉上延续当前项目的暖纸色背景、墨色文字、棕金辅助色、蓝灰 / 暖炭灰克制强调色，并沿用刷题页中的绿色、红色、黄色结果语义。

## 3. 页面结构

### 3.1 第一屏：总评封面

第一屏由左右两块组成。

左侧为整轮总评：

- 小标题：`Practice assessment` 或中文等价文案。
- 主标题：优先使用 `session.summary`，其次使用 `assessDetail.overallComment`。
- 描述：展示整轮关键判断，避免继续堆多个独立卡片。
- 元信息胶囊：题集、反馈模式、用时或完成时间。

右侧为核心成绩：

- 达标率作为主视觉，使用环形或强视觉数字。
- 总分。
- 完成数，例如 `18 / 20`。

设计原则：

- 第一屏必须让用户立即知道本轮表现和整体判断。
- 不把所有指标都塞进第一屏，避免信息噪声。
- 主评语是视觉主角，分数是证据，不反过来。

### 3.2 第二层：结果分布

用四个横向指标展示：

- 正确 / 完全掌握：`correctCount`
- 缺漏 / 需补全：`deficientCount`
- 错误 / 需重做：`wrongCount`
- 不会 / 建议回炉：`unknownCount`

颜色语义：

- 正确：绿色。
- 缺漏：黄色或棕金弱强调。
- 错误：红色。
- 不会：黄色，和刷题答题卡保持一致。

这层用于解释达标率和分数，不承载长文本。

### 3.3 第三层：整轮分析

使用左右双栏：

- 做得好的地方：来自 `assessDetail.strengths`
- 需要补的地方：来自 `assessDetail.weaknesses`

每条分析固定展示：

- 标题：`AssessPoint.title`
- 模块标签：可选展示 `AssessPoint.moduleTag`
- 分析正文：`AssessPoint.analysis`

如果某项没有标题，则降级使用模块标签；如果都没有，则显示“未命名要点”。

设计原则：

- 每条 insight 是“标题 + 分析”，不要只展示一段散文。
- 优势和薄弱点并列，让用户形成对比。
- 薄弱点需要更高视觉权重，因为它直接影响下一步行动。

### 3.4 第四层：复习行动

使用横向 step strip 展示复习路径。

当前后端 `AssessDetail` 已包含：

- `reviewGuidance`

前端第一版可以将 `reviewGuidance` 拆为一到三段展示；如果无法可靠拆分，则作为一整段行动建议展示。

如果后续后端恢复或新增结构化 `reviewSuggestions`，则映射为：

- 第一步：优先复盘薄弱模块。
- 第二步：重做错误和不会题。
- 第三步：开启同模块下一轮练习。

设计原则：

- 这一层必须回答“我接下来做什么”。
- 不再重复解释失分原因。
- 如果没有建议，展示温和空态，而不是隐藏整块区域。

### 3.5 第五层：题目明细

题目明细采用紧凑列表，而不是大卡片堆叠。

每行展示：

- 题号。
- 题干单行摘要。
- `feedbackSummary` 或状态摘要。
- 结果标签：正确 / 缺漏 / 错误 / 不会。
- 分数。

默认展示前若干条，支持展开全部。明细的定位是复盘入口，不抢整轮分析的视觉层级。

## 4. 数据映射

页面主要读取 `PracticeSessionDetail`：

- `session.score`
- `session.accuracy`
- `session.totalQuestions`
- `session.answeredCount`
- `session.correctCount`
- `session.deficientCount`
- `session.wrongCount`
- `session.unknownCount`
- `session.summary`
- `session.assessDetail.overallComment`
- `session.assessDetail.reviewGuidance`
- `session.assessDetail.strengths`
- `session.assessDetail.weaknesses`
- `items[].question`
- `items[].result`
- `items[].score`
- `items[].feedbackSummary`
- `items[].status`

当前前端类型中如果存在历史字段 `reviewSuggestions`，实现时不应依赖它作为必要字段；以后端当前 `AssessDetail` 为准。

## 5. 空态与异常

加载中：

- 显示简洁的结果读取状态。

加载失败：

- 保留返回测试页入口。

评估为空：

- 仍展示做题统计。
- 分析区展示“暂无整轮分析，已保存本轮作答记录。”

题目为空：

- 隐藏题目明细列表，保留统计和分析。

未完成 session 进入结果页：

- 前端应跳回 `/practice/:sessionId` 或展示“本轮尚未完成”的提示。
- 不在结果页提供继续答题的复杂逻辑。

## 6. 响应式布局

桌面端：

- 第一屏双栏：左侧总评，右侧达标率。
- 分析区双栏。
- 复习行动三列。
- 题目明细列表。

移动端：

- 全部变为单列。
- 指标分布改为两列。
- 题目明细行改为题号 + 内容 + 结果标签的堆叠结构。
- 不使用横向滚动承载核心内容。

## 7. 实现边界

第一版只改前端展示：

- `frontend/src/pages/ResultPage.tsx`
- `frontend/src/styles/globals.css`

不改后端接口，不改 assess 生成逻辑。

如果实现时发现 `reviewGuidance` 过长或不可拆，前端只做段落展示，不在客户端强行生成不存在的结构化建议。

## 8. 验证标准

实现后需要验证：

1. `npm run typecheck`
2. `npm run build`
3. 浏览器检查 `/practice/:sessionId/result`
4. 桌面端第一屏不再是卡片堆叠。
5. 移动端没有文字溢出和横向滚动。
6. 无 assessDetail 时仍能正常展示统计。
7. 题目明细中长题干不会撑破布局。
