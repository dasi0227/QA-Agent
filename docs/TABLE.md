# QA_Agent 数据库设计

本文以以下代码为准：

- MySQL DDL：`backend/sql/init_mysql.sql`
- PostgreSQL DDL：`backend/sql/init_postgres.sql`
- Entity：`backend/qa-agent-infrastructure/src/main/java/com/dasi/qa/agent/infrastructure/persistent/entity/`

当前正式表结构为 **12 张 MySQL 表 + 1 张 PostgreSQL 检索表**。

## 1. MySQL 表清单

### 1.1 用户层

1. `user_account`
2. `user_profile`

### 1.2 资料层

3. `source_document`
4. `document_chunk`

### 1.3 生成任务层

5. `qa_generation_task`
6. `qa_generation_task_message`

### 1.4 问答资产层

7. `qa_set`
8. `qa_set_document_ref`
9. `qa_item`

### 1.5 练习层

10. `practice_session`
11. `practice_session_item`

### 1.6 消息任务层

12. `message_job`

## 2. PostgreSQL 表清单

1. `chunk_search`

## 3. 逐表说明

### 3.1 `user_account`

用途：账号主表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `CHAR(36)` | 主键 |
| `username` | `VARCHAR(100)` | 用户名，唯一 |
| `email` | `VARCHAR(200)` | 邮箱，唯一 |
| `password` | `VARCHAR(255)` | 密码哈希 |
| `status` | `VARCHAR(32)` | 当前实现使用 `ACTIVE` / `DISABLED` |
| `avatar` | `VARCHAR(512)` | OSS object key 或默认头像 key |
| `created_at` | `DATETIME` | 创建时间 |
| `updated_at` | `DATETIME` | 更新时间 |

### 3.2 `user_profile`

用途：用户画像和 Agent 默认配置。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `user_id` | `CHAR(36)` | 主键，外键到 `user_account.id` |
| `target_role` | `VARCHAR(120)` | 目标岗位 |
| `target_domain` | `VARCHAR(120)` | 目标领域 |
| `target_company` | `VARCHAR(120)` | 目标公司 |
| `allow_refer_memory` | `TINYINT(1)` | 生成规划是否允许参考长期记忆 |
| `allow_web_search` | `TINYINT(1)` | 生成链路是否允许 Web 补充 |
| `allow_fallback` | `TINYINT(1)` | Plan 失败时是否允许兜底规划，默认 `0` |
| `answer_style` | `VARCHAR(255)` | 答案风格 |
| `feedback_style` | `VARCHAR(255)` | 反馈风格 |
| `grade` | `VARCHAR(64)` | 年级 |
| `major` | `VARCHAR(128)` | 专业 |
| `stage` | `VARCHAR(128)` | 当前阶段 |
| `llm_base_url` | `VARCHAR(500)` | 用户自配 LLM 端点 |
| `llm_api_key` | `VARCHAR(255)` | 用户自配 LLM Key |
| `llm_model_name` | `VARCHAR(100)` | 用户自配模型名 |
| `created_at` | `DATETIME` | 创建时间 |
| `updated_at` | `DATETIME` | 更新时间 |

补充：

1. `allow_fallback` 已在公开 Profile API DTO 中暴露，用于控制 Plan 失败时是否允许兜底规划。
2. 三条 Agent 链路都依赖 `llm_*` 字段构造用户专属模型。

### 3.3 `source_document`

用途：资料主表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `CHAR(36)` | 主键 |
| `user_id` | `CHAR(36)` | 用户隔离字段 |
| `file_name` | `VARCHAR(255)` | 文件名 |
| `file_type` | `VARCHAR(32)` | 当前主要为 `MARKDOWN` |
| `file_path` | `VARCHAR(500)` | 可选路径 |
| `raw_content` | `LONGTEXT` | 原始正文 |
| `index_status` | `VARCHAR(32)` | `INDEXING` / `FINISHED` / `UNSOLVED` |
| `reference_count` | `INT` | 被题集引用次数 |
| `created_at` | `DATETIME` | 创建时间 |
| `updated_at` | `DATETIME` | 更新时间 |

### 3.4 `document_chunk`

用途：Markdown 切片业务真数据。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `CHAR(36)` | 主键，和 `chunk_search.chunk_id` 对齐 |
| `document_id` | `CHAR(36)` | 外键到 `source_document.id` |
| `user_id` | `CHAR(36)` | 用户隔离字段 |
| `chunk_index` | `INT` | 在资料中的顺序 |
| `heading_path` | `VARCHAR(500)` | 章节路径，如 `Redis > 跳表` |
| `content` | `LONGTEXT` | 切片正文 |
| `summary` | `LONGTEXT` | 由 summarizer LLM 生成的摘要 |
| `created_at` | `DATETIME` | 创建时间 |
| `updated_at` | `DATETIME` | 更新时间 |

约束：

1. `document_id + chunk_index` 唯一。
2. 删除资料时会同步删除该表对应切片。

### 3.5 `qa_generation_task`

用途：问答集生成任务主表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `CHAR(36)` | 主键，taskId |
| `user_id` | `CHAR(36)` | 用户隔离字段 |
| `title` | `VARCHAR(255)` | 请求标题 |
| `user_prompt` | `LONGTEXT` | 用户生成要求 |
| `document_ids_json` | `JSON` | 资料 ID 数组 |
| `qa_set_id` | `CHAR(36)` | 成功生成后的题集 ID |
| `status` | `VARCHAR(32)` | `PENDING` / `PROCESSING` / `SOLVED` / `CANCELED` / `UNSOLVED` |
| `stage` | `VARCHAR(32)` | 当前展示阶段，存 `GeneratePhase.generateStage` 文案 |
| `error_code` | `VARCHAR(64)` | `AgentErrorType` 名称 |
| `error_message` | `LONGTEXT` | 错误详情 |
| `allow_refer_memory` | `TINYINT(1)` | 任务启动时是否允许参考长期记忆的快照 |
| `allow_web_search` | `TINYINT(1)` | 任务启动时快照 |
| `requested_question_count` | `INT` | 请求题数 |
| `created_at` | `DATETIME` | 创建时间 |
| `started_at` | `DATETIME` | 首次进入执行时写入 |
| `completed_at` | `DATETIME` | 终态时间 |
| `updated_at` | `DATETIME` | 更新时间 |

### 3.6 `qa_generation_task_message`

用途：SSE 阶段消息留档。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `CHAR(36)` | 主键 |
| `task_id` | `CHAR(36)` | 外键到 `qa_generation_task.id` |
| `user_id` | `CHAR(36)` | 用户 ID |
| `stage` | `VARCHAR(32)` | 阶段文案 |
| `message` | `LONGTEXT` | 阶段消息摘要 |
| `content` | `LONGTEXT` | 完整 `SseEvent` JSON |
| `created_at` | `DATETIME` | 创建时间 |

### 3.7 `qa_set`

用途：问答集资产主表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `CHAR(36)` | 主键 |
| `user_id` | `CHAR(36)` | 用户隔离字段 |
| `task_id` | `CHAR(36)` | 来源任务，可空 |
| `title` | `VARCHAR(255)` | 题集标题 |
| `description` | `LONGTEXT` | 题集概述 |
| `module_tags_json` | `JSON` | 题集内标签汇总 |
| `question_count` | `INT` | 题目总数 |
| `practice_count` | `INT` | 已完成练习次数 |
| `average_score` | `INT` | 练习平均分 |
| `best_score` | `INT` | 最佳分数 |
| `average_accuracy` | `DECIMAL(10,2)` | 平均达标率 |
| `best_accuracy` | `DECIMAL(10,2)` | 最佳达标率 |
| `last_practiced_at` | `DATETIME` | 最近完成练习时间 |
| `created_at` | `DATETIME` | 创建时间 |
| `updated_at` | `DATETIME` | 更新时间 |

### 3.8 `qa_set_document_ref`

用途：题集和资料的多对多引用关系。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `CHAR(36)` | 主键 |
| `qa_set_id` | `CHAR(36)` | 题集 ID |
| `document_id` | `CHAR(36)` | 资料 ID |
| `created_at` | `DATETIME` | 创建时间 |

约束：`qa_set_id + document_id` 唯一。

### 3.9 `qa_item`

用途：问答集中的单题。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `CHAR(36)` | 主键 |
| `qa_set_id` | `CHAR(36)` | 所属题集 |
| `user_id` | `CHAR(36)` | 用户隔离字段 |
| `question` | `LONGTEXT` | 面试题目 |
| `knowledge_note` | `LONGTEXT` | 复习笔记 |
| `answer` | `LONGTEXT` | 标准回答 |
| `module_tag` | `VARCHAR(120)` | 模块标签 |
| `difficulty` | `VARCHAR(32)` | `EASY` / `MEDIUM` / `HARD` |
| `keywords` | `LONGTEXT` | AssistAgent 异步补全的答题关键词，逗号分隔 |
| `hint` | `LONGTEXT` | AssistAgent 异步补全的答前轻提示 |
| `source_reliable` | `TINYINT(1)` | 资料证据是否足以支撑主要答案，默认 `1` |
| `is_imported` | `TINYINT(1)` | 是否来自 .dasi 导入，默认 `0` |
| `source_chunk_ids_json` | `JSON` | 来源切片 ID 数组 |
| `complete_status` | `VARCHAR(32)` | 手动题智能补全状态：`PROCESSING` / `SOLVED` / `UNSOLVED` |
| `sort_order` | `INT` | 题目顺序 |
| `created_at` | `DATETIME` | 创建时间 |
| `updated_at` | `DATETIME` | 更新时间 |

### 3.9.1 `.dasi` 题集文件

`.dasi` 是问答集资产交换格式，不对应新增数据库表。

第一版 `.dasi` 文件内部是 UTF-8 JSON，只保存：

- 题集标题、描述、模块标签；
- 题目问题、标准回答、知识笔记、模块、难度、关键词、答前提示、资料可靠性和排序。

导出时不写入：

- `id`、`user_id`、`task_id`；
- 练习统计字段；
- `qa_set_document_ref`；
- `source_chunk_ids_json`；
- `practice_session` / `practice_session_item`。

导入后会生成新的 `qa_set` 和 `qa_item` 数据，练习统计归零，题目 `complete_status` 固定为 `SOLVED`，`source_chunk_ids_json` 固定为 `[]`。

### 3.10 `practice_session`

用途：一轮练习会话。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `CHAR(36)` | 主键 |
| `user_id` | `CHAR(36)` | 用户隔离字段 |
| `qa_set_id` | `CHAR(36)` | 对应题集 |
| `mode` | `VARCHAR(32)` | 练习模式 |
| `feedback_mode` | `VARCHAR(32)` | 反馈模式 |
| `status` | `VARCHAR(32)` | 当前状态 |
| `selected_module` | `VARCHAR(120)` | 按模块练习时的模块名 |
| `total_questions` | `INT` | 本轮题数 |
| `answered_count` | `INT` | 已回答题数 |
| `current_index` | `INT` | 最近停留题号，用于恢复进度 |
| `last_active_at` | `DATETIME` | 最近练习活跃时间 |
| `duration_seconds` | `INT` | 累计活跃作答秒数，以前端传入累计值和后端 max 规则持久化，不用 `finished_at - started_at` 推算 |
| `score` | `INT` | 整轮平均分 |
| `accuracy` | `DECIMAL(10,2)` | 整轮达标率 |
| `perfect_count` | `INT` | `PERFECT` 数量 |
| `correct_count` | `INT` | `CORRECT` 数量 |
| `deficient_count` | `INT` | `DEFICIENT` 数量 |
| `wrong_count` | `INT` | `WRONG` 数量 |
| `unknown_count` | `INT` | `UNKNOWN` 数量 |
| `summary` | `LONGTEXT` | 整轮摘要，当前等于 `assessDetail.overallComment` |
| `assessment_detail_json` | `JSON` | 整轮用户可读评估详情 |
| `started_at` | `DATETIME` | 开始时间 |
| `finished_at` | `DATETIME` | 首次完成时间 |
| `created_at` | `DATETIME` | 创建时间 |
| `updated_at` | `DATETIME` | 更新时间 |

### 3.11 `practice_session_item`

用途：练习会话中的单题作答结果。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `CHAR(36)` | 主键 |
| `user_id` | `CHAR(36)` | 用户隔离字段 |
| `session_id` | `CHAR(36)` | 所属练习会话 |
| `qa_item_id` | `CHAR(36)` | 对应题目 |
| `sort_order` | `INT` | 本轮顺序 |
| `user_answer` | `LONGTEXT` | 用户回答 |
| `status` | `VARCHAR(32)` | `UNANSWERED` / `DRAFT` / `UNKNOWN` / `SUBMITTED` |
| `unknown` | `TINYINT(1)` | 是否标记不会 |
| `result` | `VARCHAR(32)` | 可空，`PERFECT` / `CORRECT` / `DEFICIENT` / `WRONG` / `UNKNOWN` |
| `score` | `INT` | 单题分数 |
| `feedback_summary` | `LONGTEXT` | 单句反馈摘要 |
| `feedback_judge_detail` | `TEXT` | `JudgeDetail` JSON |
| `feedback_hint_detail` | `TEXT` | `HintDetail` JSON |
| `answered_at` | `DATETIME` | 最近反馈写入时间 |
| `submitted_at` | `DATETIME` | 本题提交时间 |
| `question_snapshot` | `LONGTEXT` | 作答时题干快照 |
| `standard_answer_snapshot` | `LONGTEXT` | 作答时标准答案快照 |
| `knowledge_note_snapshot` | `LONGTEXT` | 作答时知识笔记快照 |
| `keywords_snapshot` | `LONGTEXT` | 作答时关键词快照 |
| `hint_snapshot` | `LONGTEXT` | 作答时答前提示快照 |
| `module_tag_snapshot` | `VARCHAR(120)` | 作答时模块标签快照 |
| `difficulty_snapshot` | `VARCHAR(32)` | 作答时难度快照 |
| `source_chunk_ids_snapshot_json` | `JSON` | 作答时来源切片快照 |
| `created_at` | `DATETIME` | 创建时间 |
| `updated_at` | `DATETIME` | 更新时间 |

说明：

1. 当前没有 `feedback_detail_json` 总字段，已经拆成 `feedback_judge_detail` 和 `feedback_hint_detail`。
2. UNKNOWN 分支只写 `feedback_hint_detail`；普通判题分支只写 `feedback_judge_detail`。
3. `AFTER_ALL` 提交本轮前通常为 `UNANSWERED` / `DRAFT` / `UNKNOWN`；提交本轮后统一写入 `SUBMITTED`、`result`、`score` 和反馈字段。

### 3.12 `message_job`

用途：Kafka 消息发送记录和重试追踪。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `VARCHAR(36)` | 主键 |
| `job_id` | `VARCHAR(64)` | 业务唯一 jobId |
| `job_status` | `VARCHAR(20)` | `UNSOLVED` / `SUCCESS` / `FAIL` |
| `job_retry` | `INT` | 已重试次数 |
| `message_topic` | `VARCHAR(100)` | Kafka topic |
| `message_content` | `TEXT` | 消息正文 |
| `error_message` | `LONGTEXT` | 最近一次消费失败原因，仅后端排查使用 |
| `message_first_sent_at` | `TIMESTAMP` | 首次发送时间 |
| `message_latest_sent_at` | `TIMESTAMP` | 最近发送时间 |
| `created_at` | `TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 更新时间 |

### 3.13 `user_memory`

用途：用户长期学习画像结论。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `CHAR(36)` | 主键 |
| `user_id` | `CHAR(36)` | 用户隔离字段 |
| `memory_type` | `VARCHAR(32)` | `AWFUL` / `UNCLEAR` / `MASTER` |
| `target_type` | `VARCHAR(32)` | `MODULE_TAG` / `ANSWER_SKILL` |
| `target_key` | `VARCHAR(120)` | 模块 tag 或回答能力 key |
| `summary` | `VARCHAR(500)` | 一句话画像要点，用于卡片展示 |
| `content` | `TEXT` | 客观画像内容 |
| `support_count` | `INT` | 支撑证据数量 |
| `status` | `VARCHAR(32)` | `ACTIVE` / `HIDDEN` |
| `first_seen_at` | `DATETIME` | 首次形成时间 |
| `last_seen_at` | `DATETIME` | 最近被证据增强时间 |
| `hidden_at` | `DATETIME` | 隐藏时间 |
| `latest_session_id` | `CHAR(36)` | 最近支撑练习 |
| `latest_qa_set_id` | `CHAR(36)` | 最近支撑题集 |
| `created_at` | `DATETIME` | 创建时间 |
| `updated_at` | `DATETIME` | 更新时间 |

唯一约束：`user_id + memory_type + target_type + target_key`。

### 3.14 `user_memory_evidence`

用途：支撑长期画像的真实练习证据。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `id` | `CHAR(36)` | 主键 |
| `memory_id` | `CHAR(36)` | 对应 `user_memory.id` |
| `user_id` | `CHAR(36)` | 用户隔离字段 |
| `session_id` | `CHAR(36)` | 练习会话 |
| `session_item_id` | `CHAR(36)` | 练习单题记录 |
| `qa_set_id` | `CHAR(36)` | 题集 |
| `qa_item_id` | `CHAR(36)` | 题目 |
| `module_tag` | `VARCHAR(120)` | 模块快照 |
| `question_snapshot` | `LONGTEXT` | 题干快照 |
| `result` | `VARCHAR(32)` | 单题结果 |
| `score` | `INT` | 单题分数 |
| `source_chunk_ids_json` | `JSON` | 来源切片快照 |
| `evidence_summary` | `TEXT` | 证据摘要 |
| `created_at` | `DATETIME` | 创建时间 |

唯一约束：`memory_id + session_item_id`。

## 4. PostgreSQL `chunk_search`

用途：RAG 检索副本表。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `chunk_id` | `VARCHAR(36)` | 主键，对应 `document_chunk.id` |
| `document_id` | `VARCHAR(36)` | 资料 ID |
| `user_id` | `VARCHAR(36)` | 用户隔离字段 |
| `chunk_index` | `INT` | 切片顺序 |
| `heading_path` | `VARCHAR(500)` | 章节路径 |
| `content` | `TEXT` | 切片正文 |
| `summary` | `TEXT` | 切片摘要 |
| `embedding` | `vector(1024)` | DashScope embedding |
| `content_tsv` | `TSVECTOR` | zhparser 全文索引字段 |
| `created_at` | `TIMESTAMP` | 创建时间 |
| `updated_at` | `TIMESTAMP` | 更新时间 |

索引：

1. `idx_cs_embedding`：HNSW 向量索引
2. `idx_cs_tsv`：GIN 全文索引
3. `idx_cs_user`
4. `idx_cs_document`
5. `idx_cs_heading_path`

## 5. 与代码相关的几个注意点

1. `qa_generation_task.stage`、`qa_generation_task_message.stage` 和 SSE `stage` 使用的是 `GeneratePhase.generateStage` 文案，不是英文枚举名。
2. `user_profile.allow_fallback` 已入库，并通过 Profile API 开放读写。
3. `practice_session.finished_at` 在 Assess 重复执行时不会刷新；只在首次完成时写入。
4. 删除 `source_document` 时不仅软删主记录，还会同步移除切片和 PostgreSQL 检索副本。
5. Memory 只允许 `ACTIVE` / `HIDDEN` 两个状态；隐藏不物理删除证据。
