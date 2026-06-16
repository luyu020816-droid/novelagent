-- 项目级章节生成 DAG 版本（PlotPilot 风格可配置拓扑）
CREATE TABLE IF NOT EXISTS project_dag_versions (
    id VARCHAR(64) PRIMARY KEY,
    project_id VARCHAR(64) NOT NULL REFERENCES projects(id) ON DELETE CASCADE,
    version_no INT NOT NULL,
    label VARCHAR(128) NOT NULL DEFAULT 'default',
    dag_json JSONB NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    UNIQUE (project_id, version_no)
);

CREATE INDEX IF NOT EXISTS idx_project_dag_versions_project ON project_dag_versions (project_id, version_no DESC);

-- 每项目至多一条 active（部分 PG 版本不支持 WHERE 唯一索引时用应用层保证）
CREATE UNIQUE INDEX IF NOT EXISTS idx_project_dag_one_active
    ON project_dag_versions (project_id)
    WHERE is_active = TRUE;

COMMENT ON TABLE project_dag_versions IS '章节 LangGraph 编译前 DAG 定义；Writer 运行时编译';
