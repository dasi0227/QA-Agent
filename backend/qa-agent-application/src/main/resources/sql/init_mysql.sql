SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS `user_memory_evidence`;
DROP TABLE IF EXISTS `user_memory`;
DROP TABLE IF EXISTS `practice_session_item`;
DROP TABLE IF EXISTS `practice_session`;
DROP TABLE IF EXISTS `qa_item`;
DROP TABLE IF EXISTS `qa_set_document_ref`;
DROP TABLE IF EXISTS `qa_generation_task_message`;
DROP TABLE IF EXISTS `qa_generation_task`;
DROP TABLE IF EXISTS `document_chunk`;
DROP TABLE IF EXISTS `source_document`;
DROP TABLE IF EXISTS `message_job`;
DROP TABLE IF EXISTS `user_profile`;
DROP TABLE IF EXISTS `qa_set`;
DROP TABLE IF EXISTS `user_account`;

CREATE TABLE `user_account` (
    `id` CHAR(36) NOT NULL,
    `username` VARCHAR(100) NOT NULL,
    `email` VARCHAR(200) NOT NULL,
    `password` VARCHAR(255) NOT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `avatar` VARCHAR(512) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_account_username` (`username`),
    UNIQUE KEY `uk_user_account_email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_profile` (
    `user_id` CHAR(36) NOT NULL,
    `target_role` VARCHAR(120) NULL,
    `target_domain` VARCHAR(120) NULL,
    `target_company` VARCHAR(120) NULL,
    `allow_refer_memory` TINYINT(1) NULL COMMENT '生成规划是否允许参考长期记忆',
    `allow_web_search` TINYINT(1) NULL,
    `allow_fallback` TINYINT(1) NOT NULL DEFAULT 0,
    `answer_style` VARCHAR(255) NULL,
    `feedback_style` VARCHAR(255) NULL,
    `grade` VARCHAR(64) NULL,
    `major` VARCHAR(128) NULL,
    `stage` VARCHAR(128) NULL,
    `llm_base_url` VARCHAR(500) NULL COMMENT '用户自配 LLM API 端点',
    `llm_api_key` VARCHAR(255) NULL COMMENT '用户自配 LLM API Key',
    `llm_model_name` VARCHAR(100) NULL COMMENT '用户自配 LLM 模型名',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`user_id`),
    CONSTRAINT `fk_user_profile_user` FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `source_document` (
    `id` CHAR(36) NOT NULL,
    `user_id` CHAR(36) NOT NULL,
    `file_name` VARCHAR(255) NOT NULL,
    `file_type` VARCHAR(32) NOT NULL,
    `file_path` VARCHAR(500) NULL,
    `raw_content` LONGTEXT NULL,
    `index_status` VARCHAR(32) NOT NULL DEFAULT 'UNSOLVED' COMMENT 'INDEXING/FINISHED/UNSOLVED',
    `reference_count` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_source_document_user` (`user_id`),
    CONSTRAINT `fk_source_document_user` FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `document_chunk` (
    `id` CHAR(36) NOT NULL,
    `document_id` CHAR(36) NOT NULL,
    `user_id` CHAR(36) NOT NULL,
    `chunk_index` INT NOT NULL,
    `heading_path` VARCHAR(500) NULL,
    `content` LONGTEXT NOT NULL,
    `summary` LONGTEXT NULL,
    
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_document_chunk_document_index` (`document_id`, `chunk_index`),
    KEY `idx_document_chunk_document` (`document_id`),
    KEY `idx_document_chunk_user` (`user_id`),
    CONSTRAINT `fk_document_chunk_document` FOREIGN KEY (`document_id`) REFERENCES `source_document` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_document_chunk_user` FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `qa_set` (
    `id` CHAR(36) NOT NULL,
    `user_id` CHAR(36) NOT NULL,
    `task_id` CHAR(36) NULL,
    `title` VARCHAR(255) NOT NULL,
    `description` LONGTEXT NULL,
    `module_tags_json` JSON NULL,
    `question_count` INT NOT NULL DEFAULT 0,
    `practice_count` INT NOT NULL DEFAULT 0,
    `average_score` INT NULL,
    `best_score` INT NULL,
    `average_accuracy` DECIMAL(10,2) NULL,
    `best_accuracy` DECIMAL(10,2) NULL,
    `last_practiced_at` DATETIME NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_qa_set_user` (`user_id`),
    KEY `idx_qa_set_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `qa_generation_task` (
    `id` CHAR(36) NOT NULL,
    `user_id` CHAR(36) NOT NULL,
    `title` VARCHAR(255) NOT NULL,
    `user_prompt` LONGTEXT NULL,
    `document_ids_json` JSON NULL,
    `qa_set_id` CHAR(36) NULL,
    `status` VARCHAR(32) NOT NULL,
    `stage` VARCHAR(32) NOT NULL,
    `error_code` VARCHAR(64) NULL,
    `error_message` LONGTEXT NULL,
    `allow_refer_memory` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '任务启动时是否允许参考长期记忆的快照',
    `allow_web_search` TINYINT(1) NOT NULL DEFAULT 0,
    `requested_question_count` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `started_at` DATETIME NULL,
    `completed_at` DATETIME NULL,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_qa_generation_task_user` (`user_id`),
    KEY `idx_qa_generation_task_set` (`qa_set_id`),
    CONSTRAINT `fk_qa_generation_task_user` FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `qa_generation_task_message` (
    `id` CHAR(36) NOT NULL,
    `task_id` CHAR(36) NOT NULL,
    `user_id` CHAR(36) NOT NULL,
    `stage` VARCHAR(32) NOT NULL,
    `message` LONGTEXT NOT NULL,
    `content` LONGTEXT NULL COMMENT '完整 SseEvent JSON',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_qa_generation_task_message_task` (`task_id`),
    CONSTRAINT `fk_qa_generation_task_message_task` FOREIGN KEY (`task_id`) REFERENCES `qa_generation_task` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `qa_set_document_ref` (
    `id` CHAR(36) NOT NULL,
    `qa_set_id` CHAR(36) NOT NULL,
    `document_id` CHAR(36) NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_qa_set_document_ref_pair` (`qa_set_id`, `document_id`),
    KEY `idx_qa_set_document_ref_set` (`qa_set_id`),
    KEY `idx_qa_set_document_ref_document` (`document_id`),
    CONSTRAINT `fk_qa_set_document_ref_set` FOREIGN KEY (`qa_set_id`) REFERENCES `qa_set` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_qa_set_document_ref_document` FOREIGN KEY (`document_id`) REFERENCES `source_document` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `qa_item` (
    `id` CHAR(36) NOT NULL,
    `qa_set_id` CHAR(36) NOT NULL,
    `user_id` CHAR(36) NOT NULL,
    `question` LONGTEXT NOT NULL,
    `knowledge_note` LONGTEXT NULL,
    `answer` LONGTEXT NULL,
    `module_tag` VARCHAR(120) NOT NULL,
    `difficulty` VARCHAR(32) NULL,
    `keywords` LONGTEXT NULL,
    `hint` LONGTEXT NULL,
    `source_reliable` TINYINT(1) NOT NULL DEFAULT 1,
    `is_imported` TINYINT(1) NOT NULL DEFAULT 0,
    `source_chunk_ids_json` JSON NULL,
    `complete_status` VARCHAR(32) NOT NULL DEFAULT 'SOLVED',
    `sort_order` INT NOT NULL DEFAULT 0,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_qa_item_set` (`qa_set_id`),
    KEY `idx_qa_item_user` (`user_id`),
    CONSTRAINT `fk_qa_item_set` FOREIGN KEY (`qa_set_id`) REFERENCES `qa_set` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_qa_item_user` FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `practice_session` (
    `id` CHAR(36) NOT NULL,
    `user_id` CHAR(36) NOT NULL,
    `qa_set_id` CHAR(36) NOT NULL,
    `mode` VARCHAR(32) NOT NULL,
    `feedback_mode` VARCHAR(32) NOT NULL,
    `status` VARCHAR(32) NOT NULL,
    `selected_module` VARCHAR(120) NULL,
    `total_questions` INT NOT NULL DEFAULT 0,
    `answered_count` INT NOT NULL DEFAULT 0,
    `current_index` INT NOT NULL DEFAULT 0,
    `last_active_at` DATETIME NULL,
    `duration_seconds` INT NOT NULL DEFAULT 0,
    `score` INT NULL,
    `accuracy` DECIMAL(10,2) NULL,
    `perfect_count` INT NOT NULL DEFAULT 0,
    `correct_count` INT NOT NULL DEFAULT 0,
    `deficient_count` INT NOT NULL DEFAULT 0,
    `wrong_count` INT NOT NULL DEFAULT 0,
    `unknown_count` INT NOT NULL DEFAULT 0,
    `summary` LONGTEXT NULL,
    `assessment_detail_json` JSON NULL,
    `started_at` DATETIME NULL,
    `finished_at` DATETIME NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `idx_practice_session_user` (`user_id`),
    KEY `idx_practice_session_set` (`qa_set_id`),
    CONSTRAINT `fk_practice_session_user` FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_practice_session_set` FOREIGN KEY (`qa_set_id`) REFERENCES `qa_set` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `practice_session_item` (
    `id` CHAR(36) NOT NULL,
    `user_id` CHAR(36) NOT NULL,
    `session_id` CHAR(36) NOT NULL,
    `qa_item_id` CHAR(36) NOT NULL,
    `sort_order` INT NOT NULL DEFAULT 0,
    `user_answer` LONGTEXT NULL,
    `status` VARCHAR(32) NOT NULL DEFAULT 'UNANSWERED',
    `unknown` TINYINT(1) NOT NULL DEFAULT 0,
    `result` VARCHAR(32) NULL,
    `score` INT NULL,
    `feedback_summary` LONGTEXT NULL,
    `feedback_judge_detail` TEXT NULL,
    `feedback_hint_detail` TEXT NULL,
    `answered_at` DATETIME NULL,
    `submitted_at` DATETIME NULL,
    `question_snapshot` LONGTEXT NULL,
    `standard_answer_snapshot` LONGTEXT NULL,
    `knowledge_note_snapshot` LONGTEXT NULL,
    `keywords_snapshot` LONGTEXT NULL,
    `hint_snapshot` LONGTEXT NULL,
    `module_tag_snapshot` VARCHAR(120) NULL,
    `difficulty_snapshot` VARCHAR(32) NULL,
    `source_chunk_ids_snapshot_json` JSON NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_practice_session_item_pair` (`session_id`, `qa_item_id`),
    KEY `idx_practice_session_item_user` (`user_id`),
    KEY `idx_practice_session_item_session` (`session_id`),
    KEY `idx_practice_session_item_qa_item` (`qa_item_id`),
    CONSTRAINT `fk_practice_session_item_user` FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_practice_session_item_session` FOREIGN KEY (`session_id`) REFERENCES `practice_session` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_practice_session_item_qa_item` FOREIGN KEY (`qa_item_id`) REFERENCES `qa_item` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `message_job` (
    `id`                     VARCHAR(36) NOT NULL,
    `job_id`                 VARCHAR(64) NOT NULL,
    `job_status`             VARCHAR(20) NOT NULL DEFAULT 'UNSOLVED',
    `job_retry`              INT NOT NULL DEFAULT 0,
    `message_topic`          VARCHAR(100) NOT NULL,
    `message_content`        TEXT NOT NULL,
    `error_message`          LONGTEXT NULL,
    `message_first_sent_at`  TIMESTAMP NULL,
    `message_latest_sent_at` TIMESTAMP NULL,
    `created_at`             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`             TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_message_job_job_id` (`job_id`),
    KEY `idx_message_job_status` (`job_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_memory` (
    `id` CHAR(36) NOT NULL,
    `user_id` CHAR(36) NOT NULL,
    `memory_type` VARCHAR(32) NOT NULL,
    `target_type` VARCHAR(32) NOT NULL COMMENT 'MODULE_TAG / ANSWER_SKILL',
    `target_key` VARCHAR(120) NOT NULL COMMENT '模块 tag 或回答能力 key',
    `summary` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '一句话画像要点',
    `content` TEXT NOT NULL,
    `support_count` INT NOT NULL DEFAULT 0,
    `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    `first_seen_at` DATETIME NULL,
    `last_seen_at` DATETIME NULL,
    `hidden_at` DATETIME NULL,
    `latest_session_id` CHAR(36) NULL,
    `latest_qa_set_id` CHAR(36) NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_memory_target` (`user_id`, `memory_type`, `target_type`, `target_key`),
    KEY `idx_user_memory_user_status_updated` (`user_id`, `status`, `updated_at`),
    KEY `idx_user_memory_user_type_target` (`user_id`, `memory_type`, `target_type`, `target_key`),
    CONSTRAINT `fk_user_memory_user` FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_user_memory_latest_session` FOREIGN KEY (`latest_session_id`) REFERENCES `practice_session` (`id`) ON DELETE SET NULL,
    CONSTRAINT `fk_user_memory_latest_set` FOREIGN KEY (`latest_qa_set_id`) REFERENCES `qa_set` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `user_memory_evidence` (
    `id` CHAR(36) NOT NULL,
    `memory_id` CHAR(36) NOT NULL,
    `user_id` CHAR(36) NOT NULL,
    `session_id` CHAR(36) NOT NULL,
    `session_item_id` CHAR(36) NOT NULL,
    `qa_set_id` CHAR(36) NOT NULL,
    `qa_item_id` CHAR(36) NOT NULL,
    `module_tag` VARCHAR(120) NULL,
    `question_snapshot` LONGTEXT NULL,
    `result` VARCHAR(32) NULL,
    `score` INT NULL,
    `source_chunk_ids_json` JSON NULL,
    `evidence_summary` TEXT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_memory_evidence_item` (`memory_id`, `session_item_id`),
    KEY `idx_user_memory_evidence_memory_created` (`memory_id`, `created_at`),
    KEY `idx_user_memory_evidence_user_session` (`user_id`, `session_id`),
    CONSTRAINT `fk_user_memory_evidence_memory` FOREIGN KEY (`memory_id`) REFERENCES `user_memory` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_user_memory_evidence_user` FOREIGN KEY (`user_id`) REFERENCES `user_account` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_user_memory_evidence_session` FOREIGN KEY (`session_id`) REFERENCES `practice_session` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_user_memory_evidence_session_item` FOREIGN KEY (`session_item_id`) REFERENCES `practice_session_item` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_user_memory_evidence_set` FOREIGN KEY (`qa_set_id`) REFERENCES `qa_set` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_user_memory_evidence_item` FOREIGN KEY (`qa_item_id`) REFERENCES `qa_item` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `qa_set`
    ADD CONSTRAINT `fk_qa_set_task` FOREIGN KEY (`task_id`) REFERENCES `qa_generation_task` (`id`) ON DELETE SET NULL;

ALTER TABLE `qa_generation_task`
    ADD CONSTRAINT `fk_qa_generation_task_set` FOREIGN KEY (`qa_set_id`) REFERENCES `qa_set` (`id`) ON DELETE SET NULL;

SET FOREIGN_KEY_CHECKS = 1;
