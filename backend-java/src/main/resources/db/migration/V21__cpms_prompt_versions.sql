-- CPMS 提示词版本（Writer 读 PG；manifest 为 fallback）
CREATE TABLE IF NOT EXISTS cpms_prompt_versions (
    id VARCHAR(64) PRIMARY KEY,
    node_key VARCHAR(64) NOT NULL,
    version_label VARCHAR(64) NOT NULL,
    content TEXT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (node_key, version_label)
);

CREATE INDEX IF NOT EXISTS idx_cpms_prompt_active ON cpms_prompt_versions (node_key, is_active);

ALTER TABLE llm_usage_log ADD COLUMN IF NOT EXISTS prompt_version VARCHAR(64);

COMMENT ON TABLE cpms_prompt_versions IS '节点提示词版本；每 node_key 仅一条 is_active=true（由 seed 脚本维护）';
COMMENT ON COLUMN llm_usage_log.prompt_version IS 'CPMS version_label，便于离线对比 prompt';
