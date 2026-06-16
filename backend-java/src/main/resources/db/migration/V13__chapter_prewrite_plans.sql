-- 本章动笔前：作者确认的写作摘要/走向（与定稿后的 rolling summary 不同）
CREATE TABLE chapter_prewrite_plans (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    story_contract_id VARCHAR(64) NOT NULL,
    chapter_no INT NOT NULL,
    plan_summary TEXT NOT NULL DEFAULT '',
    confirmed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_chapter_prewrite UNIQUE (project_id, story_contract_id, chapter_no)
);

CREATE INDEX idx_chapter_prewrite_project ON chapter_prewrite_plans(project_id);
