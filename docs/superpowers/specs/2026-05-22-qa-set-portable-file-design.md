# QA Set Portable File Design

日期：2026-05-22

## 1. 目标

本次设计为问答集增加一套文件级持久化能力：

1. 用户可以将一个问答集导出为本地文件。
2. 用户可以从本地文件导入问答集。
3. 导入失败时后端抛出明确异常，前端展示错误，不创建残缺数据。
4. 前端在新增题集时提供两个入口：从文件导入、利用资料创建。
5. 前端在问答集详情页提供导出问答集按钮。

该能力只处理题集资产本身，不处理练习历史、生成任务历史、资料引用和 RAG 索引。

## 2. 文件格式

采用 **`.dasi` 后缀 + JSON 内容**。

不使用 XML，原因：

1. 题集和题目天然是结构化对象，JSON 与当前 DTO、前端类型和后端 Jackson 解析更贴合。
2. JSON 更容易做 schema version、字段兼容和错误定位。
3. XML 对本场景没有额外收益，反而增加解析、转义和前端调试成本。

`.dasi` 是产品层文件后缀，内部内容为 UTF-8 JSON。

导入时只接受 `.dasi` 后缀文件。后端不能只依赖前端 accept 属性，必须二次校验文件名后缀。

## 3. 文件 Schema

第一版 schema：

```json
{
  "schemaVersion": 1,
  "app": "QA_Agent",
  "exportedAt": "2026-05-22 22:10:00",
  "qaSet": {
    "title": "SpringBoot 核心题集",
    "description": "围绕 SpringBoot 自动配置、Starter 和事务代理生成的面试题。",
    "moduleTags": ["SpringBoot", "SpringFramework"]
  },
  "items": [
    {
      "question": "Spring Boot 的自动配置和 Starter 有什么关系？",
      "answer": "Starter 负责聚合依赖，自动配置负责根据类路径和条件注解装配 Bean...",
      "knowledgeNote": "回答时先区分依赖聚合和 Bean 装配，再说明条件注解...",
      "moduleTag": "SpringBoot",
      "difficulty": "EASY",
      "keywords": "Starter,自动配置,条件注解",
      "hint": "先区分依赖引入和 Bean 装配两个层面。",
      "sourceReliable": true,
      "sortOrder": 1
    }
  ]
}
```

### 3.1 顶层字段

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `schemaVersion` | 是 | 当前固定为 `1` |
| `app` | 是 | 固定为 `QA_Agent`，用于避免误导入其他 JSON 文件 |
| `exportedAt` | 是 | 导出时间，只用于展示和排查 |
| `qaSet` | 是 | 题集资产 |
| `items` | 是 | 题目列表，至少 1 道题 |

### 3.2 `qaSet`

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `title` | 是 | 题集标题 |
| `description` | 否 | 题集描述 |
| `moduleTags` | 否 | 题集模块标签数组 |

导入后 `moduleTags` 转为当前表字段 `module_tags_json`。

### 3.3 `items`

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `question` | 是 | 题目 |
| `answer` | 否 | 标准回答 |
| `knowledgeNote` | 否 | 知识笔记 |
| `moduleTag` | 否 | 单题模块标签，兼容当前 `qa_item.module_tag` |
| `difficulty` | 否 | `EASY` / `MEDIUM` / `HARD`，非法值导入失败 |
| `keywords` | 否 | 逗号分隔关键词 |
| `hint` | 否 | 答前提示 |
| `sourceReliable` | 否 | 资料证据可靠性；缺省为 `false` |
| `sortOrder` | 否 | 题目顺序；缺省按文件顺序生成 |

## 4. 不导出的字段

以下字段不进入 `.dasi` 文件：

| 字段 | 原因 |
| --- | --- |
| `id` | 导入后必须生成新 ID |
| `userId` | 用户隔离字段不能跨账户迁移 |
| `taskId` | 生成任务历史不属于题集资产 |
| `practiceCount` / `averageScore` / `bestScore` / `averageAccuracy` / `bestAccuracy` / `lastPracticedAt` | 练习统计是运行态数据 |
| `documentCount` / `qa_set_document_ref` | 资料引用依赖当前用户资料库 |
| `sourceChunkIdsJson` | 来源切片 ID 只在原资料库内有效 |
| `completeStatus` | 导入后的题目已具备资产字段，统一写 `SOLVED` |
| `practice_session` / `practice_session_item` | 练习历史不属于导入导出范围 |

该取舍保证 `.dasi` 文件可以跨账号、跨环境导入，不依赖原数据库上下文。

## 5. 后端设计

### 5.1 Controller

在 `QaController` 中新增两个题集级接口：

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| `GET` | `/qa/set/export?id=...` | 导出当前用户自己的问答集 |
| `POST` | `/qa/set/import` | 上传 `.dasi` 文件并导入为新问答集 |

导出接口返回文件流：

- `Content-Type: application/octet-stream`
- `Content-Disposition: attachment; filename="<title>.dasi"`

导入接口使用 `multipart/form-data`：

```text
file: xxx.dasi
```

### 5.2 Service

相关服务直接放在现有 `QaSetService` 内，不新增独立 portable service。

`IQaSetService` 增加：

```java
QaSetExportResponse exportQaSet(String id);

QaSetResponse importQaSet(QaSetImportRequest request);
```

`QaSetService` 职责：

1. 获取当前用户 ID。
2. 调用 repository 查询导出所需题集和题目。
3. 调用 converter 生成 `.dasi` JSON 字节。
4. 导入时校验文件基础属性。
5. 调用 converter 解析 JSON。
6. 调用 repository 在事务内创建题集和题目。

### 5.3 Converter

新增 `QaSetPortableConverter`，位置建议：

```text
backend/qa-agent-domain/src/main/java/com/dasi/qa/agent/domain/qa/service/set/QaSetPortableConverter.java
```

职责：

1. 将 `QaSetResponse + List<QaItemResponse>` 转换为 portable schema。
2. 将 uploaded JSON 转换为导入 command / request。
3. 做 schema 层校验：`schemaVersion`、`app`、必填字段、difficulty 合法性、items 非空。
4. 负责 JSON 序列化和反序列化。

Converter 不访问数据库，不读取当前用户，不处理权限。

### 5.4 DTO

按用户要求，DTO 放到：

```text
backend/qa-agent-types/src/main/java/com/dasi/qa/agent/types/dto/request/qa
backend/qa-agent-types/src/main/java/com/dasi/qa/agent/types/dto/response/qa
```

建议新增：

```text
QaSetImportRequest
QaSetExportResponse
QaSetPortableFile
QaSetPortableSet
QaSetPortableItem
```

`QaSetImportRequest`：

```java
private MultipartFile file;
```

`QaSetExportResponse`：

```java
private String fileName;
private byte[] content;
```

`QaSetPortableFile` / `QaSetPortableSet` / `QaSetPortableItem` 是 `.dasi` 文件 schema 对应 DTO。

### 5.5 Repository

`IQaRepository` 增加：

```java
List<QaItemResponse> queryQaItemsBySetId(String qaSetId, String userId);

QaSetResponse importQaSet(QaSetPortableFile portableFile, String userId);
```

导出：

1. `detailQaSet(id, userId)` 复用现有归属校验。
2. `queryQaItemsBySetId(id, userId)` 查询当前用户该题集下所有题目，按 `sort_order`、`created_at` 排序。

导入：

1. 生成新的 `qa_set.id`。
2. 写入 `title`、`description`、`module_tags_json`、`question_count`。
3. 统计字段统一初始化为 0 或 null。
4. 逐条生成 `qa_item.id`。
5. `complete_status` 固定写 `SOLVED`。
6. `source_chunk_ids_json` 固定写 `[]`。
7. `sort_order` 优先使用文件内 `sortOrder`，为空则按文件顺序。
8. 整个导入方法必须加事务，任何题目失败都回滚。

## 6. 校验和异常

导入失败场景：

| 场景 | 行为 |
| --- | --- |
| 文件为空 | 抛业务异常 |
| 文件后缀不是 `.dasi` | 抛业务异常 |
| 文件不是合法 JSON | 抛业务异常 |
| `app != QA_Agent` | 抛业务异常 |
| `schemaVersion` 不支持 | 抛业务异常 |
| `qaSet.title` 为空 | 抛业务异常 |
| `items` 为空 | 抛业务异常 |
| 某道题 `question` 为空 | 抛业务异常 |
| `difficulty` 非法 | 抛业务异常 |
| 字段长度超过表结构限制 | 抛业务异常 |

异常沿用当前项目风格：

- 不新增散乱异常体系。
- 使用 `ApiException(ResultCode.X)`。
- 如果现有 `ResultCode` 无法表达导入文件错误，可新增一个明确的业务码，例如 `QA_SET_FILE_INVALID`。

## 7. 前端设计

### 7.1 新增题集入口

当前左侧【新增题集】不再直接跳转 `/create`，改为打开选择弹窗。

弹窗标题：

```text
新增题集
```

弹窗提供两个大按钮：

1. **从文件导入**
   - 文件图标。
   - 副文案：`选择 .dasi 文件，恢复一份本地题集。`
   - 点击后触发隐藏 file input。
   - `accept=".dasi"`。

2. **利用资料创建**
   - 资料 / sparkle 类图标。
   - 副文案：`选择资料，让系统生成新的问答集。`
   - 点击后跳转 `/create`。

视觉要求：

- 延续现有暖纸色、墨色文字、棕金边框和浅色面板。
- 不使用黑色输入框。
- 主按钮使用当前主题色，不做高饱和红色。
- 两个选择项可以是并列的浅色 action tile，不做普通堆叠表单。

### 7.2 导出按钮

在问答集详情页操作区中，放在【删除问答集】右侧：

```text
导出问答集
```

样式建议：

- `variant="soft"` 或 `variant="outline"`。
- 强度低于【开始练习】。
- 和【删除问答集】保持同一行，但不要使用危险色。

点击后调用导出接口并下载文件。

文件名建议：

```text
<题集标题>.dasi
```

前端需要对标题中的文件非法字符做替换；后端也应返回安全文件名。

### 7.3 导入成功和失败

导入成功：

1. 关闭弹窗。
2. 刷新题集列表。
3. 自动跳转到新题集详情页。
4. 展示轻提示：`导入成功，共 X 道题`。

导入失败：

1. 弹窗不关闭。
2. 展示后端错误信息。
3. 不创建残缺题集。

## 8. 前端 API

`frontend/src/lib/api/hooks.ts` 增加：

```ts
useImportQuestionSetMutation()
useExportQuestionSetMutation()
```

导入：

```ts
POST /qa/set/import
Content-Type: multipart/form-data
```

导出：

```ts
GET /qa/set/export?id=...
Response: Blob
```

`frontend/src/lib/api/types.ts` 可增加导入响应类型，复用现有 `QuestionSet` 作为成功返回。

## 9. 数据流

### 9.1 导出

```mermaid
flowchart LR
    A["用户点击导出问答集"] --> B["GET /qa/set/export?id"]
    B --> C["QaSetService 校验当前用户并查询题集"]
    C --> D["查询题目列表"]
    D --> E["QaSetPortableConverter 生成 .dasi JSON"]
    E --> F["Controller 返回文件流"]
    F --> G["浏览器下载 .dasi 文件"]
```

### 9.2 导入

```mermaid
flowchart LR
    A["用户选择 .dasi 文件"] --> B["POST /qa/set/import"]
    B --> C["QaSetService 校验后缀和空文件"]
    C --> D["QaSetPortableConverter 解析并校验 schema"]
    D --> E["QaRepository 事务创建 qa_set 和 qa_item"]
    E --> F["返回新 QaSetResponse"]
    F --> G["前端刷新列表并跳转详情页"]
```

## 10. 文档更新

实现时需要同步更新：

1. `docs/API.md`
   - 增加 `/qa/set/export`
   - 增加 `/qa/set/import`
   - 说明 `.dasi` 文件格式和导入失败行为

2. `docs/TABLE.md`
   - 不需要新增表字段
   - 可以补充说明 `.dasi` 是题集资产交换格式，不对应数据库新表

## 11. 验证计划

后端：

1. `cd backend && mvn test`
2. `git diff --check`
3. 手动验证：
   - 导出自己的题集成功。
   - 导出他人题集返回 forbidden。
   - 导入合法 `.dasi` 成功。
   - 导入非 `.dasi` 文件失败。
   - 导入非法 JSON 失败。
   - 导入字段缺失失败。
   - 导入失败不产生 `qa_set` / `qa_item` 残留。

前端：

1. `cd frontend && npm run typecheck`
2. `cd frontend && npm run build`
3. 浏览器验证：
   - 点击【新增题集】出现二选一弹窗。
   - 【利用资料创建】进入现有 `/create`。
   - 【从文件导入】只选择 `.dasi`。
   - 导入成功后跳转新题集。
   - 导入失败展示错误。
   - 【导出问答集】下载 `.dasi` 文件。

## 12. 已确认取舍

1. `.dasi` 内部使用 JSON，不使用 XML。
2. 第一版只导出题集资产，不导出资料引用和练习历史。
3. 导入后生成新的题集和题目 ID。
4. 导入题目的 `completeStatus` 统一为 `SOLVED`。
5. 导入题目的 `sourceChunkIdsJson` 统一为空数组。
6. 服务直接放在 `QaSetService`，不新增独立 portable service。
7. DTO 放在 `types/dto/request/qa` 和 `types/dto/response/qa`。
8. 后端是导入校验权威来源，前端的 `.dasi` accept 只做体验限制。
