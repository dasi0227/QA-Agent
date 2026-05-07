# QA_Agent 数据库设计

## 1. 文档定位

本文定义 `QA_Agent` 正式数据库设计，只服务当前已经确认的产品主闭环：

1. 用户注册 / 登录
2. 全局 `Profile`
3. 资料上传与结构化解析
4. 基于资料创建问答集
5. 问答集管理
6. 练习会话与结果
7. RAG 索引与检索
8. 消息任务追踪

本文不讨论：

1. 后端代码重构方案
2. `Memory` 体系
3. 更复杂的统计分析系统
4. Agent 运行日志体系
5. 多岗位平台化扩展

## 2. 设计边界

### 2.1 已确认边界

1. 有账户体系
2. 有唯一全局 `Profile`
3. 资料是可复用资产
4. 资料要保存原始正文和结构化结果
5. `RAG` 检索由 PostgreSQL `chunk_search` 表承载，MySQL `document_chunk` 为业务真数据
6. 创建问答集采用异步任务
7. 一个问答集可以引用多份资料，一份资料也可以被多个问答集复用
8. 题目从属于问答集，不做全局题库
9. 练习保留“会话 + 会话题目结果”
10. Kafka 消息发送记录到 `message_job` 表，支持重试与死信
11. 不引入 `Memory`

### 2.2 不做

1. `memory_*` 表
2. 题目证据关系表
3. 题目长期统计独立表
4. 任务与资料独立关联表
5. 每题每次完整 Agent 原始输出明细表
6. 独立 RAG 日志表
7. 问答集版本体系
8. 全局题库体系

## 3. 正式表清单

正式库表共 **12 张（MySQL）+ 1 张（PostgreSQL）**。

### 3.1 用户层

1. `user_account`
2. `user_profile`

### 3.2 资料层

3. `source_document`
4. `document_chunk`

### 3.3 生成任务层

5. `qa_generation_task`
6. `qa_generation_task_message`

### 3.4 问答资产层

7. `qa_set`
8. `qa_set_document_ref`
9. `qa_item`

### 3.5 练习层

10. `practice_session`
11. `practice_session_item`

### 3.6 消息任务层

12. `message_job`

### 3.7 PostgreSQL RAG 检索引擎

13. `chunk_search`（PostgreSQL，不在 MySQL 中）

## 4. 逐表设计

### 4.1 `user_account`

用途：账号身份本体，只负责登录身份，不负责业务偏好。

字段：

1. `id`
2. `username`
3. `email`
4. `password`
5. `status`
6. `avatar`
7. `created_at`
8. `updated_at`

说明：

1. `password` 字段名按当前设计要求保留，实际存密码哈希值。
2. `status` 用于表达账号状态。
3. `avatar` 存储 OSS object key（非完整 URL），上传新头像后替换。NULL 时使用系统默认。

### 4.2 `user_profile`

用途：用户唯一全局画像，作为生成和练习的默认上下文。

字段：

1. `user_id`
2. `target_role`
3. `target_domain`
4. `target_company`
5. `allow_general_knowledge`
6. `allow_web_search`
7. `answer_style`
8. `feedback_style`
9. `age`
10. `grade`
11. `major`
12. `stage`
13. `llm_base_url`
14. `llm_api_key`
15. `llm_model_name`
16. `created_at`
17. `updated_at`

说明：

1. 一个用户只允许一份全局 `Profile`。
2. 不保留 `note`。
3. `llm_base_url`、`llm_api_key`、`llm_model_name` 是用户自配 LLM 接入信息，V3 GenerateAgent 必须从这里读取；缺失时任务失败，错误类型为 `ErrorType.LLM_NOT_CONFIGURED`。

### 4.3 `source_document`

用途：用户资料主表，保存原始资料和资料级结构化结果。

字段：

1. `id`
2. `user_id`
3. `file_name`
4. `file_type`
5. `file_path`
6. `raw_content`
7. `normalized_content`
8. `summary`
9. `module_tags_json`
10. `reference_count`
11. `deleted`
12. `created_at`
13. `updated_at`

说明：

1. `file_type` 固定只支持 `MARKDOWN`。
2. `summary` 表示资料级摘要。
3. `reference_count` 是资料层唯一保留的轻量统计。
4. 资料删除采用软删除。

### 4.4 `document_chunk`

用途：资料切片表，存储切片的业务真数据（qa_item 引用此表的 chunk_id）。

字段：

1. `id`
2. `document_id`
3. `user_id`
4. `chunk_index`
5. `title_path`
6. `content`
7. `summary`
8. `module_tags_json`
9. `embedding_vector`
10. `created_at`
11. `updated_at`

说明：

1. 一条记录表示一段可检索、可引用、可参与生成的证据块。
2. `updated_at` 保留，用于资料编辑后的重切片和重建。
3. 检索不走此表，走 PostgreSQL `chunk_search`，此表是业务真数据源。

### 4.5 `qa_generation_task`

用途：创建问答集的异步任务主表。

字段：

1. `id`
2. `user_id`
3. `title`
4. `note`
5. `document_ids_json`
6. `qa_set_id`
7. `status`
8. `stage`
9. `error_code`
10. `error_message`
11. `allow_general_knowledge`
12. `allow_web_search`
13. `requested_question_count`
14. `created_at`
15. `started_at`
16. `completed_at`
17. `updated_at`

说明：

1. `document_ids_json` 先保留，不再单独拆任务资料关系表。
2. `qa_set_id` 用于指向成功产出的问答集，可为空。
3. 不保留 `progress`。
4. 不在主表中保留 `message`，完整阶段消息交由独立消息表承载。

### 4.6 `qa_generation_task_message`

用途：任务阶段消息表，记录完整阶段性消息流。

字段：

1. `id`
2. `task_id`
3. `stage`
4. `message`
5. `created_at`

说明：

1. 不强行定义 `message_type`。
2. 该表主要服务多阶段任务链路，例如 `PLAN -> SEARCH -> DRAFT -> VALIDATE -> FINALIZE`。

### 4.7 `qa_set`

用途：问答集主表，产品核心资产。

字段：

1. `id`
2. `user_id`
3. `task_id`
4. `title`
5. `description`
6. `module_tags_json`
7. `question_count`
8. `practice_count`
9. `average_score`
10. `best_score`
11. `average_accuracy`
12. `best_accuracy`
13. `last_practiced_at`
14. `created_at`
15. `updated_at`

说明：

1. 不保留 `status`。
2. `task_id` 表示来源任务，可为空。
3. `description` 替代旧的 `note`。

### 4.8 `qa_set_document_ref`

用途：问答集和资料的正式多对多关系表。

字段：

1. `id`
2. `qa_set_id`
3. `document_id`
4. `created_at`

说明：

1. 该表用于支撑资料复用。
2. `reference_count` 可以由该表反推，也可以在写入时同步更新 `source_document.reference_count`。

### 4.9 `qa_item`

用途：题目表，从属于问答集，不做全局题库。

字段：

1. `id`
2. `qa_set_id`
3. `user_id`
4. `question`
5. `knowledge_note`
6. `answer`
7. `module_tag`
8. `difficulty`
9. `conflict_tip`
10. `source_chunk_ids_json`
11. `sort_order`
12. `created_at`
13. `updated_at`

说明：

1. 不保留 `tags_json`。
2. 不把题目轻量统计挂在题目表。
3. `source_chunk_ids_json` 先保留在题目表中，不单独拆证据关系表。

### 4.10 `practice_session`

用途：一轮练习会话主表，记录本轮练习整体结果。

字段：

1. `id`
2. `user_id`
3. `qa_set_id`
4. `mode`
5. `feedback_mode`
6. `status`
7. `selected_module`
8. `total_questions`
9. `answered_count`
10. `score`
11. `accuracy`
12. `summary`
13. `started_at`
14. `finished_at`
15. `created_at`
16. `updated_at`

说明：

1. 保留 `status`。
2. 不保留 `module_results_json`。
3. `accuracy` 定义见第 7 节。

### 4.11 `practice_session_item`

用途：本轮练习中的题目结果表。

字段：

1. `id`
2. `session_id`
3. `qa_item_id`
4. `sort_order`
5. `user_answer`
6. `result`
7. `score`
8. `feedback_summary`
9. `answered_at`
10. `created_at`
11. `updated_at`

说明：

1. 保留用户作答文本 `user_answer`。
2. 不保留 `is_unknown`，由 `result` 承载"不会"语义。

### 4.12 `message_job`

用途：Kafka 消息发送记录表，支撑消息追踪、重试与死信队列。

字段：

1. `id`
2. `job_id` — 唯一任务标识，按规则构造（如 `rag_{documentId}`）
3. `job_status` — 任务状态：`UNSOLVED`（初始）、`SUCCESS`、`FAIL`
4. `job_retry` — 重试次数，初始 0，每次重试 +1，上限 3
5. `message_topic` — Kafka topic，用于确定接收方
6. `message_content` — 消息内容的 JSON
7. `message_first_sent_at` — 首次发送时间
8. `message_latest_sent_at` — 最近一次发送时间
9. `created_at`
10. `updated_at`

说明：

1. `job_id` 唯一约束，支持幂等去重。
2. xxl-job 定时扫描 `UNSOLVED` 记录，`job_retry < 3` 重新发送，`>= 3` 移入死信队列（DLQ topic + `.dlq` 后缀）。
3. 消费成功后由 Consumer 标记 `SUCCESS`，消费失败标记 `FAIL`。

### 4.13 `chunk_search`（PostgreSQL）

用途：RAG 检索引擎核心表，存储切片全文、向量及索引，作为检索唯一入口。

字段：

1. `chunk_id` — 与 MySQL `document_chunk.id` 一一对应
2. `document_id` — 关联 MySQL `source_document.id`
3. `user_id` — 按用户隔离
4. `chunk_index` — 文档内排序
5. `title_path` — 标题层级路径
6. `content` — 切片正文
7. `summary` — 切片摘要
8. `module_tags_json` — 模块标签 JSONB
9. `embedding` — DashScope text-embedding-v4 生成的 1024 维向量（HNSW 索引）
10. `content_tsv` — zhparser 中文分词 TSVECTOR（GIN 索引）
11. `created_at`
12. `updated_at`

说明：

1. 数据可从 MySQL `document_chunk` 全量重建，不构成独立数据源。
2. 检索不走 MySQL，所有查询（语义/关键词/混合）只打 PostgreSQL。
3. 语义检索使用 `vector <=>` 余弦距离，关键词检索使用 `ts_rank + to_tsquery('zh', ...)`。

## 5. 主键、外键、唯一约束

### 5.1 主键规则

1. 所有主表统一使用 `UUID id` 作为主键。
2. `user_profile` 直接使用 `user_id` 作为主键。

### 5.2 外键关系

1. `user_profile.user_id -> user_account.id`
2. `source_document.user_id -> user_account.id`
3. `document_chunk.document_id -> source_document.id`
4. `document_chunk.user_id -> user_account.id`
5. `qa_generation_task.user_id -> user_account.id`
6. `qa_generation_task.qa_set_id -> qa_set.id`，可空
7. `qa_generation_task_message.task_id -> qa_generation_task.id`
8. `qa_set.user_id -> user_account.id`
9. `qa_set.task_id -> qa_generation_task.id`，可空
10. `qa_set_document_ref.qa_set_id -> qa_set.id`
11. `qa_set_document_ref.document_id -> source_document.id`
12. `qa_item.qa_set_id -> qa_set.id`
13. `qa_item.user_id -> user_account.id`
14. `practice_session.user_id -> user_account.id`
15. `practice_session.qa_set_id -> qa_set.id`
16. `practice_session_item.session_id -> practice_session.id`
17. `practice_session_item.qa_item_id -> qa_item.id`

### 5.3 唯一约束

1. `user_account.username` 唯一
2. `user_account.email` 唯一
3. `document_chunk(document_id, chunk_index)` 唯一
4. `qa_set_document_ref(qa_set_id, document_id)` 唯一
5. `practice_session_item(session_id, qa_item_id)` 唯一
6. `message_job.job_id` 唯一

## 6. 删除规则

### 6.1 `user_account`

1. 不设计真删除
2. 通过 `status` 控制账号可用性

### 6.2 `source_document`

1. 采用软删除
2. 使用 `deleted` 字段标记
3. 删除时级联清理 MySQL `document_chunk` + PostgreSQL `chunk_search`

### 6.3 `document_chunk`

1. 不允许单独业务删除
2. 必须跟随资料删除、资料重切片或资料重建同步处理

### 6.4 `qa_set`

1. 采用真删除
2. 删除问答集时级联删除：
   - `qa_set_document_ref`
   - `qa_item`
   - `practice_session`
   - `practice_session_item`

### 6.5 `practice_session`

1. 不作为高频删除对象单独设计
2. 主要跟随问答集删除一起清理

### 6.6 `message_job`

1. 不提供业务删除接口
2. 状态变为 FAIL 后可由运维手动清理

## 7. 枚举、字符串、JSON 规则

### 7.1 使用枚举的字段

1. `user_account.status`
2. `source_document.file_type`
3. `qa_generation_task.status`
4. `qa_generation_task.stage`
5. `practice_session.mode`
6. `practice_session.feedback_mode`
7. `practice_session.status`
8. `practice_session_item.result`
9. `message_job.job_status`

### 7.2 使用普通字符串的字段

1. 用户输入类字段
   - `target_role`
   - `target_domain`
   - `target_company`
   - `answer_style`
   - `feedback_style`
   - `age`
   - `grade`
   - `major`
   - `stage`
2. 正文、摘要、描述、回答、短反馈字段

### 7.3 使用 JSON 的字段

1. `source_document.module_tags_json`
2. `document_chunk.module_tags_json`
3. `qa_generation_task.document_ids_json`
4. `qa_set.module_tags_json`
5. `qa_item.source_chunk_ids_json`

### 7.4 不建议的做法

1. 不用逗号拼接字符串代替数组结构
2. 不把结构化状态塞进普通字符串
3. 不把任务完整消息流塞成一个大 JSON 字段

## 8. 准确率定义

### 8.1 单轮练习准确率

`practice_session.accuracy` 定义为：

`(CORRECT + NEEDS_IMPROVEMENT) / 已作答题数`

即：

1. `CORRECT` 算命中
2. `NEEDS_IMPROVEMENT` 算命中
3. `WRONG` 不算命中
4. `UNKNOWN` 不算命中

### 8.2 问答集聚合准确率

1. `qa_set.average_accuracy` 表示该题集多轮练习的平均准确率
2. `qa_set.best_accuracy` 表示该题集历史最佳准确率

## 9. 设计结论

1. 真正长期业务关系，用表表达
2. 保留一个必要的多对多关系表：`qa_set_document_ref`
3. 任务消息流单独建表，不塞回主任务表
4. `RAG` 以 PostgreSQL `chunk_search` 为检索引擎，MySQL `document_chunk` 为业务真数据
5. Kafka 消息发送追踪以 `message_job` 表承载
6. 不提前引入 `Memory`
7. 不提前做复杂统计和复杂审计体系
