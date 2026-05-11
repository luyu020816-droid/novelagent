-- Day 5：novel_seed_contracts / story_contracts 表结构已在 V2 创建；此处补充按 project_id 查询用的索引。
CREATE INDEX IF NOT EXISTS idx_novel_seed_contracts_project_id ON novel_seed_contracts (project_id);
CREATE INDEX IF NOT EXISTS idx_story_contracts_project_id ON story_contracts (project_id);
