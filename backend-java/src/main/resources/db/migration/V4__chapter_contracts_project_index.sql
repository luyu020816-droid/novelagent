-- Day 6：chapter_contracts 已在 V2 定义；补充按 project_id 查询索引。
CREATE INDEX IF NOT EXISTS idx_chapter_contracts_project_id ON chapter_contracts (project_id);
