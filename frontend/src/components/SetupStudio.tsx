import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import CopilotChatPanel from "./CopilotChatPanel";
import { NarrativeProposalPreview } from "./NarrativeProposalPreview";
import {
  postGenreInterview,
  type GenreInterviewChatTurn,
  type GenreInterviewResponse,
} from "../api/genre";
import { listWriterSkills, type WriterSkillOption } from "../api/writerSkills";
import {
  applyGenre,
  applyNarrative,
  applyStory,
  getSetupProposal,
  getSetupStatus,
  proposeAll,
  proposeGenre,
  proposeGenreFromInterview,
  proposeNarrative,
  proposeStory,
  reviseGenre,
  reviseNarrative,
  reviseStory,
  setSetupMode,
  type SetupProposal,
  type SetupStatus,
} from "../api/setup";

const STAGES_INIT = [
  { id: "genre", label: "题材方案", step: 1 },
  { id: "story", label: "故事契约", step: 2 },
  { id: "narrative", label: "故事结构", step: 3 },
  { id: "ready", label: "开始写章", step: 4 },
] as const;

const STAGES_REVIEW = [
  { id: "genre", label: "查看题材", step: 1 },
  { id: "story", label: "查看契约", step: 2 },
  { id: "narrative", label: "查看结构", step: 3 },
  { id: "ready", label: "继续写作", step: 4 },
] as const;

type StageId = (typeof STAGES_INIT)[number]["id"];

function parseStage(v: string | null | undefined): StageId | null {
  if (v === "genre" || v === "story" || v === "narrative" || v === "ready") return v;
  return null;
}

function genreHook(contract: unknown): string {
  if (contract == null || typeof contract !== "object") return "（暂无摘要）";
  const c = contract as { recommendedCoreHook?: string; selectedDirection?: { genre?: string; reason?: string } };
  const parts = [c.recommendedCoreHook, c.selectedDirection?.genre, c.selectedDirection?.reason].filter(Boolean);
  return parts.join(" · ") || "（暂无摘要）";
}

function storyPreview(bundle: unknown): string {
  if (bundle == null || typeof bundle !== "object") return "（暂无摘要）";
  const b = bundle as { firstVolumeOutline?: string; chapterContracts?: unknown[] };
  const n = Array.isArray(b.chapterContracts) ? b.chapterContracts.length : 0;
  const outline = b.firstVolumeOutline;
  const parts = [
    n > 0 ? `章契约 ${n} 章` : null,
    outline ? outline.slice(0, 320) + (outline.length > 320 ? "…" : "") : null,
  ].filter(Boolean);
  return parts.length ? parts.join("\n\n") : "（暂无摘要）";
}

function initBundleChapterCount(bundle: unknown): number {
  if (bundle == null || typeof bundle !== "object") return 0;
  const cc = (bundle as { chapterContracts?: unknown[] }).chapterContracts;
  return Array.isArray(cc) ? cc.length : 0;
}

export default function SetupStudio({
  projectId,
  initialStage,
}: {
  projectId: string;
  initialStage?: string | null;
}) {
  const [status, setStatus] = useState<SetupStatus | null>(null);
  const [activeStage, setActiveStage] = useState<StageId>("genre");
  const [mode, setMode] = useState<"standard" | "skill">("standard");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  const [genreProposal, setGenreProposal] = useState<SetupProposal | null>(null);
  const [storyProposal, setStoryProposal] = useState<SetupProposal | null>(null);
  const [narrativeProposal, setNarrativeProposal] = useState<SetupProposal | null>(null);
  const [feedback, setFeedback] = useState("");

  const [targetPlatform, setTargetPlatform] = useState("番茄");
  const [genderChannel, setGenderChannel] = useState("男频");
  const [storyHook, setStoryHook] = useState("");
  const [useLlmNarrative, setUseLlmNarrative] = useState(false);

  const [skills, setSkills] = useState<WriterSkillOption[]>([]);
  const [skillId, setSkillId] = useState("");
  const [interviewMessages, setInterviewMessages] = useState<GenreInterviewChatTurn[]>([]);
  const [interviewInput, setInterviewInput] = useState("");
  const [interviewDone, setInterviewDone] = useState<GenreInterviewResponse | null>(null);
  const [proposeAllMsg, setProposeAllMsg] = useState<string | null>(null);

  const locked = status?.setupLocked === true;
  const stages = locked ? STAGES_REVIEW : STAGES_INIT;
  const resume = status?.resumeChapterNo && status.resumeChapterNo > 0 ? status.resumeChapterNo : 1;

  const hydratePendingProposals = useCallback(
    async (s: SetupStatus, ids?: Record<string, string>) => {
      if (s.setupLocked) {
        setGenreProposal(null);
        setStoryProposal(null);
        setNarrativeProposal(null);
        return;
      }
      const genreId = ids?.genreProposalId ?? s.pendingGenreProposalId;
      const storyId = ids?.storyProposalId ?? s.pendingStoryProposalId;
      const narrativeId = ids?.narrativeProposalId ?? s.pendingNarrativeProposalId;

      if (genreId) {
        try {
          setGenreProposal(await getSetupProposal(projectId, genreId));
        } catch {
          setGenreProposal(null);
        }
      } else setGenreProposal(null);

      if (storyId) {
        try {
          setStoryProposal(await getSetupProposal(projectId, storyId));
        } catch {
          setStoryProposal(null);
        }
      } else setStoryProposal(null);

      if (narrativeId) {
        try {
          setNarrativeProposal(await getSetupProposal(projectId, narrativeId));
        } catch {
          setNarrativeProposal(null);
        }
      } else setNarrativeProposal(null);

      if (genreId && !s.genreConfirmed) setActiveStage("genre");
      else if (storyId && !s.storyConfirmed) setActiveStage("story");
      else if (narrativeId && !s.narrativeConfirmed) setActiveStage("narrative");
    },
    [projectId]
  );

  const refresh = useCallback(
    async (proposeAllIds?: Record<string, string>) => {
      const s = await getSetupStatus(projectId);
      setStatus(s);
      setMode(s.setupMode === "skill" ? "skill" : "standard");
      await hydratePendingProposals(s, proposeAllIds);
      if (!proposeAllIds) {
        const fromUrl = parseStage(initialStage);
        if (fromUrl) setActiveStage(fromUrl);
        else if (s.setupLocked) setActiveStage("ready");
        else if (!s.genreConfirmed) setActiveStage("genre");
        else if (!s.storyConfirmed) setActiveStage("story");
        else if (!s.narrativeConfirmed) setActiveStage("narrative");
        else setActiveStage("ready");
      }
    },
    [projectId, hydratePendingProposals, initialStage]
  );

  useEffect(() => {
    void refresh().catch((e) => setErr(e instanceof Error ? e.message : String(e)));
    listWriterSkills()
      .then((r) => setSkills(r.skills))
      .catch(() => setSkills([]));
  }, [refresh]);

  async function run<T>(fn: () => Promise<T>): Promise<T | null> {
    setErr(null);
    setBusy(true);
    try {
      return await fn();
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
      return null;
    } finally {
      setBusy(false);
    }
  }

  const narrativeDomain = (p: SetupProposal | null) => {
    const payload = p?.payload as { narrativeDomain?: unknown } | undefined;
    return payload?.narrativeDomain;
  };

  return (
    <div className="mf-setup-shell">
      <nav className="mf-setup-nav">
        <p className="mf-muted mf-text-sm" style={{ marginTop: 0, marginBottom: 14 }}>
          {status?.nextActionHint ?? "加载中…"}
        </p>
        {locked && (
          <span className="mf-badge mf-badge-warn" style={{ marginBottom: 14 }}>
            设定已锁定
          </span>
        )}
        {stages.map((st) => {
          const done =
            st.id === "genre"
              ? status?.genreConfirmed
              : st.id === "story"
                ? status?.storyConfirmed
                : st.id === "narrative"
                  ? status?.narrativeConfirmed
                  : status?.readyToWrite;
          return (
            <button
              key={st.id}
              type="button"
              className={`mf-step-btn ${activeStage === st.id ? "mf-step-btn-active" : ""}`}
              onClick={() => setActiveStage(st.id)}
            >
              <span className={`mf-step-dot ${done ? "mf-step-dot-done" : ""}`}>
                {done ? "✓" : st.step}
              </span>
              {st.label}
            </button>
          );
        })}
        {!locked && (
          <div style={{ marginTop: 16, paddingTop: 16, borderTop: "1px solid var(--mf-border)" }}>
            <label className="mf-label">入口模式</label>
            <select
              className="mf-select"
              value={mode}
              disabled={busy}
              onChange={(e) => {
                const m = e.target.value as "standard" | "skill";
                setMode(m);
                void run(() => setSetupMode(projectId, m)).then(() => refresh());
              }}
            >
              <option value="standard">标准（偏好推荐）</option>
              <option value="skill">Skill 自定义</option>
            </select>
          </div>
        )}
      </nav>

      <div className="mf-setup-main">
        {err && <p className="mf-alert mf-alert-error">{err}</p>}
        {locked && (
          <div className="mf-alert mf-alert-info mf-text-sm">
            本书已有写作进度（已定稿 {status?.acceptedChapterCount ?? 0} 章），题材与故事契约不可重新初始化。
            微调故事线请前往项目主页的「故事结构」标签。
          </div>
        )}
        {proposeAllMsg && !locked && (
          <p className="mf-alert mf-alert-info mf-text-sm">{proposeAllMsg}</p>
        )}

        {!locked && (
          <div style={{ marginBottom: 16 }}>
            <button
              type="button"
              className="mf-btn mf-btn-secondary"
              disabled={busy}
              onClick={() =>
                void run(() =>
                  proposeAll(projectId, {
                    targetPlatform,
                    genderChannel,
                    storyHook: storyHook || undefined,
                  })
                ).then((res) => {
                  if (res?.message) setProposeAllMsg(res.message);
                  return refresh(res ?? undefined);
                })
              }
            >
              按当前进度一键生成草案
            </button>
            <span className="mf-muted mf-text-sm" style={{ marginLeft: 10 }}>
              不自动采纳；需在各阶段分别确认
            </span>
          </div>
        )}

        {activeStage === "genre" && (
          <div className="mf-card mf-card-pad">
            <h3 className="mf-section-title" style={{ marginTop: 0 }}>
              {locked ? "已定题材" : "题材方案"}
            </h3>
            {locked ? (
              <div className="mf-readonly-card">{genreHook(status?.confirmedGenrePreview)}</div>
            ) : (
              <>
                {mode === "standard" ? (
                  <div style={{ display: "grid", gap: 12, maxWidth: 440, marginBottom: 12 }}>
                    <label className="mf-field">
                      <span className="mf-label">平台</span>
                      <input className="mf-input" value={targetPlatform} onChange={(e) => setTargetPlatform(e.target.value)} />
                    </label>
                    <label className="mf-field">
                      <span className="mf-label">频道</span>
                      <input className="mf-input" value={genderChannel} onChange={(e) => setGenderChannel(e.target.value)} />
                    </label>
                    <label className="mf-field">
                      <span className="mf-label">故事创意（可选）</span>
                      <textarea className="mf-textarea" value={storyHook} onChange={(e) => setStoryHook(e.target.value)} rows={2} />
                    </label>
                    <button
                      type="button"
                      className="mf-btn mf-btn-primary"
                      disabled={busy}
                      onClick={() =>
                        void run(() =>
                          proposeGenre(projectId, {
                            targetPlatform,
                            genderChannel,
                            storyHook: storyHook || undefined,
                          })
                        ).then((p) => p && setGenreProposal(p))
                      }
                    >
                      AI 生成题材草案
                    </button>
                  </div>
                ) : (
                  <div style={{ marginBottom: 12 }}>
                    <label className="mf-field">
                      <span className="mf-label">Skill</span>
                      <select className="mf-select" value={skillId} onChange={(e) => setSkillId(e.target.value)}>
                        <option value="">选择 Skill…</option>
                        {skills.map((s) => (
                          <option key={s.id} value={s.id}>
                            {s.label}
                          </option>
                        ))}
                      </select>
                    </label>
                    <div style={{ marginTop: 10, maxHeight: 200, overflow: "auto", fontSize: 13 }}>
                      {interviewMessages.map((m, i) => (
                        <p key={i} style={{ margin: "4px 0" }}>
                          <strong>{m.role === "user" ? "你" : "AI"}：</strong>
                          {m.content}
                        </p>
                      ))}
                    </div>
                    <div style={{ display: "flex", gap: 8, marginTop: 8 }}>
                      <input
                        className="mf-input"
                        value={interviewInput}
                        onChange={(e) => setInterviewInput(e.target.value)}
                        style={{ flex: 1 }}
                        disabled={interviewDone?.status === "complete"}
                      />
                      <button
                        type="button"
                        className="mf-btn"
                        disabled={busy || !interviewInput.trim()}
                        onClick={() => {
                          const text = interviewInput.trim();
                          const history = [...interviewMessages, { role: "user" as const, content: text }];
                          setInterviewMessages(history);
                          setInterviewInput("");
                          void run(() =>
                            postGenreInterview(projectId, history, {
                              writerSkillId: skillId || undefined,
                            })
                          ).then((res) => {
                            if (!res) return;
                            setInterviewMessages((prev) => [...prev, { role: "assistant", content: res.replyToUser }]);
                            if (res.status === "complete") {
                              setInterviewDone(res);
                              void run(() => proposeGenreFromInterview(projectId, res)).then((p) => p && setGenreProposal(p));
                            }
                          });
                        }}
                      >
                        发送
                      </button>
                    </div>
                  </div>
                )}
                {genreProposal && (
                  <ProposalPanel
                    title="待确认题材草案"
                    reply={genreProposal.assistantReply}
                    confirmedPreview={status?.confirmedGenrePreview ? genreHook(status.confirmedGenrePreview) : null}
                    draftPreview={genreHook((genreProposal.payload as { contract?: unknown }).contract)}
                    feedback={feedback}
                    onFeedback={setFeedback}
                    busy={busy}
                    onRevise={() =>
                      void run(() => reviseGenre(projectId, feedback)).then((p) => p && setGenreProposal(p))
                    }
                    onApply={() =>
                      void run(() => applyGenre(projectId, genreProposal.id)).then(() => {
                        setGenreProposal(null);
                        return refresh();
                      })
                    }
                    onDiscard={() => setGenreProposal(null)}
                  />
                )}
                {status?.genreConfirmed && !genreProposal && (
                  <div className="mf-readonly-card">{genreHook(status.confirmedGenrePreview)}</div>
                )}
              </>
            )}
          </div>
        )}

        {activeStage === "story" && (
          <div className="mf-card mf-card-pad">
            <h3 className="mf-section-title" style={{ marginTop: 0 }}>
              {locked ? "已定故事契约" : "故事契约与第一卷大纲"}
            </h3>
            {locked ? (
              <div className="mf-readonly-card">{storyPreview(status?.confirmedStoryPreview)}</div>
            ) : (
              <>
                <p className="mf-muted mf-text-sm">基于已确认题材，生成人物、世界观与第一卷走向（确认后才写入数据库）。</p>
                <button
                  type="button"
                  className="mf-btn mf-btn-primary"
                  disabled={busy || !status?.genreConfirmed}
                  onClick={() => void run(() => proposeStory(projectId)).then((p) => p && setStoryProposal(p))}
                >
                  AI 生成故事契约草案
                </button>
                {storyProposal && (
                  <ProposalPanel
                    title="待确认故事契约"
                    reply={storyProposal.assistantReply}
                    draftPreview={(() => {
                      const bundle = (storyProposal.payload as { initBundle?: unknown })?.initBundle;
                      const n = initBundleChapterCount(bundle);
                      const outline =
                        bundle && typeof bundle === "object"
                          ? (bundle as { firstVolumeOutline?: string }).firstVolumeOutline
                          : undefined;
                      const parts = [
                        n > 0 ? `章契约 ${n} 章` : null,
                        outline ? outline.slice(0, 280) + (outline.length > 280 ? "…" : "") : null,
                      ].filter(Boolean);
                      return parts.length ? parts.join("\n\n") : "（见下方 JSON）";
                    })()}
                    feedback={feedback}
                    onFeedback={setFeedback}
                    busy={busy}
                    onRevise={() => void run(() => reviseStory(projectId, feedback)).then((p) => p && setStoryProposal(p))}
                    onApply={() =>
                      void run(() => applyStory(projectId, storyProposal.id)).then(() => {
                        setStoryProposal(null);
                        return refresh();
                      })
                    }
                    onDiscard={() => setStoryProposal(null)}
                    extra={
                      <details style={{ marginTop: 8 }}>
                        <summary className="mf-text-sm">完整 JSON</summary>
                        <pre className="mf-pre" style={{ fontSize: 11, maxHeight: 200, overflow: "auto" }}>
                          {JSON.stringify((storyProposal.payload as { initBundle?: unknown }).initBundle, null, 2)}
                        </pre>
                      </details>
                    }
                  />
                )}
                {status?.storyConfirmed && !storyProposal && (
                  <div className="mf-readonly-card">{storyPreview(status.confirmedStoryPreview)}</div>
                )}
              </>
            )}
          </div>
        )}

        {activeStage === "narrative" && (
          <div className="mf-card mf-card-pad">
            <h3 className="mf-section-title" style={{ marginTop: 0 }}>
              {locked ? "已定故事结构" : "故事结构"}
            </h3>
            {locked ? (
              status?.confirmedNarrativePreview != null ? (
                <NarrativeProposalPreview domain={status.confirmedNarrativePreview} />
              ) : (
                <div className="mf-readonly-card">（暂无结构预览）</div>
              )
            ) : (
              <>
                <p className="mf-muted mf-text-sm">主线、支线、汇合点与伏笔；与写章任务单直接关联。</p>
                <label style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
                  <input type="checkbox" checked={useLlmNarrative} onChange={(e) => setUseLlmNarrative(e.target.checked)} />
                  使用 AI 深度规划（需 Writer 可用；否则用规则草案）
                </label>
                <button
                  type="button"
                  className="mf-btn mf-btn-primary"
                  disabled={busy || !status?.storyConfirmed}
                  onClick={() =>
                    void run(() => proposeNarrative(projectId, useLlmNarrative)).then((p) => p && setNarrativeProposal(p))
                  }
                >
                  生成故事结构草案
                </button>
                {narrativeProposal && (
                  <ProposalPanel
                    title="待确认故事结构"
                    reply={narrativeProposal.assistantReply}
                    draftPreview={<NarrativeProposalPreview domain={narrativeDomain(narrativeProposal)} />}
                    confirmedPreview={
                      status?.confirmedNarrativePreview ? (
                        <NarrativeProposalPreview domain={status.confirmedNarrativePreview} />
                      ) : null
                    }
                    feedback={feedback}
                    onFeedback={setFeedback}
                    busy={busy}
                    onRevise={() =>
                      void run(() => reviseNarrative(projectId, feedback, skillId || undefined)).then(
                        (p) => p && setNarrativeProposal(p)
                      )
                    }
                    onApply={() =>
                      void run(() => applyNarrative(projectId, narrativeProposal.id, true)).then(() => {
                        setNarrativeProposal(null);
                        return refresh();
                      })
                    }
                    onDiscard={() => setNarrativeProposal(null)}
                  />
                )}
                {status?.narrativeConfirmed && !narrativeProposal && status.confirmedNarrativePreview != null ? (
                  <NarrativeProposalPreview domain={status.confirmedNarrativePreview} />
                ) : null}
              </>
            )}
          </div>
        )}

        {activeStage === "ready" && (
          <div className="mf-card mf-card-pad">
            <h3 className="mf-section-title" style={{ marginTop: 0 }}>
              {locked ? "继续创作" : "可以开始写作"}
            </h3>
            {locked ? (
              <>
                <p>
                  已定稿 <strong>{status?.acceptedChapterCount ?? 0}</strong> 章，建议从第{" "}
                  <strong>{resume}</strong> 章继续。
                </p>
                <Link
                  to={`/projects/${encodeURIComponent(projectId)}/chapters/${resume}/workspace`}
                  className="mf-btn mf-btn-primary"
                >
                  进入第 {resume} 章写作台
                </Link>
                <Link to={`/projects/${encodeURIComponent(projectId)}`} className="mf-btn mf-btn-secondary" style={{ marginLeft: 8 }}>
                  返回项目主页
                </Link>
              </>
            ) : (
              <>
                <p>题材、故事契约与故事结构均已确认。</p>
                <Link
                  to={`/projects/${encodeURIComponent(projectId)}/chapters/${resume}/workspace`}
                  className="mf-btn mf-btn-primary"
                >
                  进入第 {resume} 章写作台
                </Link>
                <Link to={`/projects/${encodeURIComponent(projectId)}`} className="mf-btn mf-btn-secondary" style={{ marginLeft: 8 }}>
                  返回项目主页
                </Link>
              </>
            )}
          </div>
        )}

        {!locked && <CopilotChatPanel projectId={projectId} scene="setup_coach" title="向导参谋" />}
      </div>
    </div>
  );
}

function ProposalPanel(props: {
  title: string;
  reply: string | null;
  draftPreview: React.ReactNode;
  confirmedPreview?: React.ReactNode | null;
  feedback: string;
  onFeedback: (s: string) => void;
  busy: boolean;
  onRevise: () => void;
  onApply: () => void;
  onDiscard: () => void;
  extra?: React.ReactNode;
}) {
  return (
    <div className="mf-panel" style={{ marginTop: 16 }}>
      <h4 style={{ margin: "0 0 8px" }}>{props.title}</h4>
      {props.reply && <p className="mf-muted mf-text-sm">{props.reply}</p>}
      <div style={{ display: "grid", gap: 12, gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))" }}>
        {props.confirmedPreview != null && (
          <div>
            <p style={{ fontWeight: 600, fontSize: 12, margin: "0 0 4px" }}>当前已确认</p>
            {props.confirmedPreview}
          </div>
        )}
        <div>
          <p style={{ fontWeight: 600, fontSize: 12, margin: "0 0 4px" }}>新草案</p>
          {props.draftPreview}
        </div>
      </div>
      {props.extra}
      <textarea
        className="mf-textarea"
        value={props.feedback}
        onChange={(e) => props.onFeedback(e.target.value)}
        placeholder="修改意见（可选）"
        rows={2}
        style={{ width: "100%", marginTop: 10 }}
      />
      <div style={{ display: "flex", flexWrap: "wrap", gap: 8, marginTop: 10 }}>
        <button type="button" className="mf-btn mf-btn-primary" disabled={props.busy} onClick={props.onApply}>
          确认采纳
        </button>
        <button type="button" className="mf-btn" disabled={props.busy || !props.feedback.trim()} onClick={props.onRevise}>
          请 AI 修订
        </button>
        <button type="button" className="mf-btn mf-btn-secondary" disabled={props.busy} onClick={props.onDiscard}>
          放弃草案
        </button>
      </div>
    </div>
  );
}
