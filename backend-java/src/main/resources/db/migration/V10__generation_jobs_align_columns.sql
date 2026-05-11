-- 旧库中 generation_jobs 可能早于当前实体（缺 chapter_version_id、ROI 字段等）
-- 仅追加「扩展列」，不触碰 project_id / status 等应由 V9 一次性建全的核心列

ALTER TABLE generation_jobs ADD COLUMN IF NOT EXISTS current_stage VARCHAR(128);
ALTER TABLE generation_jobs ADD COLUMN IF NOT EXISTS progress_pct INT NOT NULL DEFAULT 0;
ALTER TABLE generation_jobs ADD COLUMN IF NOT EXISTS payload_json JSONB;
ALTER TABLE generation_jobs ADD COLUMN IF NOT EXISTS error_message TEXT;
ALTER TABLE generation_jobs ADD COLUMN IF NOT EXISTS chapter_version_id VARCHAR(64);
ALTER TABLE generation_jobs ADD COLUMN IF NOT EXISTS total_prompt_tokens BIGINT;
ALTER TABLE generation_jobs ADD COLUMN IF NOT EXISTS total_completion_tokens BIGINT;
ALTER TABLE generation_jobs ADD COLUMN IF NOT EXISTS total_tokens BIGINT;
ALTER TABLE generation_jobs ADD COLUMN IF NOT EXISTS retry_waste_tokens BIGINT;
ALTER TABLE generation_jobs ADD COLUMN IF NOT EXISTS trimmed_optional_count INT;
ALTER TABLE generation_jobs ADD COLUMN IF NOT EXISTS critic_reject_rounds INT;
ALTER TABLE generation_jobs ADD COLUMN IF NOT EXISTS llm_usage_summary_json JSONB;
ALTER TABLE generation_jobs ADD COLUMN IF NOT EXISTS token_budget_status_json JSONB;

CREATE INDEX IF NOT EXISTS idx_generation_jobs_project_chapter ON generation_jobs (project_id, chapter_no);
CREATE INDEX IF NOT EXISTS idx_generation_jobs_project_created ON generation_jobs (project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_generation_jobs_status ON generation_jobs (status);
