-- ============================================================
-- QA_Agent V2 RAG: PostgreSQL chunk_search DDL + 索引 + zhparser
-- 执行方式：psql -h localhost -U dasi -d qa_agent -f init_postgres.sql
-- ============================================================

-- 1. 扩展
CREATE EXTENSION IF NOT EXISTS vector;
CREATE EXTENSION IF NOT EXISTS zhparser;

-- 2. 中文分词全文检索配置
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_ts_config WHERE cfgname = 'zh'
    ) THEN
        CREATE TEXT SEARCH CONFIGURATION zh (PARSER = zhparser);
        ALTER TEXT SEARCH CONFIGURATION zh
            ADD MAPPING FOR n,v,a,i,e,l,t WITH simple;
    END IF;
END;
$$;

-- 3. chunk_search 表
DROP TABLE IF EXISTS chunk_search;

CREATE TABLE chunk_search (
    chunk_id         VARCHAR(36) PRIMARY KEY,
    document_id      VARCHAR(36) NOT NULL,
    user_id          VARCHAR(36) NOT NULL,
    chunk_index      INT NOT NULL,
    heading_path       VARCHAR(500),
    content          TEXT NOT NULL,
    summary          TEXT,
    embedding        vector(1024),
    content_tsv      TSVECTOR,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);

-- 4. 语义检索索引（HNSW 向量 ANN）
CREATE INDEX idx_cs_embedding ON chunk_search
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 200);

-- 5. 关键词检索索引（全文搜索 GIN）
CREATE INDEX idx_cs_tsv ON chunk_search USING GIN (content_tsv);

-- 6. 业务过滤索引
CREATE INDEX idx_cs_user ON chunk_search (user_id);
CREATE INDEX idx_cs_document ON chunk_search (document_id);
CREATE INDEX idx_cs_heading_path ON chunk_search (heading_path);
