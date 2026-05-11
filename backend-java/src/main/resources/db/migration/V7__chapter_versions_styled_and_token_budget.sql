ALTER TABLE chapter_versions
    ADD COLUMN IF NOT EXISTS styled_text TEXT,
    ADD COLUMN IF NOT EXISTS token_budget_status_json JSONB;
