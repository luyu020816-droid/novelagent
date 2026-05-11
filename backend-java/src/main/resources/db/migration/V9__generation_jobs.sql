-- Day 14：异步章节生成任务（RabbitMQ Worker）与 Day 15 Token ROI 摘要
-- 幂等：表已存在（上次失败后重跑 / 手工建表）时不报错

CREATE TABLE IF NOT EXISTS generation_jobs (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    chapter_no INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_stage VARCHAR(128),
    progress_pct INT NOT NULL DEFAULT 0,
    payload_json JSONB,
    error_message TEXT,
    chapter_version_id VARCHAR(64),
    total_prompt_tokens BIGINT,
    total_completion_tokens BIGINT,
    total_tokens BIGINT,
    retry_waste_tokens BIGINT,
    trimmed_optional_count INT,
    critic_reject_rounds INT,
    llm_usage_summary_json JSONB,
    token_budget_status_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_generation_jobs_project_chapter ON generation_jobs (project_id, chapter_no);
CREATE INDEX IF NOT EXISTS idx_generation_jobs_project_created ON generation_jobs (project_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_generation_jobs_status ON generation_jobs (status);
