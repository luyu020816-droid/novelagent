-- Genre row provenance (preference vs story-hook path)
ALTER TABLE genre_decision_contracts ADD COLUMN source VARCHAR(32) NOT NULL DEFAULT 'preference';
ALTER TABLE genre_decision_contracts ADD COLUMN story_hook_text TEXT;

-- Project: which genre contract init uses; which story snapshot is "active" in UI
ALTER TABLE projects ADD COLUMN selected_genre_contract_id VARCHAR(64);
ALTER TABLE projects ADD COLUMN selected_story_contract_id VARCHAR(64);

-- Story snapshot links to its novel seed (pair for listing / reload)
ALTER TABLE story_contracts ADD COLUMN novel_seed_contract_id VARCHAR(64);

-- Persist outline text from init bundle (not inside storyContract JSON)
ALTER TABLE story_contracts ADD COLUMN first_volume_outline TEXT;

-- Chapters belong to a specific story_contract snapshot (multiple inits per project)
ALTER TABLE chapter_contracts ADD COLUMN story_contract_id VARCHAR(64);

-- Backfill chapter rows with latest story_contract per project
UPDATE chapter_contracts cc
SET story_contract_id = sub.id
FROM (
    SELECT DISTINCT ON (project_id) id, project_id
    FROM story_contracts
    ORDER BY project_id, created_at DESC
) sub
WHERE cc.project_id = sub.project_id;

ALTER TABLE chapter_contracts ALTER COLUMN story_contract_id SET NOT NULL;

ALTER TABLE chapter_contracts DROP CONSTRAINT IF EXISTS chapter_contracts_project_id_chapter_no_key;

CREATE UNIQUE INDEX uq_chapter_contracts_story_chapter ON chapter_contracts (story_contract_id, chapter_no);

-- Backfill novel_seed_contract_id on existing stories (best-effort pairing by time)
-- Note: UPDATE ... FROM LATERAL cannot reference the target row alias inside LATERAL in PG; use correlated scalar subquery.
UPDATE story_contracts sc
SET novel_seed_contract_id = (
    SELECT ns.id
    FROM novel_seed_contracts ns
    WHERE ns.project_id = sc.project_id
      AND ns.created_at <= sc.created_at
    ORDER BY ns.created_at DESC
    LIMIT 1
)
WHERE sc.novel_seed_contract_id IS NULL;
