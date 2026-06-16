import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import CopilotChatPanel from "../components/CopilotChatPanel";
import LoreMiniGraph from "../components/LoreMiniGraph";
import { Link, useParams } from "react-router-dom";
import {
  acceptChapterVersion,
  deleteChapterVersion,
  fetchChapterPrewritePlan,
  fetchChapterUsageByChapter,
  fetchLatestChapterVersion,
  fetchLatestCommitVector,
  postRetryChapterVectorSync,
  postChapterPrewritePlanConfirm,
  postChapterPrewritePlanProposeAi,
  postFanqieEditorReview,
  postImportPolishedDraft,
  postPolishWithNotes,
  putChapterPrewritePlan,
  rejectChapterVersion,
  type ChapterPrewritePlan,
  type ChapterUsageAggregateRow,
  type ChapterVersionSnapshot,
  type ChapterLatestCommitVector,
} from "../api/chapters";
import { postAppendGovernanceIntentLine } from "../api/story";
import {
  fetchLatestGenerationJob,
  postChapterGenerateAsync,
  type GenerationJobStatus,
} from "../api/generationJobs";
import { getProjectLoreGraph, type LoreSnapshot } from "../api/lore";
import { getProject, type Project } from "../api/projects";
import { getChapterObligationsPreview } from "../api/narrative";
import { getSetupStatus, type SetupStatus } from "../api/setup";
import { labelStoryPhase } from "../lib/uiLabels";

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

function formatPrevSummary(prev: unknown): string {
  if (prev == null || prev === "") {
    return "（暂无已定稿摘要；请先完成上一章并接受定稿，或从第 1 章开始。）";
  }
  if (typeof prev === "string") {
    return prev;
  }
  try {
    return JSON.stringify(prev, null, 2);
  } catch {
    return String(prev);
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
  const [prewrite, setPrewrite] = useState<ChapterPrewritePlan | null>(null);
  const [planDraft, setPlanDraft] = useState("");
  const [prewriteBusy, setPrewriteBusy] = useState(false);
  const [fanqieBusy, setFanqieBusy] = useState(false);
  const [polishBusy, setPolishBusy] = useState(false);
  const [importBusy, setImportBusy] = useState(false);
  const [tomatoReview, setTomatoReview] = useState("");
  const [authorPolishNotes, setAuthorPolishNotes] = useState("");
  const [polishedPreview, setPolishedPreview] = useState<string | null>(null);
  const [globalIntentLine, setGlobalIntentLine] = useState("");
  const [governanceBusy, setGovernanceBusy] = useState(false);
  const [prewriteErr, setPrewriteErr] = useState<string | null>(null);
  const [project, setProject] = useState<Project | null>(null);
  const [lore, setLore] = useState<LoreSnapshot | null>(null);
  const [loreLoading, setLoreLoading] = useState(false);
  const [loreErr, setLoreErr] = useState<string | null>(null);
  const [vectorCommit, setVectorCommit] = useState<ChapterLatestCommitVector | null>(null);
  const [vectorBusy, setVectorBusy] = useState(false);
  const [obligations, setObligations] = useState<unknown | null>(null);
  const [obligationsErr, setObligationsErr] = useState<string | null>(null);
  const [setupStatus, setSetupStatus] = useState<SetupStatus | null>(null);

  const reloadLore = useCallback(async () => {
    if (!projectId) return;
    setLoreLoading(true);
    try {
      const d = await getProjectLoreGraph(projectId);
      setLore(d);
      setLoreErr(null);
    } catch (e: unknown) {
      setLore(null);
      setLoreErr(e instanceof Error ? e.message : String(e));
    } finally {
      setLoreLoading(false);
    }
  }, [projectId]);

  useEffect(() => {
    if (!projectId) return;
    void getProject(projectId)
      .then(setProject)
      .catch(() => setProject(null));
    void getSetupStatus(projectId)
      .then(setSetupStatus)
      .catch(() => setSetupStatus(null));
  }, [projectId]);

  useEffect(() => {
    void reloadLore();
  }, [reloadLore]);

  useEffect(() => {
    if (!projectId) return;
    void getChapterObligationsPreview(projectId, chapterNo)
      .then((o) => {
        setObligations(o);
        setObligationsErr(null);
      })
      .catch((e) => {
        setObligations(null);
        setObligationsErr(e instanceof Error ? e.message : String(e));
      });
  }, [projectId, chapterNo]);

  const chapterCount = useMemo(() => {
    const fromUsage =
      usageByChapter.length > 0 ? Math.max(...usageByChapter.map((r) => r.chapterNo)) : 0;
    const target = project?.targetChapters ?? 1;
    return Math.max(target, chapterNo, fromUsage, 1);
  }, [project?.targetChapters, chapterNo, usageByChapter]);

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
    try {
      const v = await fetchLatestCommitVector(projectId, chapterNo);
      setVectorCommit(v);
    } catch {
      setVectorCommit(null);
    }
  }, [projectId, chapterNo]);

  const reloadPrewrite = useCallback(async () => {
    if (!projectId) return;
    try {
      const plan = await fetchChapterPrewritePlan(projectId, chapterNo);
      setPrewrite(plan);
      setPlanDraft(plan.planSummary ?? "");
      setPrewriteErr(null);
    } catch (e: unknown) {
      setPrewrite(null);
      setPrewriteErr(e instanceof Error ? e.message : String(e));
    }
  }, [projectId, chapterNo]);

  useEffect(() => {
    void reloadPrewrite();
  }, [reloadPrewrite]);

  /** 切换作品/章节时清空番茄润色区，避免上一章点评遗留在下一章 */
  useEffect(() => {
    setTomatoReview("");
    setAuthorPolishNotes("");
    setPolishedPreview(null);
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
      await reloadLore();
    } catch (ex) {
      setActionErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setBusy(false);
    }
  }

  async function onModifyWithNotes(e: FormEvent) {
    e.preventDefault();
    if (!projectId || !snapshot?.id) return;
    if (!prewrite?.confirmed) {
      setActionErr("请先在下方填写「本章动笔前摘要」并点击「确认摘要无误」，再生成正文。");
      return;
    }
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

  async function onDeleteCurrentVersion() {
    if (!snapshot?.id) return;
    const st = snapshot.status;
    let msg = "确定删除当前版本？";
    if (st === "PENDING_REVIEW") {
      msg =
        "确定删除当前待审核草稿？删除后若本章没有其它版本记录，需重新生成正文。";
    } else if (st === "REJECTED") {
      msg =
        "确定删除这条「已退回」版本？删除后若本章没有其它版本记录，需重新生成正文。";
    } else if (st === "ACCEPTED") {
      msg =
        "确定删除已定稿版本？将同时删除本条对应的定稿摘要（commit）与导出 Markdown；已同步到世界观的数据不会自动撤销，后续章「上一章摘要」若依赖本章也可能不准。是否继续？";
    } else {
      setActionErr("当前状态不支持删除。");
      return;
    }
    if (!window.confirm(msg)) return;
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
    if (!prewrite?.confirmed) {
      setActionErr("请先在下方填写「本章动笔前摘要」并点击「确认摘要无误」，再生成正文。");
      return;
    }
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
    return (
      <div className="mf-page" style={{ padding: 24 }}>
        <p className="mf-alert mf-alert-error">缺少项目 ID</p>
      </div>
    );
  }

  return (
    <div
      style={{
        display: "flex",
        flexDirection: "column",
        height: "100vh",
        maxHeight: "100vh",
        overflow: "hidden",
        background: "linear-gradient(180deg, var(--mf-bg) 0%, #e8ecf4 45%, #eef2f9 100%)",
        color: "var(--mf-text)",
        fontFamily: "inherit",
      }}
    >
      <header
        style={{
          flexShrink: 0,
          display: "flex",
          flexWrap: "wrap",
          alignItems: "center",
          justifyContent: "space-between",
          gap: 12,
          padding: "12px 18px",
          background: "linear-gradient(90deg, #ffffff 0%, #f8fafc 100%)",
          borderBottom: "1px solid #c7d2fe",
          boxShadow: "0 1px 0 rgba(99, 102, 241, 0.14)",
        }}
      >
        <div style={{ display: "flex", flexDirection: "column", gap: 4, minWidth: 0 }}>
          <div style={{ fontSize: 12, color: "#64748b" }}>
            <Link to="/" style={{ color: "#4f46e5", textDecoration: "none", marginRight: 14 }}>
              我的作品
            </Link>
            <Link
              to={`/projects/${encodeURIComponent(projectId)}`}
              style={{ color: "#4f46e5", textDecoration: "none" }}
            >
              ← {project?.name?.trim() ? project.name : "本书主页"}
            </Link>
          </div>
          <div style={{ fontSize: 18, fontWeight: 700, color: "#312e81", letterSpacing: "-0.02em" }}>
            第 {chapterNo} 章 · 写作台
          </div>
        </div>
        <nav style={{ display: "flex", flexWrap: "wrap", gap: 8, alignItems: "center", fontSize: 13 }}>
          {(
            [
              [setupStatus?.setupLocked ? "查看设定" : "创作向导", `/projects/${encodeURIComponent(projectId)}/setup`],
              ["人物与世界观", `/projects/${encodeURIComponent(projectId)}/graph`],
              ["用量统计", `/projects/${encodeURIComponent(projectId)}/roi`],
              ["写作治理", `/projects/${encodeURIComponent(projectId)}/governance`],
            ] as const
          ).map(([label, path]) => (
            <Link
              key={path}
              to={path}
              style={{
                color: "#4338ca",
                textDecoration: "none",
                padding: "6px 11px",
                borderRadius: 8,
                background: "rgba(99, 102, 241, 0.08)",
                border: "1px solid rgba(99, 102, 241, 0.22)",
              }}
            >
              {label}
            </Link>
          ))}
        </nav>
      </header>

      <div style={{ display: "flex", flex: 1, minHeight: 0, overflow: "hidden" }}>
        <aside
          style={{
            width: 200,
            flexShrink: 0,
            borderRight: "1px solid #cbd5e1",
            background: "linear-gradient(180deg, #f8fafc 0%, #f1f5f9 100%)",
            overflowY: "auto",
            padding: "12px 10px",
          }}
        >
          <div
            style={{
              fontSize: 11,
              fontWeight: 700,
              color: "#64748b",
              letterSpacing: "0.05em",
              marginBottom: 8,
            }}
          >
            章节目录
          </div>
          {Array.from({ length: chapterCount }, (_, idx) => idx + 1).map((n) => {
            const active = n === chapterNo;
            return (
              <Link
                key={n}
                to={`/projects/${encodeURIComponent(projectId)}/chapters/${n}/workspace`}
                style={{
                  display: "block",
                  padding: "8px 10px",
                  marginBottom: 4,
                  borderRadius: 8,
                  textDecoration: "none",
                  fontSize: 14,
                  fontWeight: active ? 700 : 500,
                  color: active ? "#312e81" : "#475569",
                  background: active ? "rgba(99, 102, 241, 0.16)" : "transparent",
                  borderLeft: active ? "3px solid #6366f1" : "3px solid transparent",
                }}
              >
                第 {n} 章
              </Link>
            );
          })}
        </aside>

        <main
          style={{
            flex: 1,
            minWidth: 0,
            overflowY: "auto",
            padding: "18px 20px 32px",
            background: "rgba(255, 255, 255, 0.78)",
          }}
        >
      {setupStatus && !setupStatus.readyToWrite && !setupStatus.setupLocked ? (
        <div
          className="mf-panel mf-panel-warn"
          style={{ marginBottom: 16, padding: "12px 14px", fontSize: 14 }}
        >
          <strong>请先完成创作向导</strong>
          <p style={{ margin: "6px 0 10px" }}>{setupStatus.nextActionHint}</p>
          <Link
            to={`/projects/${encodeURIComponent(projectId)}/setup`}
            className="mf-btn mf-btn-primary"
            style={{ display: "inline-block" }}
          >
            打开创作向导
          </Link>
        </div>
      ) : null}

      <p style={{ color: "#64748b", fontSize: 14, marginBottom: 16 }}>
        须先确认本章「动笔前摘要」后，后台才会接受生成请求。生成在后台进行，无需一直停留在本页。定稿并「接受」后，人物与伏笔会进入世界观手册供后续章节参考。
      </p>

      {prewriteErr && <p style={{ color: "crimson", fontSize: 14, marginBottom: 12 }}>{prewriteErr}</p>}

      <section className="mf-panel" style={{ marginBottom: 16 }}>
        <h2 style={{ marginTop: 0, marginBottom: 8, fontSize: 16 }}>本章写作任务</h2>
        {obligationsErr && <p style={{ color: "crimson", fontSize: 13 }}>{obligationsErr}</p>}
        {obligations != null && typeof obligations === "object" && obligations !== null ? (
          <>
            {"storyPhase" in obligations && (obligations as { storyPhase?: string }).storyPhase ? (
              <p style={{ fontSize: 13, margin: "0 0 6px", color: "#64748b" }}>
                全书阶段：{labelStoryPhase((obligations as { storyPhase?: string }).storyPhase)}
                {"phaseRules" in obligations &&
                (obligations as { phaseRules?: { phaseDisplayName?: string } }).phaseRules?.phaseDisplayName
                  ? `（${(obligations as { phaseRules?: { phaseDisplayName?: string } }).phaseRules?.phaseDisplayName}）`
                  : ""}
              </p>
            ) : null}
            {"summaryLine" in obligations ? (
              <p style={{ fontSize: 14, margin: "0 0 8px", color: "#334155" }}>
                {(obligations as { summaryLine?: string }).summaryLine || "（无特别约束）"}
              </p>
            ) : null}
            {"storyAnchor" in obligations &&
            typeof (obligations as { storyAnchor?: string }).storyAnchor === "string" ? (
              <p style={{ fontSize: 12, margin: "0 0 6px", color: "#475569", fontStyle: "italic" }}>
                {(obligations as { storyAnchor?: string }).storyAnchor}
              </p>
            ) : null}
            {"debtDueBlock" in obligations &&
            typeof (obligations as { debtDueBlock?: string }).debtDueBlock === "string" &&
            (obligations as { debtDueBlock?: string }).debtDueBlock ? (
              <pre
                style={{
                  margin: "0 0 8px",
                  fontSize: 12,
                  maxHeight: 100,
                  overflow: "auto",
                  background: "#f0fdf4",
                  padding: 8,
                  borderRadius: 6,
                  border: "1px solid #bbf7d0",
                }}
              >
                {(obligations as { debtDueBlock?: string }).debtDueBlock}
              </pre>
            ) : null}
            {"continuityBrief" in obligations &&
            typeof (obligations as { continuityBrief?: string }).continuityBrief === "string" &&
            (obligations as { continuityBrief?: string }).continuityBrief ? (
              <pre
                style={{
                  margin: "0 0 8px",
                  fontSize: 12,
                  maxHeight: 120,
                  overflow: "auto",
                  background: "#fff7ed",
                  padding: 8,
                  borderRadius: 6,
                  border: "1px solid #fed7aa",
                }}
              >
                {(obligations as { continuityBrief?: string }).continuityBrief}
              </pre>
            ) : null}
            {"narrativePromptLines" in obligations &&
            Array.isArray((obligations as { narrativePromptLines?: string[] }).narrativePromptLines) &&
            ((obligations as { narrativePromptLines?: string[] }).narrativePromptLines?.length ?? 0) > 0 ? (
              <pre
                style={{
                  margin: "0 0 8px",
                  fontSize: 12,
                  maxHeight: 140,
                  overflow: "auto",
                  background: "#f8fafc",
                  padding: 8,
                  borderRadius: 6,
                  border: "1px solid #e2e8f0",
                }}
              >
                {((obligations as { narrativePromptLines?: string[] }).narrativePromptLines ?? []).join("\n")}
              </pre>
            ) : null}
          </>
        ) : null}
        <details style={{ marginTop: 8 }}>
          <summary style={{ fontSize: 13, color: "#64748b", cursor: "pointer" }}>查看完整任务数据（高级）</summary>
          <pre
            style={{
              margin: "8px 0 0",
              fontSize: 12,
              maxHeight: 200,
              overflow: "auto",
              background: "#fff",
              padding: 10,
              borderRadius: 8,
              border: "1px solid #e2e8f0",
            }}
          >
            {obligations != null ? JSON.stringify(obligations, null, 2) : "加载中…"}
          </pre>
        </details>
      </section>

      <section
        style={{
          marginBottom: 20,
          padding: 16,
          borderRadius: 10,
          border: "1px solid #e2e8f0",
          background: "#fafbfc",
        }}
      >
        <h2 style={{ marginTop: 0, marginBottom: 10, fontSize: 17 }}>动笔前摘要（须确认后再写正文）</h2>
        {chapterNo > 1 && (
          <div style={{ marginBottom: 14 }}>
            <h3 style={{ margin: "0 0 6px", fontSize: 14, color: "#475569" }}>上一章已定稿摘要</h3>
            <pre
              style={{
                margin: 0,
                whiteSpace: "pre-wrap",
                fontSize: 13,
                maxHeight: 160,
                overflow: "auto",
                background: "#fff",
                padding: 10,
                borderRadius: 8,
                border: "1px solid #e5e7eb",
              }}
            >
              {formatPrevSummary(prewrite?.prevChapterCommitSummary)}
            </pre>
          </div>
        )}
        <h3 style={{ margin: "0 0 6px", fontSize: 14, color: "#475569" }}>本章走向 / 动笔前摘要（草稿）</h3>
        <textarea
          value={planDraft}
          onChange={(ev) => setPlanDraft(ev.target.value)}
          rows={6}
          style={{ width: "100%", boxSizing: "border-box", fontFamily: "inherit", fontSize: 14 }}
          placeholder="写明本章要写到的情节点、情绪收束、伏笔与禁忌；确认后才会交给写作管线。"
        />
        <div style={{ marginTop: 10, display: "flex", flexWrap: "wrap", gap: 8, alignItems: "center" }}>
          <button
            type="button"
            disabled={prewriteBusy}
            onClick={async () => {
              if (!projectId) return;
              setPrewriteBusy(true);
              setActionErr(null);
              try {
                const p = await putChapterPrewritePlan(projectId, chapterNo, planDraft);
                setPrewrite(p);
              } catch (ex) {
                setActionErr(ex instanceof Error ? ex.message : String(ex));
              } finally {
                setPrewriteBusy(false);
              }
            }}
          >
            {prewriteBusy ? "保存中…" : "保存摘要草稿"}
          </button>
          <button
            type="button"
            disabled={prewriteBusy}
            onClick={async () => {
              if (!projectId) return;
              setPrewriteBusy(true);
              setActionErr(null);
              try {
                const text = await postChapterPrewritePlanProposeAi(projectId, chapterNo);
                setPlanDraft(text);
                const p = await putChapterPrewritePlan(projectId, chapterNo, text);
                setPrewrite(p);
              } catch (ex) {
                setActionErr(ex instanceof Error ? ex.message : String(ex));
              } finally {
                setPrewriteBusy(false);
              }
            }}
          >
            AI 起草摘要
          </button>
          <button
            type="button"
            disabled={prewriteBusy}
            onClick={async () => {
              if (!projectId) return;
              setPrewriteBusy(true);
              setActionErr(null);
              try {
                const p = await postChapterPrewritePlanConfirm(projectId, chapterNo);
                setPrewrite(p);
                setPlanDraft(p.planSummary ?? "");
              } catch (ex) {
                setActionErr(ex instanceof Error ? ex.message : String(ex));
              } finally {
                setPrewriteBusy(false);
              }
            }}
          >
            确认摘要无误（解锁生成）
          </button>
          <span style={{ fontSize: 13, color: prewrite?.confirmed ? "#15803d" : "#b45309" }}>
            {prewrite?.confirmed ? "已确认，可生成正文。" : "未确认：修改摘要后需重新保存并再次确认。"}
          </span>
          <p style={{ fontSize: 12, color: "#64748b", marginTop: 10, marginBottom: 0, maxWidth: 640 }}>
            若<strong>摘要正文</strong>与上次保存不同，保存时会自动删除本章所有「待审核 / 已退回」旧稿、排队任务及本章用量日志，避免仍显示上一轮生成的统计。
          </p>
        </div>
      </section>

      <section
        style={{
          marginBottom: 20,
          padding: 16,
          borderRadius: 10,
          border: "1px solid #e2e8f0",
          background: "#fffdf8",
        }}
      >
        <h2 style={{ marginTop: 0, marginBottom: 10, fontSize: 17 }}>番茄编辑点评与润色</h2>
        <p style={{ fontSize: 13, color: "#64748b", marginTop: 0 }}>
          以平台编辑视角点评当前稿。点评生成后<strong>可随时修改</strong>「番茄编辑意见」与「作者补充」，再点「合并意见润色」或再次运行点评；与是否已确认本章摘要、是否已定稿无关。
        </p>
        <div style={{ display: "flex", flexWrap: "wrap", gap: 8, marginBottom: 10 }}>
          <button
            type="button"
            disabled={fanqieBusy}
            onClick={async () => {
              if (!projectId) return;
              setFanqieBusy(true);
              setActionErr(null);
              try {
                const r = await postFanqieEditorReview(projectId, chapterNo);
                setTomatoReview(r);
              } catch (ex) {
                setActionErr(ex instanceof Error ? ex.message : String(ex));
              } finally {
                setFanqieBusy(false);
              }
            }}
          >
            {fanqieBusy ? "点评中…" : "运行番茄编辑点评"}
          </button>
        </div>
        <label style={{ fontSize: 13, fontWeight: 600 }}>番茄编辑意见（可改）</label>
        <textarea
          value={tomatoReview}
          onChange={(ev) => setTomatoReview(ev.target.value)}
          rows={5}
          style={{ width: "100%", boxSizing: "border-box", marginTop: 4, marginBottom: 10 }}
        />
        <label style={{ fontSize: 13, fontWeight: 600 }}>作者补充意见</label>
        <textarea
          value={authorPolishNotes}
          onChange={(ev) => setAuthorPolishNotes(ev.target.value)}
          rows={3}
          style={{ width: "100%", boxSizing: "border-box", marginTop: 4, marginBottom: 10 }}
          placeholder="可选：你希望润色时额外强调的腔调、删改点等"
        />
        <div style={{ display: "flex", flexWrap: "wrap", gap: 8, marginBottom: 10 }}>
          <button
            type="button"
            disabled={polishBusy}
            onClick={async () => {
              if (!projectId) return;
              setPolishBusy(true);
              setActionErr(null);
              try {
                const out = await postPolishWithNotes(projectId, chapterNo, {
                  tomatoReview,
                  authorNotes: authorPolishNotes,
                });
                setPolishedPreview(out);
              } catch (ex) {
                setActionErr(ex instanceof Error ? ex.message : String(ex));
              } finally {
                setPolishBusy(false);
              }
            }}
          >
            {polishBusy ? "润色中…" : "合并意见润色"}
          </button>
          <button
            type="button"
            disabled={importBusy || !polishedPreview?.trim()}
            onClick={async () => {
              if (!projectId || !polishedPreview?.trim()) return;
              setImportBusy(true);
              setActionErr(null);
              try {
                await postImportPolishedDraft(projectId, chapterNo, polishedPreview, polishedPreview);
                setPolishedPreview(null);
                await refresh();
              } catch (ex) {
                setActionErr(ex instanceof Error ? ex.message : String(ex));
              } finally {
                setImportBusy(false);
              }
            }}
          >
            {importBusy ? "写入中…" : "将润色稿存为新待审核版"}
          </button>
        </div>
        {polishedPreview && (
          <div>
            <h3 style={{ fontSize: 14, marginBottom: 6 }}>润色预览</h3>
            <pre
              style={{
                whiteSpace: "pre-wrap",
                maxHeight: 220,
                overflow: "auto",
                background: "#fafafa",
                padding: 10,
                borderRadius: 8,
                fontSize: 13,
              }}
            >
              {polishedPreview.slice(0, 24000)}
              {polishedPreview.length > 24000 ? "\n…" : ""}
            </pre>
          </div>
        )}
      </section>

      <section
        style={{
          marginBottom: 20,
          padding: 16,
          borderRadius: 10,
          border: "1px solid #e2e8f0",
          background: "#f8fafc",
        }}
      >
        <h2 style={{ marginTop: 0, marginBottom: 8, fontSize: 17 }}>全书用语 / 风格（追加到作者意图）</h2>
        <p style={{ fontSize: 13, color: "#64748b", marginTop: 0 }}>
          写入当前选中 Story 快照的「作者意图」末尾，并自动加「【全局】」前缀，后续各章生成都会带上。
        </p>
        <textarea
          value={globalIntentLine}
          onChange={(ev) => setGlobalIntentLine(ev.target.value)}
          rows={2}
          style={{ width: "100%", boxSizing: "border-box", marginBottom: 8 }}
          placeholder="例如：禁用「映入眼帘」；对话占比偏高等"
        />
        <button
          type="button"
          disabled={governanceBusy || !globalIntentLine.trim()}
          onClick={async () => {
            if (!projectId) return;
            setGovernanceBusy(true);
            setActionErr(null);
            try {
              await postAppendGovernanceIntentLine(projectId, globalIntentLine.trim());
              setGlobalIntentLine("");
            } catch (ex) {
              setActionErr(ex instanceof Error ? ex.message : String(ex));
            } finally {
              setGovernanceBusy(false);
            }
          }}
        >
          {governanceBusy ? "写入中…" : "追加到作者意图"}
        </button>
      </section>

      <h2 style={{ marginTop: 24, fontSize: 17 }}>当前文稿</h2>
      {pollErr && <p style={{ color: "crimson" }}>{pollErr}</p>}
      {!snapshot && !pollErr && (
        <p style={{ color: "#64748b" }}>本章尚无生成稿，可在右侧栏发起写作。</p>
      )}
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
              padding: 12,
              borderRadius: 8,
              minHeight: 240,
              maxHeight: "min(68vh, 880px)",
              overflow: "auto",
              border: "1px solid #e5e7eb",
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

          {(snapshot.status === "PENDING_REVIEW" ||
            snapshot.status === "REJECTED" ||
            snapshot.status === "ACCEPTED") && (
              <div style={{ marginTop: 12 }}>
                <button
                  type="button"
                  disabled={busy}
                  onClick={() => void onDeleteCurrentVersion()}
                  style={{ padding: "8px 14px" }}
                >
                  删除当前版本
                </button>
                {snapshot.status === "ACCEPTED" && (
                  <p style={{ margin: "8px 0 0", fontSize: 12, color: "#92400e", maxWidth: 560 }}>
                    已定稿删除会移除本章该版本的归档与导出文件；Neo4j 等外部同步不会自动回滚。
                  </p>
                )}
              </div>
            )}

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
                  <strong>修改</strong> — 在右侧栏填写意见，沿用现有 LangGraph 管线按意见再生一版（会先退回当前稿）。
                </li>
              </ol>
              <p style={{ margin: "0 0 12px", fontSize: 13, color: "#64748b" }}>
                不需要本稿时，请用中栏正文下的「删除当前版本」。
              </p>
              <div style={{ display: "flex", flexWrap: "wrap", gap: 8, alignItems: "center" }}>
                <form onSubmit={onAccept} style={{ display: "inline" }}>
                  <button type="submit" disabled={busy} style={{ padding: "8px 14px" }}>
                    ① 确认定稿
                  </button>
                </form>
                <form onSubmit={onModifyWithNotes} style={{ display: "inline" }}>
                  <button type="submit" disabled={busy || !prewrite?.confirmed} style={{ padding: "8px 14px" }}>
                    ② 按意见改稿并重新生成
                  </button>
                </form>
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

      {vectorCommit && (
        <div
          style={{
            marginTop: 12,
            padding: "10px 12px",
            borderRadius: 8,
            background:
              vectorCommit.vectorSyncStatus === "OK"
                ? "#ecfdf5"
                : vectorCommit.vectorSyncStatus === "PENDING"
                  ? "#fffbeb"
                  : "#fef2f2",
            fontSize: 13,
            color: "#334155",
          }}
        >
          <strong>向量索引（Qdrant）</strong>：{vectorCommit.vectorSyncStatus ?? "—"}
          {vectorCommit.vectorSyncAttempts > 0 && (
            <span style={{ marginLeft: 8, color: "#64748b" }}>
              尝试 {vectorCommit.vectorSyncAttempts} 次
            </span>
          )}
          {vectorCommit.vectorSyncError && (
            <div style={{ marginTop: 6, color: "#b91c1c", whiteSpace: "pre-wrap" }}>
              {vectorCommit.vectorSyncError}
            </div>
          )}
          {(vectorCommit.vectorSyncStatus === "FAILED" ||
            vectorCommit.vectorSyncStatus === "SKIPPED") && (
            <button
              type="button"
              style={{ marginTop: 8 }}
              disabled={vectorBusy}
              onClick={async () => {
                if (!vectorCommit.commitId) return;
                setVectorBusy(true);
                setActionErr(null);
                try {
                  await postRetryChapterVectorSync(vectorCommit.commitId);
                  await refresh();
                } catch (ex) {
                  setActionErr(ex instanceof Error ? ex.message : String(ex));
                } finally {
                  setVectorBusy(false);
                }
              }}
            >
              重试向量同步
            </button>
          )}
        </div>
      )}

      {actionErr && <p style={{ color: "crimson", marginTop: 8 }}>{actionErr}</p>}
        </main>

        <aside
          style={{
            width: "32%",
            minWidth: 268,
            maxWidth: 420,
            flexShrink: 0,
            display: "flex",
            flexDirection: "column",
            minHeight: 0,
            alignSelf: "stretch",
            borderLeft: "1px solid #cbd5e1",
            background: "linear-gradient(180deg, #fafbff 0%, #f4f6fb 100%)",
          }}
        >
          <div
            style={{
              flex: "2 1 0",
              minHeight: 0,
              overflowY: "auto",
              padding: "14px 14px 16px",
              display: "flex",
              flexDirection: "column",
              gap: 12,
            }}
          >
            <CopilotChatPanel
              key={`${projectId}-${chapterNo}`}
              projectId={projectId}
              scene="chapter_coach"
              title="本章参谋"
              chapterNo={chapterNo}
              contextBlob={chapterCoachContext}
              embedded
              messagesMaxHeight={168}
            />

            {(jobHint || latestJob) && (
              <div
                style={{
                  padding: 12,
                  borderRadius: 8,
                  background: "#f0f7ff",
                  border: "1px solid #cfe8ff",
                  fontSize: 13,
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
                      页面会自动刷新状态；完成后中栏会出现「待您审核」的新草稿。
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
                  <p style={{ margin: 0, fontSize: 12, color: "#14532d" }}>
                    本章最近一次生成约消耗 <strong>{latestJob.totalTokens.toLocaleString()}</strong> token（估算）
                    {latestJob.retryWasteTokens != null && latestJob.retryWasteTokens > 0 && (
                      <>；其中约 {latestJob.retryWasteTokens.toLocaleString()} 因修改重算虚耗</>
                    )}
                    。
                  </p>
                )}
              </div>
            )}

            <div
              style={{
                padding: 12,
                borderRadius: 10,
                border: "1px solid #e2e8f0",
                background: "#fff",
              }}
            >
              <h2 style={{ marginTop: 0, marginBottom: 8, fontSize: 15, color: "#334155" }}>
                {snapshot?.status === "PENDING_REVIEW" ? "修改意见与再生成" : "生成本章"}
              </h2>
              <p style={{ margin: "0 0 10px", fontSize: 12, color: "#64748b", lineHeight: 1.45 }}>
                首次生成只需在中栏<strong>确认「动笔前摘要」</strong>即可点「开始生成」。下面「生成取向」是风格开关，不是「必须先重写」；修改意见框<strong>可留空</strong>。
              </p>
              <div style={{ marginBottom: 8, fontSize: 13 }}>
                <span style={{ fontWeight: 600, marginRight: 8 }}>生成取向：</span>
                <label style={{ marginRight: 10, cursor: "pointer" }}>
                  <input
                    type="radio"
                    name="rewriteMode"
                    checked={rewriteMode === "plot"}
                    onChange={() => setRewriteMode("plot")}
                  />{" "}
                  剧情为主
                </label>
                <label style={{ cursor: "pointer" }}>
                  <input
                    type="radio"
                    name="rewriteMode"
                    checked={rewriteMode === "anti_ai"}
                    onChange={() => setRewriteMode("anti_ai")}
                  />{" "}
                  弱化 AI 腔
                </label>
              </div>
              <div style={{ fontSize: 12, fontWeight: 600, color: "#475569", marginBottom: 4 }}>修改意见（可选）</div>
              <textarea
                value={rewriteNotes}
                onChange={(ev) => setRewriteNotes(ev.target.value)}
                rows={4}
                style={{
                  width: "100%",
                  boxSizing: "border-box",
                  minWidth: 0,
                  fontFamily: "inherit",
                  fontSize: 13,
                }}
                placeholder={
                  snapshot?.status === "PENDING_REVIEW"
                    ? "要改哪里写在这里；点中栏「按意见改稿并重新生成」时必填。也可只点本栏「开始生成」排队新版。"
                    : "可不填。若有额外希望（节奏、人设禁忌等）可写；会一并交给写作管线。"
                }
              />
              <div style={{ marginTop: 8, display: "flex", flexWrap: "wrap", gap: 8 }}>
                <form onSubmit={onGenerateFresh}>
                  <button type="submit" disabled={busy || !prewrite?.confirmed}>
                    {busy ? "提交中…" : snapshot?.status === "PENDING_REVIEW" ? "不删旧稿再生成" : "开始生成"}
                  </button>
                </form>
              </div>
              {snapshot?.status === "PENDING_REVIEW" && (
                <p style={{ fontSize: 12, color: "#64748b", margin: "8px 0 0" }}>
                  待审核时优先用中栏「按意见改稿」；本按钮会排队新版本。
                </p>
              )}
            </div>
          </div>

          <div
            style={{
              flexShrink: 0,
              height: "clamp(150px, 30vh, 300px)",
              borderTop: "1px solid #c7d2fe",
              background: "rgba(255,255,255,0.92)",
              padding: "8px 12px 10px",
              display: "flex",
              flexDirection: "column",
              overflow: "hidden",
              boxShadow: "0 -4px 12px rgba(15, 23, 42, 0.06)",
            }}
          >
            <div style={{ display: "flex", alignItems: "baseline", justifyContent: "space-between", gap: 8, marginBottom: 4, flexShrink: 0 }}>
              <span style={{ fontSize: 13, fontWeight: 700, color: "#3730a3" }}>人物关系简图</span>
              <Link
                to={`/projects/${encodeURIComponent(projectId)}/graph`}
                style={{ fontSize: 12, color: "#4f46e5", textDecoration: "none", whiteSpace: "nowrap" }}
              >
                完整手册 →
              </Link>
            </div>
            <div style={{ flex: 1, minHeight: 0, overflow: "hidden", display: "flex", flexDirection: "column" }}>
              <LoreMiniGraph data={lore} error={loreErr} loading={loreLoading} />
            </div>
          </div>
        </aside>
      </div>
    </div>
  );
}
