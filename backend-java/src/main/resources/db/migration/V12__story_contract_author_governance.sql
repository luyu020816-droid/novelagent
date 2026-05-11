-- 作者治理：长期意图 + 不可违背要点（JSON 数组）
ALTER TABLE story_contracts
    ADD COLUMN IF NOT EXISTS author_intent TEXT;
ALTER TABLE story_contracts
    ADD COLUMN IF NOT EXISTS non_negotiables JSONB;
