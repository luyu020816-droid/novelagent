ALTER TABLE chapter_versions
    ADD COLUMN IF NOT EXISTS llm_usage_summary_json JSONB;
