import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import CopilotChatPanel from "../components/CopilotChatPanel";
import { Link, useParams } from "react-router-dom";
import {
  acceptChapterVersion,
  deleteChapterVersion,
  fetchChapterUsageByChapter,
  fetchLatestChapterVersion,
  rejectChapterVersion,
  type ChapterUsageAggregateRow,
  type ChapterVersionSnapshot,
} from "../api/chapters";
import {
  fetchLatestGenerationJob,
  postChapterGenerateAsync,
  type GenerationJobStatus,
} from "../api/generationJobs";

function friendlyVersionStatus(status: string): string {
  switch (status) {
    case "PENDING_REVIEW":
      return "待您审核";
    case "ACCEPTED":
      return "已定稿";
    case "REJECTED":
      return "已退回";
    default:
      return status;
  }
}

export default function ChapterWorkspacePage() {
  const { projectId, chapterNo: chapterNoParam } = useParams<{ projectId: string; chapterNo: string }>();
  const chapterNo = Number(chapterNoParam || "1") || 1;

  const [snapshot, setSnapshot] = useState<ChapterVersionSnapshot | null>(null);
  const [pollErr, setPollErr] = useState<string | null>(null);
  const [actionErr, setActionErr] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [rewriteNotes, setRewriteNotes] = useState("");
  const [textView, setTextView] = useState<"styled" | "raw">("styled");
  const [usageByChapter, setUsageByChapter] = useState<ChapterUsageAggregateRow[]>([]);
  const [usageErr, setUsageErr] = useState<string | null>(null);
  const [latestJob, setLatestJob] = useState<GenerationJobStatus | null>(null);
  const [jobHint, setJobHint] = useState<string | null>(null);
  const [rewriteMode, setRewriteMode] = useState<"plot" | "anti_ai">("plot");

  const refresh = useCallback(async () => {
    if (!projectId) return;
    try {
      const s = await fetchLatestChapterVersion(projectId, chapterNo);
      setSnapshot(s);
      setPollErr(null);
    } catch (e: unknown) {
      setPollErr(e instanceof Error ? e.message : String(e));
    }
    try {
      const rows = await fetchChapterUsageByChapter(projectId);
      setUsageByChapter(rows);
      setUsageErr(null);
    } catch (e: unknown) {
      setUsageErr(e instanceof Error ? e.message : String(e));
    }
    try {
      const j = await fetchLatestGenerationJob(projectId, chapterNo);
      setLatestJob(j);
    } catch {
      setLatestJob(null);
    }
  }, [projectId, chapterNo]);

  useEffect(() => {
    void refresh();
    const t = window.setInterval(() => void refresh(), 1500);
    return () => window.clearInterval(t);
  }, [refresh]);

  useEffect(() => {
    if (!snapshot) return;
    if ((snapshot.styledText || "").trim().length > 0) {
      setTextView("styled");
    } else {
      setTextView("raw");
    }
  }, [snapshot?.id, snapshot?.styledText]);

  const chapterCoachContext = useMemo(() => {
    if (!snapshot) {
      return rewriteNotes.trim() ? `【修改意见草稿】\n${rewriteNotes.trim()}` : "";
    }
    const raw =
      textView === "styled"
        ? (snapshot.styledText || "").trim() || (snapshot.chapterText || "").trim()
        : (snapshot.chapterText || "").trim();
    const bits = [raw.slice(0, 20000)];
    if (rewriteNotes.trim()) bits.push(`【修改意见草稿】\n${rewriteNotes.trim()}`);
    return bits.join("\n\n");
  }, [snapshot, textView, rewriteNotes]);

  async function onAccept(e: FormEvent) {
    e.preventDefault();
    if (!snapshot?.id) return;
    setActionErr(null);
    setBusy(true);
    try {
      await acceptChapterVersion(snapshot.id);
      await refresh();
    } catch (ex) {
      setActionErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setBusy(false);
    }
  }

  async function onModifyWithNotes(e: FormEvent) {
    e.preventDefault();
    if (!projectId || !snapshot?.id) return;
    const notes = rewriteNotes.trim();
    if (!notes) {
      setActionErr("请先填写修改意见（会交给写作管线按意见重写）。");
      return;
    }
    setActionErr(null);
    setBusy(true);
    setJobHint(null);
    try {
      await rejectChapterVersion(snapshot.id);
      const q = await postChapterGenerateAsync(projectId, chapterNo, {
        userRewriteNotes: notes,
        rewriteMode,
      });
      setJobHint(q.message ?? "已加入后台队列，请稍候查看下方进度。");
      await refresh();
    } catch (ex) {
      setActionErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setBusy(false);
    }
  }

  async function onDeleteDraft() {
    if (!snapshot?.id) return;
    if (!window.confirm("确定删除当前待审核草稿？删除后本章需重新生成。")) return;
    setActionErr(null);
    setBusy(true);
    try {
      await deleteChapterVersion(snapshot.id);
      setRewriteNotes("");
      await refresh();
    } catch (ex) {
      setActionErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setBusy(false);
    }
  }

  async function onGenerateFresh(e: FormEvent) {
    e.preventDefault();
    if (!projectId) return;
    setActionErr(null);
    setBusy(true);
    setJobHint(null);
    try {
      const body: Record<string, unknown> = {
        rewriteMode,
      };
      if (rewriteNotes.trim()) {
        body.userRewriteNotes = rewriteNotes.trim();
      }
      const q = await postChapterGenerateAsync(projectId, chapterNo, body);
      setJobHint(q.message ?? "已加入后台队列，请稍候查看下方进度。");
      await refresh();
    } catch (ex) {
      setActionErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setBusy(false);
    }
  }

  if (!projectId) {
    return <p style={{ color: "crimson" }}>缺少项目 ID</p>;
  }

  return (
    <section style={{ maxWidth: 720 }}>
      <p style={{ marginBottom: 12 }}>
        <Link to={`/projects/${encodeURIComponent(projectId)}`} style={{ color: "#2563eb", textDecoration: "none" }}>
          ← 返回作品主页
        </Link>
        {" · "}
        <Link to={`/projects/${encodeURIComponent(projectId)}/story/init`} style={{ color: "#2563eb", textDecoration: "none" }}>
          题材与大纲
        </Link>
        {" · "}
        <Link to={`/projects/${encodeURIComponent(projectId)}/graph`} style={{ color: "#2563eb", textDecoration: "none" }}>
          人物与世界观
        </Link>
        {" · "}
        <Link to={`/projects/${encodeURIComponent(projectId)}/roi`} style={{ color: "#2563eb", textDecoration: "none" }}>
          用量统计
        </Link>
        {" · "}
        <Link to={`/projects/${encodeURIComponent(projectId)}/governance`} style={{ color: "#2563eb", textDecoration: "none" }}>
          写作治理
        </Link>
      </p>
      <h1 style={{ fontSize: 22, marginBottom: 8 }}>第 {chapterNo} 章 · 写作台</h1>
      <p style={{ color: "#64748b", fontSize: 14, marginBottom: 16 }}>
        生成在后台进行，无需一直停留在本页。定稿并「接受」后，人物与伏笔会进入世界观手册供后续章节参考。
      </p>

      <CopilotChatPanel
        projectId={projectId}
        scene="chapter_coach"
        title="本章参谋"
        chapterNo={chapterNo}
        contextBlob={chapterCoachContext}
      />

      {(jobHint || latestJob) && (
        <div
          style={{
            marginTop: 12,
            padding: 12,
            borderRadius: 8,
            background: "#f0f7ff",
            border: "1px solid #cfe8ff",
            fontSize: 14,
          }}
        >
          {jobHint && (
            <p style={{ margin: "0 0 8px", fontWeight: 600 }}>
              {jobHint}
            </p>
          )}
          {latestJob && (latestJob.status === "PENDING" || latestJob.status === "RUNNING") && (
            <>
              <p style={{ margin: "0 0 6px", color: "#333" }}>
                <strong>当前步骤：</strong>
                {latestJob.currentStage ?? "排队中"}
              </p>
              <div
                style={{
                  height: 12,
                  background: "#ddeafd",
                  borderRadius: 6,
                  overflow: "hidden",
                }}
              >
                <div
                  style={{
                    width: `${Math.min(100, Math.max(0, latestJob.progressPct))}%`,
                    height: "100%",
                    background: "#2563eb",
                    transition: "width 0.4s ease",
                  }}
                />
              </div>
              <p style={{ margin: "6px 0 0", fontSize: 12, color: "#555" }}>
                页面会自动刷新状态；完成后下方会出现「待您审核」的新草稿。
              </p>
            </>
          )}
          {latestJob && latestJob.status === "FAILED" && (
            <p style={{ margin: 0, color: "crimson" }}>
              <strong>生成失败：</strong>
              {latestJob.errorMessage ?? "未知错误"}
            </p>
          )}
          {latestJob && latestJob.status === "SUCCEEDED" && latestJob.totalTokens != null && (
            <p style={{ margin: 0, fontSize: 13, color: "#14532d" }}>
              本章最近一次生成约消耗 <strong>{latestJob.totalTokens.toLocaleString()}</strong> token（估算）
              {latestJob.retryWasteTokens != null && latestJob.retryWasteTokens > 0 && (
                <>；其中约 {latestJob.retryWasteTokens.toLocaleString()} 因修改重算虚耗</>
              )}
              。可在「用量统计」查看全书。
            </p>
          )}
        </div>
      )}

      <h2 style={{ marginTop: 24, fontSize: 17 }}>当前文稿</h2>
      {pollErr && <p style={{ color: "crimson" }}>{pollErr}</p>}
      {!snapshot && !pollErr && <p style={{ color: "#64748b" }}>本章尚无生成稿，可在页面底部发起写作。</p>}
      {snapshot && (
        <div style={{ fontSize: 14, marginTop: 8 }}>
          <p style={{ marginBottom: 8 }}>
            <strong>状态：</strong>
            {friendlyVersionStatus(snapshot.status)}
            {" · "}
            <strong>自动预审：</strong>
            {snapshot.aiCriticPass ? "通过" : "未通过"}
          </p>
          {snapshot.tokenBudgetStatus && (
            <p style={{ fontSize: 13, color: "#444", marginBottom: 8 }}>
              <strong>上下文长度：</strong>
              {(() => {
                const tbs = snapshot.tokenBudgetStatus;
                const before = tbs.estimated_tokens_before ?? tbs.estimatedTokensBefore;
                const after = tbs.estimated_tokens_after ?? tbs.estimatedTokensAfter;
                if (typeof before === "number" && typeof after === "number") {
                  return `生成前参考材料约 ${before} token，精简后约 ${after} token`;
                }
                return "已按篇幅预算做过精简";
              })()}
              {(() => {
                const dropped =
                  snapshot.tokenBudgetStatus.dropped_optional_categories ??
                  snapshot.tokenBudgetStatus.droppedOptionalCategories;
                return Array.isArray(dropped) && dropped.length > 0 ? (
                  <span style={{ color: "#92400e" }}>（已暂时收起部分次要参考）</span>
                ) : null;
              })()}
            </p>
          )}
          {snapshot.llmUsageSummary &&
            typeof snapshot.llmUsageSummary === "object" &&
            (snapshot.llmUsageSummary.calls as number | undefined) !== undefined && (
              <p style={{ fontSize: 13, color: "#444", marginBottom: 8 }}>
                <strong>本次写作调用：</strong>
                {(() => {
                  const u = snapshot.llmUsageSummary;
                  const calls = u.calls as number;
                  const total =
                    (u.total_tokens as number | undefined) ??
                    (u.totalTokens as number | undefined) ??
                    0;
                  const inc =
                    (u.includes_estimates as boolean | undefined) ??
                    (u.includesEstimates as boolean | undefined);
                  return `共 ${calls} 次 · 合计约 ${total} token${inc ? "（含估算）" : ""}`;
                })()}
              </p>
            )}
          {snapshot.styledText ? (
            <div style={{ marginBottom: 8, display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
              <span style={{ fontWeight: 600 }}>正文视图：</span>
              <label style={{ cursor: "pointer" }}>
                <input
                  type="radio"
                  name="textView"
                  checked={textView === "styled"}
                  onChange={() => setTextView("styled")}
                />{" "}
                润色版（推荐）
              </label>
              <label style={{ cursor: "pointer" }}>
                <input
                  type="radio"
                  name="textView"
                  checked={textView === "raw"}
                  onChange={() => setTextView("raw")}
                />{" "}
                初稿
              </label>
            </div>
          ) : null}
          <pre
            style={{
              whiteSpace: "pre-wrap",
              background: "#fafafa",
              padding: 10,
              borderRadius: 8,
              maxHeight: 280,
              overflow: "auto",
            }}
          >
            {(() => {
              const raw = snapshot.chapterText || "";
              const styled = snapshot.styledText || "";
              const show =
                textView === "styled" && styled.trim().length > 0 ? styled : raw;
              return show.slice(0, 12000) + (show.length > 12000 ? "\n…" : "");
            })()}
          </pre>

          {snapshot.status === "PENDING_REVIEW" && (
            <div
              style={{
                marginTop: 16,
                padding: 14,
                borderRadius: 10,
                border: "1px solid #cbd5e1",
                background: "#f8fafc",
              }}
            >
              <h3 style={{ marginTop: 0, marginBottom: 10, fontSize: 16 }}>本章审核</h3>
              <ol style={{ margin: "0 0 12px", paddingLeft: 20, fontSize: 14, color: "#334155" }}>
                <li style={{ marginBottom: 8 }}>
                  <strong>确认</strong> — 满意则定稿并写入归档。
                </li>
                <li style={{ marginBottom: 8 }}>
                  <strong>修改</strong> — 在下方填写意见，沿用现有 LangGraph 管线按意见再生一版（会先退回当前稿）。
                </li>
                <li>
                  <strong>删除</strong> — 扔掉本稿，不保留记录（需重新生成）。
                </li>
              </ol>
              <div style={{ display: "flex", flexWrap: "wrap", gap: 8, alignItems: "center" }}>
                <form onSubmit={onAccept} style={{ display: "inline" }}>
                  <button type="submit" disabled={busy} style={{ padding: "8px 14px" }}>
                    ① 确认定稿
                  </button>
                </form>
                <form onSubmit={onModifyWithNotes} style={{ display: "inline" }}>
                  <button type="submit" disabled={busy} style={{ padding: "8px 14px" }}>
                    ② 按意见改稿并重新生成
                  </button>
                </form>
                <button type="button" disabled={busy} onClick={() => void onDeleteDraft()} style={{ padding: "8px 14px" }}>
                  ③ 删除本稿
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      <details style={{ marginTop: 28 }}>
        <summary style={{ cursor: "pointer", fontWeight: 600, color: "#475569" }}>
          各章累计用量（可选查看）
        </summary>
        <p style={{ fontSize: 13, color: "#64748b", marginTop: 10 }}>
          以下为本书截至目前各章写作过程的累计估算用量（多次修改会叠加）。
        </p>
        {usageErr && <p style={{ color: "crimson", fontSize: 13 }}>{usageErr}</p>}
        {!usageErr && usageByChapter.length === 0 && (
          <p style={{ fontSize: 13 }}>暂无记录。</p>
        )}
        {usageByChapter.length > 0 && (
          <table style={{ borderCollapse: "collapse", fontSize: 13, marginTop: 8 }}>
            <thead>
              <tr>
                <th style={{ border: "1px solid #ddd", padding: 6, textAlign: "left" }}>章号</th>
                <th style={{ border: "1px solid #ddd", padding: 6, textAlign: "right" }}>写作调用次数</th>
                <th style={{ border: "1px solid #ddd", padding: 6, textAlign: "right" }}>累计 token（估算）</th>
              </tr>
            </thead>
            <tbody>
              {usageByChapter.map((row) => (
                <tr
                  key={row.chapterNo}
                  style={{
                    background: row.chapterNo === chapterNo ? "#f0f7ff" : undefined,
                  }}
                >
                  <td style={{ border: "1px solid #ddd", padding: 6 }}>{row.chapterNo}</td>
                  <td style={{ border: "1px solid #ddd", padding: 6, textAlign: "right" }}>
                    {row.callCount}
                  </td>
                  <td style={{ border: "1px solid #ddd", padding: 6, textAlign: "right" }}>
                    {row.totalTokens}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </details>

      <h2 style={{ marginTop: 28, fontSize: 17 }}>
        {snapshot?.status === "PENDING_REVIEW" ? "修改意见（用于上方「按意见改稿」）" : "生成本章"}
      </h2>
      <div style={{ marginBottom: 10, fontSize: 14 }}>
        <span style={{ fontWeight: 600, marginRight: 10 }}>重写模式：</span>
        <label style={{ marginRight: 14, cursor: "pointer" }}>
          <input
            type="radio"
            name="rewriteMode"
            checked={rewriteMode === "plot"}
            onChange={() => setRewriteMode("plot")}
          />{" "}
          剧情导向（默认）
        </label>
        <label style={{ cursor: "pointer" }}>
          <input
            type="radio"
            name="rewriteMode"
            checked={rewriteMode === "anti_ai"}
            onChange={() => setRewriteMode("anti_ai")}
          />{" "}
          弱化 AI 腔（anti_ai）
        </label>
      </div>
      <textarea
        value={rewriteNotes}
        onChange={(ev) => setRewriteNotes(ev.target.value)}
        rows={4}
        style={{ width: "100%", boxSizing: "border-box" }}
        placeholder={
          snapshot?.status === "PENDING_REVIEW"
            ? "写明要改哪里：节奏、对白、人设、情节走向等（必填后点「按意见改稿」）"
            : "可选：希望本章加强的方向（首次生成或追加约束）"
        }
      />
      <div style={{ marginTop: 8, display: "flex", flexWrap: "wrap", gap: 8 }}>
        <form onSubmit={onGenerateFresh}>
          <button type="submit" disabled={busy}>
            {busy ? "提交中…" : snapshot?.status === "PENDING_REVIEW" ? "不删旧稿，直接再生成一版" : "开始生成本章"}
          </button>
        </form>
        {snapshot?.status === "PENDING_REVIEW" && (
          <span style={{ fontSize: 13, color: "#64748b", alignSelf: "center" }}>
            待审核时优先用上方「按意见改稿」；本按钮会排队新版本但不先删当前稿。
          </span>
        )}
      </div>

      {actionErr && <p style={{ color: "crimson", marginTop: 8 }}>{actionErr}</p>}
    </section>
  );
}
