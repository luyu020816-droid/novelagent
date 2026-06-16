ALTER TABLE narrative_storylines
    ADD COLUMN storyline_role VARCHAR(16) NOT NULL DEFAULT 'SUB';

COMMENT ON COLUMN narrative_storylines.storyline_role IS 'MAIN | SUB | DARK，用于任务单 Prompt 分级';
