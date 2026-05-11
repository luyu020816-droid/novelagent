-- Day 8：accepted commit 上挂载结构化滚动摘要（JSON），供后续章节 historySummaries 注入。
ALTER TABLE chapter_commits
    ADD COLUMN summary JSONB;
