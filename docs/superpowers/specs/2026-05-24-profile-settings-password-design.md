# Profile 设置页改版与修改密码接口设计

## 1. 背景与目标

当前 Profile 页是单页表单，账户信息、求职信息、智能体配置和能力开关都堆在同一页里。随着 V6 Memory、密码修改、智能体配置继续增加，单页纵向堆叠会让信息层级变差，也不利于后续扩展。

本次改动目标：

1. 只调整 Profile 页主内容区域，做成“左侧目录 + 右侧内容”的设置页结构。
2. 顶部导航保持现状，不新增 Profile 顶部 tab，不修改顶部页眉组件样式。
3. Profile 使用多路由拆分：个人、记忆、智能体。
4. 个人页保留头像修改能力，并新增修改密码表单。
5. 后端新增独立的修改密码接口，要求校验旧密码，不能复用通用账号更新接口。

本次不实现 V6 Memory 的真实数据表、记忆录入、记忆列表和记忆详情。`/profile/memory` 只保留页面入口和空状态，后续等 Memory 字段确定后再接入。

## 2. 非目标

1. 不修改 `Topbar`、`NavCapsule`、顶部导航样式和顶部导航栏目。
2. 不新增顶部 Profile tab。进入 Profile 仍然通过右上角头像。
3. 不改登录、注册、会话刷新、鉴权跳转逻辑。
4. 不新增智能体滑块参数。“滑块选项”按当前语义理解为现有能力开关。
5. 不调整 V6 Memory 后端领域模型、数据库表和 MQ 消费链路。
6. 不重构 Profile 之外的页面视觉系统。

## 3. 路由设计

现状只有：

- `/profile`

改为：

- `/profile`：重定向到 `/profile/info`
- `/profile/info`：个人
- `/profile/memory`：记忆
- `/profile/config`：智能体

右上角头像入口保持原样：仍然是点击头像进入 Profile。实现时可以只把头像链接目标从 `/profile` 调整为 `/profile/info`，但不修改 `Topbar` 的样式、布局、类名和导航栏目。如果保留头像链接到 `/profile`，也可以依靠 `/profile -> /profile/info` 重定向达到同样效果。

## 4. 前端结构设计

推荐拆分为以下组件：

- `ProfileLayout`
  - Profile 主内容的大卡片容器。
  - 左侧目录：`个人`、`记忆`、`智能体`。
  - 右侧使用 `Outlet` 渲染子路由。
- `ProfileInfoPage`
  - 账户信息。
  - 头像修改组件。
  - 求职信息表单。
  - 修改密码表单。
- `ProfileMemoryPage`
  - 先展示空状态。
  - 不渲染假的记忆数据。
- `ProfileConfigPage`
  - 模型配置。
  - 风格提示词。
  - 能力开关。

保留并复用现有能力：

- `ProfileAvatarCropper`
- `useProfileQuery`
- `useSaveProfileMutation`
- `useUploadAvatarMutation`
- 当前 `Profile` 类型里的字段

新增：

- `ChangePasswordInput`
- `useChangePasswordMutation`

如果实现时发现 `ProfilePage.tsx` 继续膨胀，会拆出 `frontend/src/components/profile/` 下的表单片段，避免一个页面文件承载所有布局、表单和头像裁剪逻辑。

## 5. 页面信息架构

### 5.1 Profile 总体布局

主内容区使用一个大的设置卡片。卡片内部左侧是目录，右侧是当前路由内容。

左侧目录只展示文字，不使用 `01`、`02`、`03` 这类编号，因为这些分区没有先后步骤含义。

目录项：

- 个人
- 记忆
- 智能体

桌面端：

- 左侧目录固定宽度。
- 右侧内容区自适应。
- 卡片整体保持当前项目的柔和玻璃质感，但避免把每个表单区都做成嵌套卡片。

移动端：

- 主卡片保留。
- 左侧目录改为顶部横向分段或紧凑列表。
- 右侧内容在下方展示。
- 顶部导航仍保持当前响应式行为，不在本次修改范围内调整。

### 5.2 个人页

个人页包含三组内容。

账户信息：

- 头像修改入口。
- 用户名只读。
- 邮箱只读。

头像修改：

- 使用现有头像裁剪组件。
- 上传成功后刷新当前用户信息。
- 不改变 OSS 上传流程。

求职信息：

- 目标岗位。
- 目标领域。
- 目标公司。
- 当前阶段。
- 专业。
- 年级。

修改密码：

- 当前密码。
- 新密码。
- 确认新密码。
- 单独提交，不和求职信息保存绑定。

密码表单前端校验：

- 当前密码不能为空。
- 新密码至少 8 位。
- 确认新密码必须和新密码一致。
- 新密码不能和当前密码完全相同。
- 成功后清空三个密码输入框。

### 5.3 记忆页

记忆页本次只做结构占位：

- 显示“记忆能力待接入”类空状态。
- 不展示假数据。
- 不设计筛选、删除、编辑、可信度字段。

原因：V6 Memory 的字段、沉淀规则、证据引用和可靠性模型还未最终确认，提前做可交互列表容易造成前端数据契约返工。

### 5.4 智能体页

智能体页包含三组内容。

模型配置：

- Base URL。
- API Key。
- 模型名称。

风格提示词：

- 答案风格 `answerStyle`。
- 反馈风格 `feedbackStyle`。

能力开关：

- 允许通用知识补充 `allowGeneralKnowledge`。
- 允许联网检索 `allowWebSearch`。
- 允许兜底策略 `allowFallback`。

这些字段沿用现有 `Profile` 保存接口，不新增数据库字段。

## 6. 后端修改密码接口设计

### 6.1 接口

新增接口：

`POST /identity/account/password`

鉴权：是。

请求体：

```json
{
  "currentPassword": "old-password",
  "newPassword": "new-password"
}
```

响应：

```json
{
  "code": 0,
  "msg": "success",
  "data": null
}
```

### 6.2 为什么不用 `/identity/account/update`

`/identity/account/update` 是通用账号更新接口，当前依赖请求体里的 `id`，且只负责把传入密码编码后更新。它不适合修改密码：

1. 修改密码必须基于当前登录用户，不能让前端传 userId 决定修改谁。
2. 修改密码必须校验旧密码。
3. 修改密码不应该顺带修改用户名、邮箱、状态、头像。

因此新增独立接口更清晰，风险也更小。

### 6.3 后端分层

新增请求 DTO：

- `ChangePasswordRequest`
  - `currentPassword`
  - `newPassword`

`IdentityController`：

- 新增 `POST /identity/account/password`。
- 只接收 `ChangePasswordRequest`。
- 不接收 userId。

`IProfileCrudService` / `ProfileCrudService`：

- 新增 `changePassword(ChangePasswordRequest request)`。
- 通过 `IContextUtil.getUserId()` 获取当前用户。
- 读取当前账号。
- 使用 `PasswordEncoder.matches(currentPassword, passwordHash)` 校验旧密码。
- 使用 `PasswordEncoder.encode(newPassword)` 更新密码。

`IIdentityRepository` / `IdentityRepository`：

- 需要能读取用户密码哈希。
- 需要按当前 userId 更新密码。
- 更新后清理账号缓存。

### 6.4 校验规则

后端校验：

- 当前密码不能为空。
- 新密码不能为空。
- 新密码长度至少 8 位。
- 新密码不能和当前密码相同。
- 当前密码不匹配时返回明确错误。

错误码策略：

- 可以新增 `PASSWORD_INVALID`，用于当前密码错误。
- 参数缺失、长度不足、新旧相同可以复用 `BAD_REQUEST`。

如果为了最小改动不新增错误码，也可以全部返回 `BAD_REQUEST`，但前端提示会更粗。本设计推荐新增一个“当前密码错误”的明确错误码。

## 7. 前端 API 设计

新增类型：

```ts
export type ChangePasswordInput = {
    currentPassword: string;
    newPassword: string;
};
```

新增 Hook：

```ts
export function useChangePasswordMutation()
```

行为：

- 调用 `POST /identity/account/password`。
- 成功后不需要刷新用户信息。
- 成功后由页面清空密码表单。
- 失败时沿用全局错误处理。

## 8. 文档更新

需要更新：

- `docs/API.md`
  - 在 Identity 账号接口中新增 `/identity/account/password`。
  - 说明该接口只修改当前登录用户密码。

不需要更新：

- `docs/TABLE.md`
  - 不新增表。
  - `user_account.password` 字段已存在。

## 9. 风险与边界

1. 顶部导航误改风险  
   本次必须避免修改顶部导航样式。若需要调整头像链接目标，只允许改路径，不改样式和布局。

2. Profile 表单拆分后的保存风险  
   个人页和智能体页都使用同一个 Profile 保存接口。实现时要避免一个页面保存时把另一个页面未加载或未填写的字段覆盖为空。

3. 密码哈希读取风险  
   现有 `UserAccountResponse` 如果包含 password 字段，后端可以复用；如果响应对象不应暴露 password，仓储层应新增内部读取方法，而不是把密码哈希返回给前端。

4. `allowFallback` 文档不一致风险  
   当前前端和 Profile 归一化逻辑已经使用 `allowFallback`，但 `docs/API.md` 说明它“不在公开请求/响应 DTO 中暴露”。实现时需要确认后端 DTO 当前实际状态，并把文档修正到真实接口契约。

5. Memory 占位误解风险  
   `/profile/memory` 只是结构占位，不代表 V6 Memory 字段已确定，也不代表后端已实现记忆领域。

## 10. 验证计划

后端：

- `cd backend && mvn -DskipTests package`

前端：

- `cd frontend && npm run typecheck`
- `cd frontend && npm run build`

手动验证：

- 点击右上角头像仍进入 Profile。
- 顶部导航视觉与当前保持一致。
- `/profile` 自动进入 `/profile/info`。
- `/profile/info` 可以修改头像、保存求职信息、修改密码。
- `/profile/memory` 显示空状态。
- `/profile/config` 可以保存模型配置、风格提示词和能力开关。
- 旧密码错误时修改密码失败并显示错误。
- 新密码与确认密码不一致时前端拦截提交。
