-- Local login migration for existing PostgreSQL volumes.
-- BCrypt hash below corresponds to plaintext password: 123456

ALTER TABLE nguoidung
    ADD COLUMN IF NOT EXISTS password VARCHAR(100),
    ADD COLUMN IF NOT EXISTS microsoftrefreshtoken TEXT,
    ADD COLUMN IF NOT EXISTS microsofttokenexpiry TIMESTAMP;

UPDATE nguoidung
SET password = '$2y$10$hhhBmL7l5Iklx76Gse5l3OUWQWu2i78B4FWXK9K0zkwHToN6nBMVi'
WHERE password IS NULL OR password = '';

ALTER TABLE nguoidung
    ALTER COLUMN password SET NOT NULL;

CREATE EXTENSION IF NOT EXISTS vector;

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

CREATE INDEX IF NOT EXISTS idx_ai_document_chunk_type
    ON ai_document_chunk ((metadata ->> 'type'));

CREATE INDEX IF NOT EXISTS idx_ai_document_chunk_embedding
    ON ai_document_chunk
    USING ivfflat (embedding vector_cosine_ops)
    WITH (lists = 100);
