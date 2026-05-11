import type { CSSProperties } from "react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  getProject,
  getProjectWorkspace,
  listProjects,
  type Project,
  type ProjectWorkspace,
} from "../api/projects";
import { fetchChapterUsageByChapter, type ChapterUsageAggregateRow } from "../api/chapters";
import { fetchLatestGenerationJob, type GenerationJobStatus } from "../api/generationJobs";

function sumTokens(rows: ChapterUsageAggregateRow[]): number {
  return rows.reduce((s, r) => s + r.totalTokens, 0);
}

function focusChapterNo(rows: ChapterUsageAggregateRow[]): number {
  if (rows.length === 0) return 1;
  return Math.max(...rows.map((r) => r.chapterNo));
}

type PanelBundle = {
  project: Project | null;
  workspace: ProjectWorkspace | null;
  usage: ChapterUsageAggregateRow[];
  job: GenerationJobStatus | null;
  err: string | null;
};

function emptyPanel(): PanelBundle {
  return { project: null, workspace: null, usage: [], job: null, err: null };
}

export default function DualBooksPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const leftParam = searchParams.get("left") ?? "";
  const rightParam = searchParams.get("right") ?? "";

  const [projectList, setProjectList] = useState<Project[]>([]);
  const [listErr, setListErr] = useState<string | null>(null);
  const [left, setLeft] = useState<PanelBundle>(emptyPanel);
  const [right, setRight] = useState<PanelBundle>(emptyPanel);

  const refreshList = useCallback(() => {
    listProjects()
      .then(setProjectList)
      .catch((e: Error) => setListErr(e.message));
  }, []);

  useEffect(() => {
    refreshList();
  }, [refreshList]);

  /** 列表加载后：若 URL 无参数且至少两部作品，默认填满左右。 */
  useEffect(() => {
    if (projectList.length < 2 || leftParam || rightParam) return;
    const a = projectList[0].id;
    const b = projectList[1].id;
    setSearchParams({ left: a, right: b }, { replace: true });
  }, [projectList, leftParam, rightParam, setSearchParams]);

  const loadPanel = useCallback(async (projectId: string): Promise<PanelBundle> => {
    if (!projectId.trim()) {
      return { ...emptyPanel(), err: null };
    }
    try {
      const [project, workspace, usage] = await Promise.all([
        getProject(projectId),
        getProjectWorkspace(projectId),
        fetchChapterUsageByChapter(projectId),
      ]);
      const ch = focusChapterNo(usage);
      let job: GenerationJobStatus | null = null;
      try {
        job = await fetchLatestGenerationJob(projectId, ch);
      } catch {
        job = null;
      }
      return { project, workspace, usage, job, err: null };
    } catch (e: unknown) {
      return {
        ...emptyPanel(),
        err: e instanceof Error ? e.message : String(e),
      };
    }
  }, []);

  useEffect(() => {
    let cancelled = false;
    async function run() {
      const [l, r] = await Promise.all([loadPanel(leftParam), loadPanel(rightParam)]);
      if (!cancelled) {
        setLeft(l);
        setRight(r);
      }
    }
    void run();
    const t = window.setInterval(() => void run(), 4000);
    return () => {
      cancelled = true;
      window.clearInterval(t);
    };
  }, [leftParam, rightParam, loadPanel]);

  function setSide(side: "left" | "right", projectId: string) {
    const next = new URLSearchParams(searchParams);
    next.set(side, projectId);
    setSearchParams(next, { replace: true });
  }

  const dupWarn = useMemo(
    () => leftParam && rightParam && leftParam === rightParam,
    [leftParam, rightParam]
  );

  function panelBody(data: PanelBundle, pid: string) {
    if (!pid) {
      return (
        <p style={{ color: "#64748b", margin: 0 }}>
          请从上方下拉框选择一部作品。
        </p>
      );
    }
    if (data.err) {
      return <p style={{ color: "crimson", margin: 0 }}>{data.err}</p>;
    }
    if (!data.project) {
      return <p style={{ color: "#64748b", margin: 0 }}>加载中…</p>;
    }
    const p = data.project;
    const w = data.workspace;
    const usage = data.usage;
    const tokens = sumTokens(usage);
    const chFocus = focusChapterNo(usage);
    const job = data.job;

    const genreOk = !!(w?.selectedGenreContractId && w.selectedGenreContractId.length > 0);
    const storyOk = !!(w?.selectedStoryContractId && w.selectedStoryContractId.length > 0);

    return (
      <div style={{ fontSize: 14, display: "flex", flexDirection: "column", gap: 12 }}>
        <div>
          <strong style={{ fontSize: 16 }}>{p.name}</strong>
          <span style={{ color: "#64748b", marginLeft: 8 }}>
            {p.status} · 目标 {p.targetChapters} 章
          </span>
        </div>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 8 }}>
          <Link
            to={`/projects/${encodeURIComponent(pid)}`}
            style={{ color: "#2563eb", textDecoration: "none" }}
          >
            作品主页
          </Link>
          <Link
            to={`/projects/${encodeURIComponent(pid)}/story/init`}
            style={{ color: "#2563eb", textDecoration: "none" }}
          >
            题材与大纲
          </Link>
          <Link
            to={`/projects/${encodeURIComponent(pid)}/chapters/${chFocus}/workspace`}
            style={{ color: "#2563eb", textDecoration: "none" }}
          >
            写作台（第 {chFocus} 章）
          </Link>
          <Link
            to={`/projects/${encodeURIComponent(pid)}/roi`}
            style={{ color: "#2563eb", textDecoration: "none" }}
          >
            用量
          </Link>
        </div>
        <ul style={{ margin: 0, paddingLeft: 20, color: "#334155" }}>
          <li>题材方案已选：{genreOk ? "是" : "否"}</li>
          <li>初始化快照已选：{storyOk ? "是" : "否"}</li>
          <li>历史快照条数：{w?.storyInits?.length ?? 0}</li>
          <li>
            已有写作记录的章节数：<strong>{usage.length}</strong>
          </li>
          <li>
            累计估算 token（全书写作过程）：<strong>{tokens.toLocaleString()}</strong>
          </li>
        </ul>
        {job && (job.status === "PENDING" || job.status === "RUNNING") && (
          <div
            style={{
              padding: 10,
              borderRadius: 8,
              background: "#eff6ff",
              border: "1px solid #bfdbfe",
            }}
          >
            <div style={{ fontWeight: 600, marginBottom: 6 }}>
              第 {job.chapterNo} 章 · 后台生成
            </div>
            <div style={{ fontSize: 13, marginBottom: 6 }}>{job.currentStage ?? "排队中"}</div>
            <div
              style={{
                height: 10,
                background: "#dbeafe",
                borderRadius: 5,
                overflow: "hidden",
              }}
            >
              <div
                style={{
                  width: `${Math.min(100, Math.max(0, job.progressPct))}%`,
                  height: "100%",
                  background: "#2563eb",
                  transition: "width 0.4s ease",
                }}
              />
            </div>
          </div>
        )}
        {job && job.status === "FAILED" && (
          <p style={{ color: "crimson", margin: 0, fontSize: 13 }}>
            最近任务失败：{job.errorMessage ?? "未知"}
          </p>
        )}
      </div>
    );
  }

  const selectStyle: CSSProperties = {
    width: "100%",
    maxWidth: 280,
    padding: "8px 10px",
    fontSize: 14,
    borderRadius: 8,
    border: "1px solid #cbd5e1",
    boxSizing: "border-box",
  };

  return (
    <section style={{ maxWidth: 1100, margin: "0 auto" }}>
      <p style={{ marginBottom: 12 }}>
        <Link to="/" style={{ color: "#2563eb", textDecoration: "none" }}>
          ← 返回作品列表
        </Link>
      </p>
      <h1 style={{ fontSize: 22, marginBottom: 8 }}>双书对照</h1>
      <p style={{ color: "#64748b", fontSize: 14, marginBottom: 20 }}>
        同一页并排刷新两部作品的选题进度、写作用量与最近一章的后台生成状态（约每 4 秒刷新）。
      </p>

      {listErr && <p style={{ color: "crimson", marginBottom: 12 }}>{listErr}</p>}
      {dupWarn && (
        <p style={{ color: "#92400e", marginBottom: 12 }}>
          左右选择了同一部作品；若要对照两部书，请选 different 作品。
        </p>
      )}
      {projectList.length < 2 && (
        <p style={{ color: "#64748b", marginBottom: 16 }}>
          至少需要两部作品才能对照。当前共 {projectList.length} 部。
          <Link to="/projects/new" style={{ marginLeft: 8 }}>
            新建作品
          </Link>
        </p>
      )}

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "minmax(0, 1fr) minmax(0, 1fr)",
          gap: 16,
          alignItems: "start",
        }}
        className="dual-books-grid"
      >
        <div
          style={{
            border: "1px solid #e5e7eb",
            borderRadius: 12,
            padding: 16,
            background: "#fff",
            boxShadow: "0 1px 2px rgba(0,0,0,0.04)",
            minWidth: 0,
          }}
        >
          <div style={{ marginBottom: 14 }}>
            <label style={{ display: "block", fontWeight: 600, marginBottom: 6 }}>左栏 · 作品 A</label>
            <select
              style={selectStyle}
              value={leftParam}
              onChange={(e) => setSide("left", e.target.value)}
            >
              <option value="">— 选择作品 —</option>
              {projectList.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </div>
          {panelBody(left, leftParam)}
        </div>

        <div
          style={{
            border: "1px solid #e5e7eb",
            borderRadius: 12,
            padding: 16,
            background: "#fff",
            boxShadow: "0 1px 2px rgba(0,0,0,0.04)",
            minWidth: 0,
          }}
        >
          <div style={{ marginBottom: 14 }}>
            <label style={{ display: "block", fontWeight: 600, marginBottom: 6 }}>右栏 · 作品 B</label>
            <select
              style={selectStyle}
              value={rightParam}
              onChange={(e) => setSide("right", e.target.value)}
            >
              <option value="">— 选择作品 —</option>
              {projectList.map((p) => (
                <option key={p.id} value={p.id}>
                  {p.name}
                </option>
              ))}
            </select>
          </div>
          {panelBody(right, rightParam)}
        </div>
      </div>

      <style>{`
        @media (max-width: 720px) {
          .dual-books-grid {
            grid-template-columns: 1fr !important;
          }
        }
      `}</style>

      <p style={{ fontSize: 12, color: "#94a3b8", marginTop: 20 }}>
        提示：地址栏带参数可书签收藏，例如{" "}
        <code style={{ fontSize: 11 }}>
          /books/dual?left=…&right=…
        </code>
      </p>
    </section>
  );
}
