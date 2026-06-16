-- 全书自动驾驶、叙事域 JSON、章后指标、子文本账本
ALTER TABLE projects ADD COLUMN autopilot_mode VARCHAR(32) NOT NULL DEFAULT 'MANUAL';
ALTER TABLE projects ADD COLUMN auto_accept_policy VARCHAR(32) NOT NULL DEFAULT 'NEVER';
ALTER TABLE projects ADD COLUMN max_auto_chapters_per_run INT NOT NULL DEFAULT 20;
ALTER TABLE projects ADD COLUMN autopilot_chapters_this_run INT NOT NULL DEFAULT 0;
ALTER TABLE projects ADD COLUMN autopilot_paused BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE projects ADD COLUMN autopilot_pause_reason TEXT;
ALTER TABLE projects ADD COLUMN pause_on_vector_sync_failed BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE projects ADD COLUMN narrative_domain_json JSONB;
ALTER TABLE projects ADD COLUMN autopilot_last_action_json JSONB;

COMMENT ON COLUMN projects.autopilot_mode IS 'MANUAL | AUTO_QUEUE_GENERATE | FULL_UNATTENDED';
COMMENT ON COLUMN projects.auto_accept_policy IS 'NEVER | CRITIC_PASS | CRITIC_AND_METRICS';
COMMENT ON COLUMN projects.narrative_domain_json IS 'PlotPilot 式域快照：storylines、confluences 等';

CREATE TABLE chapter_narrative_metrics (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    chapter_no INT NOT NULL,
    commit_id VARCHAR(64),
    tension_score DOUBLE PRECISION,
    style_similarity DOUBLE PRECISION,
    raw_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_narrative_metrics_project_chapter ON chapter_narrative_metrics (project_id, chapter_no DESC);

CREATE TABLE subtext_ledger (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    chapter_no INT NOT NULL,
    character_ref VARCHAR(255),
    question TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'pending',
    suggested_resolve_chapter INT,
    consumed_at_chapter INT,
    importance VARCHAR(16) NOT NULL DEFAULT 'medium',
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_subtext_project ON subtext_ledger (project_id, chapter_no);
