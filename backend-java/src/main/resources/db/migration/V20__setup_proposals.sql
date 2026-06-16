-- 创作向导：待确认提案（确认后才写入业务表）
CREATE TABLE setup_proposals (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    stage VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'pending',
    payload_json JSONB NOT NULL,
    assistant_reply TEXT,
    base_version INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_setup_proposals_project_stage ON setup_proposals (project_id, stage, status);

ALTER TABLE projects ADD COLUMN setup_mode VARCHAR(32);
COMMENT ON COLUMN projects.setup_mode IS 'standard | skill，创作向导入口模式';
