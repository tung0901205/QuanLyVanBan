-- Migration an toàn cho database đã tồn tại trước bản sửa AI/Dashboard.
CREATE EXTENSION IF NOT EXISTS vector;

CREATE TABLE IF NOT EXISTS ketquaai (
    id BIGSERIAL PRIMARY KEY,
    vanbanid BIGINT,
    nguoiyeucauid BIGINT,
    loaixulyai VARCHAR(100) NOT NULL,
    noidungdauvao TEXT,
    ketquatrave TEXT,
    dotincay NUMERIC(5,2),
    modelsudung VARCHAR(100),
    thoigianxuly TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    ghichu TEXT
);

ALTER TABLE IF EXISTS ketquaai
    ALTER COLUMN ghichu TYPE TEXT;

CREATE TABLE IF NOT EXISTS ai_document_chunk (
    id BIGSERIAL PRIMARY KEY,
    van_ban_id BIGINT NOT NULL,
    tep_dinh_kem_id BIGINT,
    chunk_index INT,
    noi_dung TEXT NOT NULL,
    embedding VECTOR(768),
    metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
    ngay_tao TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ketquaai_vanban
    ON ketquaai(vanbanid);

CREATE INDEX IF NOT EXISTS idx_ketquaai_nguoiyeucau
    ON ketquaai(nguoiyeucauid);

CREATE INDEX IF NOT EXISTS idx_ai_document_chunk_type
    ON ai_document_chunk ((metadata ->> 'type'));

CREATE INDEX IF NOT EXISTS idx_ai_document_chunk_embedding
    ON ai_document_chunk
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
