-- Add Microsoft OAuth columns without quoted mixed-case identifiers.
ALTER TABLE nguoidung
    ADD COLUMN IF NOT EXISTS microsoftrefreshtoken TEXT,
    ADD COLUMN IF NOT EXISTS microsofttokenexpiry TIMESTAMP;
