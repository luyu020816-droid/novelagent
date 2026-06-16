-- 定稿后 Qdrant 向量同步可观测性；项目叙事 checkpoint / 阶段（全书状态机扩展位）
ALTER TABLE chapter_commits ADD COLUMN vector_sync_status VARCHAR(20);
ALTER TABLE chapter_commits ADD COLUMN vector_sync_error TEXT;
ALTER TABLE chapter_commits ADD COLUMN vector_sync_at TIMESTAMP;
ALTER TABLE chapter_commits ADD COLUMN vector_sync_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE projects ADD COLUMN narrative_phase VARCHAR(64);
ALTER TABLE projects ADD COLUMN narrative_checkpoint_json JSONB;

COMMENT ON COLUMN chapter_commits.vector_sync_status IS 'PENDING | OK | FAILED | SKIPPED（无 embedding 配置等）';
COMMENT ON COLUMN projects.narrative_phase IS '可选：全书叙事阶段（如 ACTIVE、PAUSED_ERROR）';
COMMENT ON COLUMN projects.narrative_checkpoint_json IS 'JSON：lastAcceptedChapterNo、lastCommitId、updatedAt 等';
