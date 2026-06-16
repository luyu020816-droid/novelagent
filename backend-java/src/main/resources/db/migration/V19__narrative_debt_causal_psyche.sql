-- A: 叙事债务 + 因果边；B: 人物 psyche 快照（按章）
CREATE TABLE narrative_debts (
    id VARCHAR(32) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    debt_type VARCHAR(32) NOT NULL,
    description TEXT NOT NULL,
    planted_chapter INT NOT NULL,
    due_chapter INT,
    importance INT NOT NULL DEFAULT 2,
    status VARCHAR(16) NOT NULL DEFAULT 'open',
    resolved_chapter INT,
    involved_entities JSONB,
    context TEXT,
    source_ref VARCHAR(96),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_narrative_debts_project_status ON narrative_debts(project_id, status);
CREATE UNIQUE INDEX uq_narrative_debts_source ON narrative_debts(project_id, source_ref)
    WHERE source_ref IS NOT NULL;

CREATE TABLE narrative_causal_edges (
    id VARCHAR(32) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    cause_summary TEXT NOT NULL,
    effect_summary TEXT,
    planted_chapter INT NOT NULL,
    due_chapter INT,
    importance INT NOT NULL DEFAULT 2,
    status VARCHAR(16) NOT NULL DEFAULT 'open',
    resolved_chapter INT,
    involved_entities JSONB,
    source_chapter INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_narrative_causal_project_status ON narrative_causal_edges(project_id, status);

CREATE TABLE character_psyche_snapshots (
    id VARCHAR(32) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    chapter_no INT NOT NULL,
    character_ref VARCHAR(128) NOT NULL,
    masks_json JSONB,
    emotional_state TEXT,
    scars_text TEXT,
    motivations_text TEXT,
    snapshot_json JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_character_psyche_ch ON character_psyche_snapshots(project_id, chapter_no, character_ref);

COMMENT ON TABLE narrative_debts IS '叙事债务（伏笔/子文本/汇合/因果等）';
COMMENT ON TABLE narrative_causal_edges IS '未闭环因果链';
COMMENT ON TABLE character_psyche_snapshots IS '按章人物 psyche 快照（伤疤/执念/情绪）';
