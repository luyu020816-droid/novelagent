import { Link } from "react-router-dom";
import type { SetupStatus } from "../api/setup";

function chapterHref(projectId: string, chapterNo: number) {
  return `/projects/${encodeURIComponent(projectId)}/chapters/${chapterNo}/workspace`;
}

export default function ProjectWorkflowBanner({
  projectId,
  status,
}: {
  projectId: string;
  status: SetupStatus | null;
}) {
  if (status == null) return null;

  const locked = status.setupLocked;
  const resume = status.resumeChapterNo > 0 ? status.resumeChapterNo : 1;

  if (locked) {
    return (
      <div className="mf-progress-banner">
        <div style={{ flex: "1 1 240px" }}>
          <p style={{ margin: 0, fontWeight: 700, fontSize: "1rem" }}>创作进行中</p>
          <p className="mf-muted mf-text-sm" style={{ margin: "6px 0 0" }}>
            已定稿 {status.acceptedChapterCount} 章
            {status.draftVersionCount > 0 ? ` · 共 ${status.draftVersionCount} 个版本` : ""}
            — 设定已锁定，仅可查看
          </p>
        </div>
        <Link to={chapterHref(projectId, resume)} className="mf-btn mf-btn-primary">
          继续写第 {resume} 章
        </Link>
        <Link
          to={`/projects/${encodeURIComponent(projectId)}/setup`}
          className="mf-btn mf-btn-secondary"
        >
          查看创作设定
        </Link>
      </div>
    );
  }

  const steps = [
    { ok: status.genreConfirmed, label: "题材", stage: "genre" },
    { ok: status.storyConfirmed, label: "故事契约", stage: "story" },
    { ok: status.narrativeConfirmed, label: "故事结构", stage: "narrative" },
    { ok: status.readyToWrite, label: "写章", stage: "ready" },
  ];

  return (
    <div className="mf-panel" style={{ marginBottom: 22, padding: "16px 18px" }}>
      <p style={{ margin: "0 0 12px", fontSize: 14 }}>
        <strong>创作进度：</strong>
        <span className="mf-muted">{status.nextActionHint}</span>
      </p>
      <div style={{ display: "flex", flexWrap: "wrap", gap: 8, alignItems: "center" }}>
        {steps.map((s) => (
          <Link
            key={s.label}
            to={`/projects/${encodeURIComponent(projectId)}/setup?stage=${s.stage}`}
            className={`mf-btn mf-btn-sm ${s.ok ? "mf-badge-success" : ""}`}
            style={s.ok ? { borderColor: "#86efac" } : undefined}
          >
            {s.ok ? "✓ " : ""}
            {s.label}
          </Link>
        ))}
        {!status.readyToWrite && (
          <Link to={`/projects/${encodeURIComponent(projectId)}/setup`} className="mf-btn mf-btn-primary mf-btn-sm">
            打开创作向导
          </Link>
        )}
        {status.readyToWrite && (
          <Link to={chapterHref(projectId, resume)} className="mf-btn mf-btn-primary mf-btn-sm">
            进入写作台
          </Link>
        )}
      </div>
    </div>
  );
}
