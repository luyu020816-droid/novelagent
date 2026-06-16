import type { MouseEvent } from "react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { deleteProject, listProjects, type Project } from "../api/projects";

function progressLabel(p: Project): string {
  const ch = p.currentChapter ?? 0;
  if (ch > 0) return `写作中 · 第 ${ch} 章`;
  return "新建";
}

export default function ProjectListPage() {
  const [items, setItems] = useState<Project[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const [busyId, setBusyId] = useState<string | null>(null);

  function refresh() {
    listProjects()
      .then(setItems)
      .catch((e: Error) => setErr(e.message));
  }

  useEffect(() => {
    refresh();
  }, []);

  async function onDelete(e: MouseEvent<HTMLButtonElement>, p: Project) {
    e.preventDefault();
    e.stopPropagation();
    if (
      !window.confirm(
        `确定删除「${p.name}」？\n所有章节、设定与导出文件都会清空，无法恢复。`
      )
    ) {
      return;
    }
    setBusyId(p.id);
    setErr(null);
    try {
      await deleteProject(p.id);
      refresh();
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setBusyId(null);
    }
  }

  if (err && items.length === 0) {
    return (
      <section className="mf-page">
        <p className="mf-alert mf-alert-error">加载失败：{err}</p>
      </section>
    );
  }

  return (
    <section className="mf-page mf-prose">
      <div style={{ display: "flex", flexWrap: "wrap", alignItems: "flex-end", justifyContent: "space-between", gap: 12, marginBottom: 8 }}>
        <div>
          <h1 className="mf-page-title">我的作品</h1>
          <p className="mf-page-lede" style={{ marginBottom: 0 }}>
            每个作品是一套独立的小说工程。进入作品后继续写作或查看设定。
          </p>
        </div>
        <Link to="/projects/new" className="mf-btn mf-btn-primary" style={{ textDecoration: "none" }}>
          + 新建作品
        </Link>
      </div>
      <p className="mf-text-sm mf-muted" style={{ margin: "0 0 20px" }}>
        <Link to="/books/dual">双书对照</Link> 可并排查看两部书的进度。
      </p>
      {err && (
        <p className="mf-alert mf-alert-error" role="alert">
          {err}
        </p>
      )}
      {items.length === 0 ? (
        <div className="mf-empty-state">
          <p style={{ margin: "0 0 16px" }}>还没有作品，创建第一部开始长篇创作。</p>
          <Link to="/projects/new" className="mf-btn mf-btn-primary" style={{ textDecoration: "none" }}>
            创建第一部
          </Link>
        </div>
      ) : (
        <ul className="mf-project-grid" style={{ listStyle: "none", padding: 0, margin: 0 }}>
          {items.map((p) => (
            <li key={p.id}>
              <div className="mf-project-card mf-project-row">
                <Link to={`/projects/${encodeURIComponent(p.id)}`} className="mf-project-row-link">
                  <div style={{ display: "flex", alignItems: "center", gap: 10, flexWrap: "wrap" }}>
                    <strong style={{ fontSize: "1.08rem" }}>{p.name}</strong>
                    <span className={`mf-badge ${(p.currentChapter ?? 0) > 0 ? "mf-badge-success" : "mf-badge-muted"}`}>
                      {progressLabel(p)}
                    </span>
                  </div>
                  <div className="mf-muted mf-text-sm" style={{ marginTop: 8 }}>
                    {p.language === "zh-CN" ? "中文" : p.language} · 目标 {p.targetChapters} 章
                  </div>
                </Link>
                <button
                  type="button"
                  disabled={busyId === p.id}
                  onClick={(ev) => void onDelete(ev, p)}
                  className="mf-btn mf-btn-danger mf-btn-sm"
                  style={{ alignSelf: "center", flexShrink: 0 }}
                >
                  {busyId === p.id ? "删除中…" : "删除"}
                </button>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
