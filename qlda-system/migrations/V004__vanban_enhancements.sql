-- Add OCR text storage and AI classification columns.
-- The base schema creates unquoted identifiers, which PostgreSQL stores in lowercase.
ALTER TABLE vanban ADD COLUMN IF NOT EXISTS noidungocr TEXT;
ALTER TABLE vanban ADD COLUMN IF NOT EXISTS aiphanloai VARCHAR(100);
ALTER TABLE vanban ADD COLUMN IF NOT EXISTS aiconfidence DOUBLE PRECISION;
