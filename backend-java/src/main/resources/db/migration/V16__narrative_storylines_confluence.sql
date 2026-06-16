-- PlotPilot 式叙事结构真源：故事线 + 汇合点（PostgreSQL）
CREATE TABLE narrative_storylines (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    storyline_key VARCHAR(64) NOT NULL,
    title VARCHAR(512) NOT NULL,
    parent_storyline_id VARCHAR(64),
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    est_start_chapter INT,
    est_end_chapter INT,
    milestones_json JSONB,
    current_milestone_index INT NOT NULL DEFAULT 0,
    last_active_chapter_no INT,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_narr_story_key UNIQUE (project_id, storyline_key)
);
CREATE INDEX idx_narr_story_project ON narrative_storylines (project_id, sort_order);

CREATE TABLE narrative_confluences (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    primary_storyline_id VARCHAR(64) NOT NULL,
    secondary_storyline_id VARCHAR(64) NOT NULL,
    target_chapter INT NOT NULL,
    confluence_type VARCHAR(32) NOT NULL DEFAULT 'intersect',
    resolved BOOLEAN NOT NULL DEFAULT FALSE,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_narr_conf_project_ch ON narrative_confluences (project_id, target_chapter);

COMMENT ON TABLE narrative_storylines IS '故事线真源：ACTIVE/COMPLETED/PAUSED 等';
COMMENT ON TABLE narrative_confluences IS '汇合点：intersect|absorb|reveal；target_chapter 为计划章号';
