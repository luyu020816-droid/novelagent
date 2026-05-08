CREATE TABLE genre_decision_contracts (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    selected_direction JSONB,
    candidate_rankings JSONB,
    risk_notes JSONB,
    raw_json JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE novel_seed_contracts (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    raw_json JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE story_contracts (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    version INT NOT NULL DEFAULT 1,
    raw_json JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE chapter_contracts (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    chapter_no INT NOT NULL,
    title_hint VARCHAR(255),
    raw_json JSONB NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (project_id, chapter_no)
);

CREATE TABLE generation_jobs (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    chapter_no INT,
    job_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    current_stage VARCHAR(64),
    progress INT DEFAULT 0,
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    updated_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE chapter_versions (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    chapter_no INT NOT NULL,
    version INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    scene_plan_json JSONB,
    chapter_text TEXT,
    critic_report_json JSONB,
    rewrite_instruction_json JSONB,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (project_id, chapter_no, version)
);

CREATE TABLE chapter_commits (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    chapter_no INT NOT NULL,
    version INT NOT NULL,
    status VARCHAR(32) NOT NULL,
    chapter_contract_id VARCHAR(64),
    context_pack_hash VARCHAR(128),
    final_text_path TEXT,
    review_report_id VARCHAR(64),
    token_usage_summary JSONB,
    rejection_reason TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);

CREATE TABLE memory_summaries (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL,
    chapter_no INT NOT NULL,
    commit_id VARCHAR(64) NOT NULL,
    title VARCHAR(255),
    summary_text TEXT NOT NULL,
    key_events JSONB,
    character_state_changes JSONB,
    new_foreshadowing JSONB,
    cliffhanger TEXT,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE (project_id, chapter_no)
);

CREATE TABLE llm_usage_log (
    id BIGSERIAL PRIMARY KEY,
    job_id VARCHAR(64),
    project_id VARCHAR(64),
    chapter_no INT,
    agent_name VARCHAR(64),
    node_name VARCHAR(64),
    provider VARCHAR(32),
    model VARCHAR(64),
    estimated_input_tokens INT,
    estimated_output_tokens INT,
    estimated_total_tokens INT,
    actual_input_tokens INT,
    actual_output_tokens INT,
    actual_total_tokens INT,
    latency_ms INT,
    status VARCHAR(32),
    error_message TEXT,
    created_at TIMESTAMP DEFAULT NOW()
);
