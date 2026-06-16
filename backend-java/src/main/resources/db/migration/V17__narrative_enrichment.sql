-- 叙事真源补全：汇合字段、故事线进度、定稿策略、履约报告
ALTER TABLE narrative_confluences
    ADD COLUMN context_summary TEXT,
    ADD COLUMN pre_reveal_hint TEXT,
    ADD COLUMN behavior_guards JSONB;

ALTER TABLE narrative_storylines
    ADD COLUMN progress_summary TEXT;

ALTER TABLE projects
    ADD COLUMN narrative_accept_policy_json JSONB;

ALTER TABLE chapter_versions
    ADD COLUMN fulfillment_report_json JSONB;

COMMENT ON COLUMN narrative_confluences.context_summary IS '汇合语义摘要（PlotPilot context_summary）';
COMMENT ON COLUMN narrative_confluences.behavior_guards IS 'reveal 类型行为约束 JSON 字符串数组';
COMMENT ON COLUMN projects.narrative_accept_policy_json IS '定稿后 PG 回写策略';
COMMENT ON COLUMN chapter_versions.fulfillment_report_json IS '本章任务单履约校验（生成后/定稿后）';
