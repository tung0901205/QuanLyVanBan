-- Migration V002: Digital signature audit table.
-- Use unquoted identifiers because the base schema and JPA mappings are
-- resolved by PostgreSQL as lowercase identifiers.

CREATE TABLE IF NOT EXISTS chukyso (
    id        BIGSERIAL PRIMARY KEY,
    vanbanid  BIGINT    NOT NULL,
    nguoikyid BIGINT,
    ngayky    TIMESTAMP NOT NULL,
    loaiky    VARCHAR(50),
    ghichu    TEXT,
    hashfile  VARCHAR(64),
    certinfo  TEXT
);

CREATE INDEX IF NOT EXISTS idx_chukyso_vanbanid ON chukyso (vanbanid);
