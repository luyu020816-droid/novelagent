import type { MouseEvent } from "react";
import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { deleteProject, listProjects, type Project } from "../api/projects";

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
    return <p style={{ color: "crimson" }}>加载失败：{err}</p>;
  }

  return (
    <section>
      <h1 style={{ fontSize: 22, marginBottom: 8 }}>我的作品</h1>
      <p style={{ color: "#555", marginBottom: 20 }}>
        每个作品是一套独立的小说工程。点卡片进入题材与写作流程。
        {" "}
        <Link to="/books/dual" style={{ color: "#2563eb" }}>
          双书对照（并排看两部进度）
        </Link>
      </p>
      {err && <p style={{ color: "crimson", marginBottom: 12 }}>{err}</p>}
      {items.length === 0 ? (
        <p>
          还没有作品。<Link to="/projects/new">创建第一部</Link>
        </p>
      ) : (
        <ul style={{ listStyle: "none", padding: 0, margin: 0, display: "grid", gap: 12 }}>
          {items.map((p) => (
            <li key={p.id}>
              <div
                style={{
                  display: "flex",
                  alignItems: "stretch",
                  justifyContent: "space-between",
                  gap: 12,
                  background: "#fff",
                  border: "1px solid #e5e7eb",
                  borderRadius: 10,
                  padding: "14px 16px",
                  boxShadow: "0 1px 2px rgba(0,0,0,0.04)",
                }}
              >
                <Link
                  to={`/projects/${encodeURIComponent(p.id)}`}
                  style={{
                    flex: 1,
                    textDecoration: "none",
                    color: "inherit",
                  }}
                >
                  <strong style={{ fontSize: 16 }}>{p.name}</strong>
                  <div style={{ fontSize: 13, color: "#64748b", marginTop: 6 }}>
                    {p.language === "zh-CN" ? "中文" : p.language} · 目标 {p.targetChapters} 章
                  </div>
                </Link>
                <button
                  type="button"
                  disabled={busyId === p.id}
                  onClick={(ev) => void onDelete(ev, p)}
                  style={{
                    alignSelf: "center",
                    fontSize: 13,
                    padding: "6px 12px",
                    borderRadius: 8,
                    border: "1px solid #fecaca",
                    background: "#fff",
                    color: "#b91c1c",
                    cursor: busyId === p.id ? "wait" : "pointer",
                  }}
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
