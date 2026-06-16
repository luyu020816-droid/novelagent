import { Link, useSearchParams } from "react-router-dom";
import { listProjects, type Project } from "../api/projects";
import SetupStudio from "../components/SetupStudio";
import { useCallback, useEffect, useState } from "react";

export default function DualBooksPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const leftParam = searchParams.get("left") ?? "";
  const rightParam = searchParams.get("right") ?? "";

  const [projectList, setProjectList] = useState<Project[]>([]);
  const [listErr, setListErr] = useState<string | null>(null);

  const refreshList = useCallback(() => {
    listProjects()
      .then(setProjectList)
      .catch((e: Error) => setListErr(e.message));
  }, []);

  useEffect(() => {
    refreshList();
  }, [refreshList]);

  function setSide(side: "left" | "right", projectId: string) {
    const next = new URLSearchParams(searchParams);
    if (projectId) {
      next.set(side, projectId);
    } else {
      next.delete(side);
    }
    setSearchParams(next, { replace: true });
  }

  const dupWarn = leftParam && rightParam && leftParam === rightParam;

  function column(side: "left" | "right", label: string, pid: string) {
    return (
      <div className="mf-col-shell">
        <div style={{ flexShrink: 0 }}>
          <label className="mf-label" style={{ marginBottom: 6 }}>
            {label}
          </label>
          <select
            className="mf-select"
            value={pid}
            onChange={(e) => setSide(side, e.target.value)}
            style={{ marginBottom: 10 }}
          >
            <option value="">— 选择作品 —</option>
            {projectList.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </select>
          {pid ? (
            <p className="mf-muted mf-text-sm" style={{ margin: "0 0 8px" }}>
              <Link to={`/projects/${encodeURIComponent(pid)}`}>打开该作品详情（丛书预设 / 题材列表）</Link>
            </p>
          ) : null}
        </div>
        <div style={{ overflowY: "auto", flex: 1, paddingRight: 4, marginTop: 4, minHeight: 0 }}>
          {pid ? (
            <SetupStudio projectId={pid} />
          ) : (
            <p className="mf-muted mf-text-sm">先选择一部作品，下方会出现创作向导（与项目内「创作向导」页相同）。</p>
          )}
        </div>
      </div>
    );
  }

  return (
    <section className="mf-page mf-prose" style={{ maxWidth: 1480, margin: "0 auto", padding: "0 4px" }}>
      <Link to="/" className="mf-back">
        ← 返回作品列表
      </Link>
      <h1 className="mf-page-title">双书对照 · 并行向导</h1>
      <p className="mf-page-lede">
        左右<strong>各自独立</strong>使用创作向导（题材 → 故事 → 结构）。URL 参数{" "}
        <code className="mf-code">?left=…&amp;right=…</code> 可收藏。
      </p>

      {listErr && (
        <p className="mf-alert mf-alert-error" role="alert">
          {listErr}
        </p>
      )}
      {dupWarn && <p className="mf-alert mf-alert-warn">两侧选了同一部作品；若要对照两本书，请选不同作品。</p>}
      {projectList.length < 2 && (
        <p className="mf-muted mf-text-sm" style={{ marginBottom: 16 }}>
          至少需要两部作品方便对照。
          <Link to="/projects/new">新建作品</Link>
        </p>
      )}

      <div
        className="dual-books-grid"
        style={{
          display: "grid",
          gridTemplateColumns: "minmax(0, 1fr) minmax(0, 1fr)",
          gap: 16,
          alignItems: "stretch",
        }}
      >
        {column("left", "左栏 · 作品 A", leftParam)}
        {column("right", "右栏 · 作品 B", rightParam)}
      </div>

      <style>{`
        @media (max-width: 960px) {
          .dual-books-grid {
            grid-template-columns: 1fr !important;
          }
        }
      `}</style>
    </section>
  );
}
