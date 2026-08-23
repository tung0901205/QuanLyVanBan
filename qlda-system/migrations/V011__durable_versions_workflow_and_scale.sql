SET client_encoding = 'UTF8';

-- Persist the workflow projection used by document-service internal APIs.
ALTER TABLE IF EXISTS vanban ADD COLUMN IF NOT EXISTS nguoixulyid BIGINT;
ALTER TABLE IF EXISTS vanban ADD COLUMN IF NOT EXISTS trangthaiquytrinh VARCHAR(50);
ALTER TABLE IF EXISTS vanban ADD COLUMN IF NOT EXISTS buochientai VARCHAR(255);
ALTER TABLE IF EXISTS vanban ADD COLUMN IF NOT EXISTS xulyvanbanid BIGINT;
ALTER TABLE IF EXISTS vanban ADD COLUMN IF NOT EXISTS ocrconfidence DOUBLE PRECISION;
ALTER TABLE IF EXISTS vanban ADD COLUMN IF NOT EXISTS ocrfileurl VARCHAR(1000);

CREATE INDEX IF NOT EXISTS idx_vanban_assignee_due
    ON vanban(nguoixulyid, hanxuly)
    WHERE COALESCE(daxoa, FALSE) = FALSE;
CREATE INDEX IF NOT EXISTS idx_vanban_unit_status_created
    ON vanban(donvichutriid, trangthai, ngaytao DESC)
    WHERE COALESCE(daxoa, FALSE) = FALSE;

-- Version history must survive service restarts and deployments.
CREATE TABLE IF NOT EXISTS vanbanphienban (
    id BIGSERIAL PRIMARY KEY,
    vanbanid BIGINT NOT NULL,
    tenphienban VARCHAR(100) NOT NULL,
    fileurl VARCHAR(1000),
    noidungthaydoi TEXT,
    noidungsnapshot TEXT,
    nguoitaoid BIGINT,
    ngaytao TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_vanbanphienban_vanban FOREIGN KEY (vanbanid) REFERENCES vanban(id) ON DELETE CASCADE,
    CONSTRAINT uq_vanbanphienban_name UNIQUE (vanbanid, tenphienban)
);
CREATE INDEX IF NOT EXISTS idx_vanbanphienban_document_created
    ON vanbanphienban(vanbanid, ngaytao DESC);

-- A case file can contain an arbitrary number of documents.
ALTER TABLE IF EXISTS hosocongviec ADD COLUMN IF NOT EXISTS nhomhoso VARCHAR(100);
CREATE TABLE IF NOT EXISTS hosovanban (
    hosoid BIGINT NOT NULL,
    vanbanid BIGINT NOT NULL,
    ngaygan TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (hosoid, vanbanid),
    CONSTRAINT fk_hosovanban_hoso FOREIGN KEY (hosoid) REFERENCES hosocongviec(id) ON DELETE CASCADE,
    CONSTRAINT fk_hosovanban_vanban FOREIGN KEY (vanbanid) REFERENCES vanban(id) ON DELETE CASCADE
);
INSERT INTO hosovanban(hosoid, vanbanid)
SELECT id, vanbanid FROM hosocongviec WHERE vanbanid IS NOT NULL
ON CONFLICT (hosoid, vanbanid) DO NOTHING;
CREATE INDEX IF NOT EXISTS idx_hosovanban_document ON hosovanban(vanbanid);

-- Keep vector and result lookups fast as the document corpus grows.
CREATE INDEX IF NOT EXISTS idx_ai_chunk_document_attachment
    ON ai_document_chunk(van_ban_id, tep_dinh_kem_id, chunk_index);
CREATE INDEX IF NOT EXISTS idx_ketquaai_document_time
    ON ketquaai(vanbanid, thoigianxuly DESC);
