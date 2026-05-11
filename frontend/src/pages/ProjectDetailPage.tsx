import type { CSSProperties } from "react";
import { FormEvent, useCallback, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import {
  deleteGenreContract,
  getGenreContract,
  parseGenreForm,
  postGenreInterview,
  postGenreRecommendFromStoryStream,
  postGenreRecommendStream,
  putGenreContract,
  putGenreSelection,
  type GenreDecisionContract,
  type GenreInterviewChatTurn,
  type GenreInterviewResponse,
  type GenreRecommendResponse,
} from "../api/genre";
import {
  deleteProject,
  getProjectDetail,
  getProjectWorkspace,
  setFanSeriesPreset,
  type ProjectDetail,
  type ProjectWorkspace,
} from "../api/projects";
import { listWriterSkills, type WriterSkillOption } from "../api/writerSkills";

function asRecord(v: unknown): Record<string, unknown> | null {
  return v != null && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : null;
}

function strField(obj: Record<string, unknown>, ...keys: string[]): string {
  for (const k of keys) {
    const v = obj[k];
    if (typeof v === "string") return v;
  }
  return "";
}

function subTagsToComma(sd: Record<string, unknown>): string {
  const st = sd.subTags ?? sd.sub_tags;
  if (Array.isArray(st)) return st.filter((x): x is string => typeof x === "string").join(", ");
  return "";
}

function riskNotesFromRaw(raw: Record<string, unknown>): string[] {
  const r = raw.riskNotes ?? raw.risk_notes;
  if (!Array.isArray(r)) return [];
  return r.filter((x): x is string => typeof x === "string");
}

function candidatesFromRaw(raw: Record<string, unknown>): Record<string, unknown>[] {
  const c = raw.candidateRankings ?? raw.candidate_rankings;
  if (!Array.isArray(c)) return [];
  return c.map((item) => asRecord(item)).filter((x): x is Record<string, unknown> => x != null);
}

function hubCardStyle(): CSSProperties {
  return {
    display: "flex",
    flexDirection: "column",
    gap: 6,
    padding: "14px 16px",
    borderRadius: 10,
    border: "1px solid #e5e7eb",
    background: "#fff",
    textDecoration: "none",
    color: "#0f172a",
    boxShadow: "0 1px 2px rgba(0,0,0,0.05)",
    fontSize: 14,
  };
}

function hubCardSub(): CSSProperties {
  return { fontSize: 12, color: "#64748b", fontWeight: 400 };
}

export default function ProjectDetailPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const navigate = useNavigate();
  const [data, setData] = useState<ProjectDetail | null>(null);
  const [workspace, setWorkspace] = useState<ProjectWorkspace | null>(null);
  const [err, setErr] = useState<string | null>(null);
  const [deleteBusy, setDeleteBusy] = useState(false);
  const [deleteErr, setDeleteErr] = useState<string | null>(null);

  const [fanPresetDraft, setFanPresetDraft] = useState("");
  const [fanPresetBusy, setFanPresetBusy] = useState(false);
  const [fanPresetErr, setFanPresetErr] = useState<string | null>(null);
  const [writerSkills, setWriterSkills] = useState<WriterSkillOption[]>([]);
  const [writerSkillsErr, setWriterSkillsErr] = useState<string | null>(null);

  const [genreTab, setGenreTab] = useState<"preference" | "story_hook">("preference");

  const [genreBusy, setGenreBusy] = useState(false);
  const [genreErr, setGenreErr] = useState<string | null>(null);
  const [genreResult, setGenreResult] = useState<GenreRecommendResponse | null>(null);
  const [genreStreamLog, setGenreStreamLog] = useState("");
  const [targetPlatform, setTargetPlatform] = useState("番茄");
  const [genderChannel, setGenderChannel] = useState("男频");
  const [preferredGenresRaw, setPreferredGenresRaw] = useState("");
  const [avoidRaw, setAvoidRaw] = useState("强虐, 纯后宫");
  const [writingStrengthRaw, setWritingStrengthRaw] = useState("爽点, 反转");
  const [riskPreference, setRiskPreference] = useState("medium");

  const [hookBusy, setHookBusy] = useState(false);
  const [hookErr, setHookErr] = useState<string | null>(null);
  const [hookStreamLog, setHookStreamLog] = useState("");
  const [storyHookText, setStoryHookText] = useState("");
  const [hookPlatform, setHookPlatform] = useState("番茄");
  const [hookChannel, setHookChannel] = useState("男频");
  const [hookRisk, setHookRisk] = useState("medium");

  const [pathBSub, setPathBSub] = useState<"interview" | "quick">("interview");
  const [interviewMessages, setInterviewMessages] = useState<GenreInterviewChatTurn[]>([]);
  const [interviewInput, setInterviewInput] = useState("");
  const [interviewBusy, setInterviewBusy] = useState(false);
  const [interviewErr, setInterviewErr] = useState<string | null>(null);
  const [interviewDone, setInterviewDone] = useState<GenreInterviewResponse | null>(null);

  const [pickGenreId, setPickGenreId] = useState<string | null>(null);
  const [pickBusy, setPickBusy] = useState(false);
  const [pickErr, setPickErr] = useState<string | null>(null);

  const [genreModalId, setGenreModalId] = useState<string | null>(null);
  const [genreModalBusy, setGenreModalBusy] = useState(false);
  const [genreModalSaveBusy, setGenreModalSaveBusy] = useState(false);
  const [genreModalErr, setGenreModalErr] = useState<string | null>(null);
  const [genreModalRaw, setGenreModalRaw] = useState<Record<string, unknown> | null>(null);
  const [genreModalHook, setGenreModalHook] = useState("");
  const [genreModalGenre, setGenreModalGenre] = useState("");
  const [genreModalChannel, setGenreModalChannel] = useState("");
  const [genreModalReason, setGenreModalReason] = useState("");
  const [genreModalSubTags, setGenreModalSubTags] = useState("");
  const [genreModalCandidates, setGenreModalCandidates] = useState<Record<string, unknown>[]>([]);
  const [genreModalRiskText, setGenreModalRiskText] = useState("");

  const refreshWorkspace = useCallback(async () => {
    if (!projectId) return;
    try {
      const w = await getProjectWorkspace(projectId);
      setWorkspace(w);
      setPickGenreId(w.selectedGenreContractId);
    } catch {
      /* ignore */
    }
  }, [projectId]);

  useEffect(() => {
    if (!projectId) {
      setErr("缺少项目 ID");
      return;
    }
    setErr(null);
    getProjectDetail(projectId)
      .then(setData)
      .catch((e: Error) => setErr(e.message));
    refreshWorkspace();
  }, [projectId, refreshWorkspace]);

  useEffect(() => {
    if (!data?.project) return;
    setFanPresetDraft(data.project.fanSeriesPreset ?? "");
  }, [data?.project?.id, data?.project?.fanSeriesPreset]);

  useEffect(() => {
    if (!projectId) return;
    listWriterSkills()
      .then((r) => {
        setWriterSkills(r.skills);
        setWriterSkillsErr(null);
      })
      .catch((e: Error) => {
        setWriterSkills([]);
        setWriterSkillsErr(e.message);
      });
  }, [projectId]);

  useEffect(() => {
    setInterviewMessages([]);
    setInterviewInput("");
    setInterviewErr(null);
    setInterviewDone(null);
    setPathBSub("interview");
  }, [projectId]);

  const onSaveFanPreset = useCallback(async () => {
    if (!projectId) return;
    setFanPresetErr(null);
    setFanPresetBusy(true);
    try {
      const updated = await setFanSeriesPreset(projectId, fanPresetDraft || null);
      setData((prev) => (prev ? { ...prev, project: updated } : prev));
    } catch (ex) {
      setFanPresetErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setFanPresetBusy(false);
    }
  }, [projectId, fanPresetDraft]);

  async function onDeleteProject() {
    if (!projectId) return;
    if (
      !window.confirm(
        "确定删除整个作品？所有章节、题材方案、初始化记录与本地导出文件都会删除，且不可恢复。"
      )
    ) {
      return;
    }
    setDeleteErr(null);
    setDeleteBusy(true);
    try {
      await deleteProject(projectId);
      navigate("/");
    } catch (ex) {
      setDeleteErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setDeleteBusy(false);
    }
  }

  if (!projectId) {
    return <p style={{ color: "crimson" }}>缺少项目 ID</p>;
  }

  if (err) {
    return <p style={{ color: "crimson" }}>加载失败：{err}</p>;
  }

  if (!data) {
    return <p>加载中…</p>;
  }

  const { project, writerEngine } = data;
  const writerConnected = writerEngine.health.ok && writerEngine.test.ok;

  async function onGenreSubmit(e: FormEvent) {
    e.preventDefault();
    if (!projectId) return;
    setGenreErr(null);
    setGenreBusy(true);
    setGenreStreamLog("");
    try {
      const body = parseGenreForm({
        targetPlatform,
        genderChannel,
        preferredGenresRaw,
        avoidRaw,
        writingStrengthRaw,
        riskPreference,
      });
      await postGenreRecommendStream(projectId, body, (eventName, payload) => {
        if (eventName === "llm_delta") {
          const node = typeof payload.node === "string" ? payload.node : "";
          const text = typeof payload.text === "string" ? payload.text : "";
          setGenreStreamLog((prev) => prev + `[${node}] ${text}`);
        }
        if (eventName === "persisted") {
          const contractId = typeof payload.contractId === "string" ? payload.contractId : "";
          const contract = payload.contract as GenreDecisionContract | undefined;
          if (contractId && contract) {
            setGenreResult({ contractId, contract });
          }
          void refreshWorkspace();
        }
        if (eventName === "error") {
          const msg = typeof payload.message === "string" ? payload.message : JSON.stringify(payload);
          setGenreErr(msg);
        }
      });
    } catch (ex) {
      setGenreErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setGenreBusy(false);
    }
  }

  async function onStoryHookSubmit(e: FormEvent) {
    e.preventDefault();
    if (!projectId) return;
    const hook = storyHookText.trim();
    if (!hook) {
      setHookErr("请填写一两句故事线或创意。");
      return;
    }
    setHookErr(null);
    setHookBusy(true);
    setHookStreamLog("");
    try {
      await postGenreRecommendFromStoryStream(
        projectId,
        {
          storyHook: hook,
          targetPlatform: hookPlatform.trim() || "番茄",
          genderChannel: hookChannel.trim() || "男频",
          riskPreference: hookRisk.trim() || "medium",
        },
        (eventName, payload) => {
          if (eventName === "llm_delta") {
            const node = typeof payload.node === "string" ? payload.node : "";
            const text = typeof payload.text === "string" ? payload.text : "";
            setHookStreamLog((prev) => prev + `[${node}] ${text}`);
          }
          if (eventName === "persisted") {
            const contractId = typeof payload.contractId === "string" ? payload.contractId : "";
            const contract = payload.contract as GenreDecisionContract | undefined;
            if (contractId && contract) {
              setGenreResult({ contractId, contract });
            }
            void refreshWorkspace();
          }
          if (eventName === "error") {
            const msg = typeof payload.message === "string" ? payload.message : JSON.stringify(payload);
            setHookErr(msg);
          }
        }
      );
    } catch (ex) {
      setHookErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setHookBusy(false);
    }
  }

  async function onInterviewSend(e: FormEvent) {
    e.preventDefault();
    if (!projectId) return;
    const text = interviewInput.trim();
    if (!text) return;
    setInterviewErr(null);
    const userTurn: GenreInterviewChatTurn = { role: "user", content: text };
    const history = [...interviewMessages, userTurn];
    setInterviewMessages(history);
    setInterviewInput("");
    setInterviewBusy(true);
    try {
      const res = await postGenreInterview(projectId, history);
      const assistantTurn: GenreInterviewChatTurn = {
        role: "assistant",
        content: res.replyToUser,
      };
      setInterviewMessages((prev) => [...prev, assistantTurn]);
      if (res.status === "complete") {
        setInterviewDone(res);
      }
    } catch (ex) {
      setInterviewErr(ex instanceof Error ? ex.message : String(ex));
      setInterviewMessages((prev) => prev.slice(0, -1));
    } finally {
      setInterviewBusy(false);
    }
  }

  async function onConfirmGenreSelection(e: FormEvent) {
    e.preventDefault();
    if (!projectId || !pickGenreId) {
      setPickErr("请先在列表中选择一份题材方案。");
      return;
    }
    setPickErr(null);
    setPickBusy(true);
    try {
      await putGenreSelection(projectId, pickGenreId);
      await refreshWorkspace();
    } catch (ex) {
      setPickErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setPickBusy(false);
    }
  }

  async function openGenreModal(contractId: string) {
    if (!projectId) return;
    setGenreModalId(contractId);
    setGenreModalBusy(true);
    setGenreModalErr(null);
    setGenreModalRaw(null);
    try {
      const d = await getGenreContract(projectId, contractId);
      const raw = asRecord(d.rawJson);
      if (!raw) throw new Error("rawJson 不是对象");
      const sd = asRecord(raw.selectedDirection) ?? asRecord(raw.selected_direction) ?? {};
      setGenreModalRaw(JSON.parse(JSON.stringify(raw)) as Record<string, unknown>);
      setGenreModalHook(strField(raw, "recommendedCoreHook", "recommended_core_hook"));
      setGenreModalGenre(strField(sd, "genre"));
      setGenreModalChannel(strField(sd, "channel"));
      setGenreModalReason(strField(sd, "reason"));
      setGenreModalSubTags(subTagsToComma(sd));
      setGenreModalCandidates(candidatesFromRaw(raw).map((c) => ({ ...c })));
      setGenreModalRiskText(riskNotesFromRaw(raw).join("\n"));
    } catch (ex) {
      setGenreModalErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setGenreModalBusy(false);
    }
  }

  function closeGenreModal() {
    setGenreModalId(null);
    setGenreModalBusy(false);
    setGenreModalSaveBusy(false);
    setGenreModalErr(null);
    setGenreModalRaw(null);
  }

  async function saveGenreModal() {
    if (!projectId || !genreModalId || !genreModalRaw) return;
    setGenreModalErr(null);
    const next = JSON.parse(JSON.stringify(genreModalRaw)) as Record<string, unknown>;
    const subTags = genreModalSubTags
      .split(/[,，]/)
      .map((s) => s.trim())
      .filter(Boolean);
    const sd: Record<string, unknown> = {
      ...(asRecord(next.selectedDirection) ?? asRecord(next.selected_direction) ?? {}),
    };
    sd.genre = genreModalGenre;
    sd.channel = genreModalChannel;
    sd.reason = genreModalReason;
    sd.subTags = subTags;
    next.selectedDirection = sd;
    next.selected_direction = sd;
    next.recommendedCoreHook = genreModalHook;
    next.recommended_core_hook = genreModalHook;
    next.candidateRankings = genreModalCandidates;
    next.candidate_rankings = genreModalCandidates;
    const rn = genreModalRiskText
      .split(/\r?\n/)
      .map((s) => s.trim())
      .filter(Boolean);
    next.riskNotes = rn;
    next.risk_notes = rn;
    setGenreModalSaveBusy(true);
    try {
      await putGenreContract(projectId, genreModalId, { rawJson: next });
      await refreshWorkspace();
      closeGenreModal();
    } catch (ex) {
      setGenreModalErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setGenreModalSaveBusy(false);
    }
  }

  async function onDeleteGenreContractRow(contractId: string) {
    if (!projectId) return;
    if (!window.confirm(`确定删除该题材方案？删除后不可恢复。\nID：${contractId}`)) return;
    try {
      await deleteGenreContract(projectId, contractId);
      if (pickGenreId === contractId) setPickGenreId(null);
      await refreshWorkspace();
    } catch (ex) {
      window.alert(ex instanceof Error ? ex.message : String(ex));
    }
  }

  const contract: GenreDecisionContract | undefined = genreResult?.contract;

  const sourceLabel = (s: string) => (s === "story_hook" ? "故事线" : "偏好");

  return (
    <section>
      <p style={{ marginBottom: 16 }}>
        <Link to="/" style={{ color: "#2563eb", textDecoration: "none" }}>
          ← 返回作品列表
        </Link>
      </p>
      <h1 style={{ fontSize: 24, marginBottom: 12 }}>{project.name}</h1>
      <p style={{ color: "#64748b", marginBottom: 20 }}>在此管理题材、世界观与章节写作。</p>

      <div
        style={{
          display: "grid",
          gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))",
          gap: 12,
          marginBottom: 24,
        }}
      >
        <Link
          to={`/projects/${encodeURIComponent(projectId)}/story/init`}
          style={hubCardStyle()}
        >
          <strong>① 题材与大纲</strong>
          <span style={hubCardSub()}>选定题材、初始化故事与章纲</span>
        </Link>
        <Link
          to={`/projects/${encodeURIComponent(projectId)}/chapters/1/workspace`}
          style={hubCardStyle()}
        >
          <strong>② 章节写作</strong>
          <span style={hubCardSub()}>从第 1 章开始生成与定稿</span>
        </Link>
        <Link to={`/projects/${encodeURIComponent(projectId)}/graph`} style={hubCardStyle()}>
          <strong>人物与世界观</strong>
          <span style={hubCardSub()}>已定稿章节整理出的人物与关系</span>
        </Link>
        <Link to={`/projects/${encodeURIComponent(projectId)}/roi`} style={hubCardStyle()}>
          <strong>用量统计</strong>
          <span style={hubCardSub()}>各章写作消耗的估算用量</span>
        </Link>
        <Link to={`/projects/${encodeURIComponent(projectId)}/governance`} style={hubCardStyle()}>
          <strong>写作治理</strong>
          <span style={hubCardSub()}>作者意图、硬约束、风格与全书替换</span>
        </Link>
        <a
          href={`/api/projects/${encodeURIComponent(projectId)}/export/accepted-book.md`}
          target="_blank"
          rel="noreferrer"
          style={hubCardStyle()}
        >
          <strong>导出全书</strong>
          <span style={hubCardSub()}>合并已定稿章节为一份文稿</span>
        </a>
      </div>

      <div
        style={{
          marginBottom: 24,
          padding: 14,
          background: "#fff7ed",
          border: "1px solid #fed7aa",
          borderRadius: 10,
          fontSize: 14,
        }}
      >
        <strong style={{ color: "#9a3412" }}>危险操作</strong>
        <p style={{ margin: "8px 0 10px", color: "#57534e" }}>
          删除作品后无法恢复，请确认已备份重要内容。
        </p>
        {deleteErr && <p style={{ color: "crimson", marginBottom: 8 }}>{deleteErr}</p>}
        <button
          type="button"
          disabled={deleteBusy}
          onClick={() => void onDeleteProject()}
          style={{
            padding: "8px 14px",
            borderRadius: 8,
            border: "1px solid #dc2626",
            background: "#fff",
            color: "#b91c1c",
            cursor: deleteBusy ? "wait" : "pointer",
          }}
        >
          {deleteBusy ? "删除中…" : "删除整个作品"}
        </button>
      </div>

      <h2 style={{ marginTop: 24 }}>作品信息</h2>
      <dl style={{ display: "grid", gridTemplateColumns: "140px 1fr", gap: 8 }}>
        <dt>ID</dt>
        <dd style={{ margin: 0 }}>{project.id}</dd>
        <dt>名称</dt>
        <dd style={{ margin: 0 }}>{project.name}</dd>
        <dt>语言</dt>
        <dd style={{ margin: 0 }}>{project.language}</dd>
        <dt>目标章节</dt>
        <dd style={{ margin: 0 }}>{project.targetChapters}</dd>
        <dt>当前章节</dt>
        <dd style={{ margin: 0 }}>{project.currentChapter}</dd>
        <dt>状态</dt>
        <dd style={{ margin: 0 }}>{project.status}</dd>
        <dt>创建时间</dt>
        <dd style={{ margin: 0 }}>{project.createdAt}</dd>
        <dt>更新时间</dt>
        <dd style={{ margin: 0 }}>{project.updatedAt}</dd>
      </dl>

      <h3 style={{ marginTop: 20, marginBottom: 8 }}>写作 Skill（YAML）</h3>
      <p style={{ fontSize: 13, color: "#64748b", maxWidth: 560, marginTop: 0 }}>
        在 Writer 下的 <code style={{ fontSize: 12 }}>app/skills/library/</code>{" "}
        放置根目录 <code>*.yaml</code>，或<strong>子文件夹</strong>内 <code>skill.yaml</code>/
        <code>index.yaml</code>（详见该目录 README）；重启 Writer 后列表自动更新。
        保存后，<strong>故事初始化</strong>合并全书字段进契约，<strong>章节生成</strong>附带每章短约束。
        可选「无」表示不使用任何 Skill。改 Skill 不会自动更新已有快照。
      </p>
      {writerSkillsErr && (
        <p style={{ color: "#b45309", marginBottom: 8, fontSize: 13 }}>
          无法加载 Skill 列表（Writer 未启动或代理失败）：{writerSkillsErr}
        </p>
      )}
      {fanPresetErr && <p style={{ color: "crimson", marginBottom: 8 }}>{fanPresetErr}</p>}
      <div style={{ display: "flex", gap: 10, alignItems: "center", flexWrap: "wrap", marginBottom: 8 }}>
        <label style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <span style={{ fontSize: 14 }}>选用 Skill</span>
          <select
            value={fanPresetDraft}
            onChange={(e) => setFanPresetDraft(e.target.value)}
            style={{ minWidth: 260, padding: "6px 8px", borderRadius: 6 }}
          >
            <option value="">无（不使用）</option>
            {fanPresetDraft &&
              !writerSkills.some((s) => s.id === fanPresetDraft) && (
                <option value={fanPresetDraft}>
                  已保存但 library 中未找到：{fanPresetDraft}
                </option>
              )}
            {writerSkills.map((s) => (
              <option key={s.id} value={s.id}>
                {s.label} ({s.id})
              </option>
            ))}
          </select>
        </label>
        <button
          type="button"
          disabled={fanPresetBusy}
          onClick={() => void onSaveFanPreset()}
          style={{
            padding: "8px 14px",
            borderRadius: 8,
            border: "1px solid #cbd5e1",
            background: "#f8fafc",
            cursor: fanPresetBusy ? "wait" : "pointer",
          }}
        >
          {fanPresetBusy ? "保存中…" : "保存预设"}
        </button>
      </div>

      <details style={{ marginTop: 20, marginBottom: 8 }}>
        <summary style={{ cursor: "pointer", fontWeight: 600, color: "#475569" }}>
          写作服务连接状态（排障时可展开）
        </summary>
        <p style={{ marginTop: 10 }}>
          <strong>当前：</strong>
          {writerConnected ? (
            <span style={{ color: "#15803d" }}>正常</span>
          ) : (
            <span style={{ color: "crimson" }}>异常（生成可能失败）</span>
          )}
        </p>
        <pre
          style={{
            background: "#f6f6f6",
            padding: 12,
            borderRadius: 8,
            overflow: "auto",
            fontSize: 12,
          }}
        >
          {JSON.stringify(writerEngine, null, 2)}
        </pre>
      </details>

      <h2 style={{ marginTop: 32 }}>题材方案（路径 A 偏好 · 路径 B 互动共创）</h2>
      <p style={{ maxWidth: 640 }}>
        路径 A：填偏好表单后<strong>实时生成</strong>若干题材卡片。路径 B：默认用<strong>多轮采访</strong>把脑洞聊清楚；也可切到「一句话快速生成」。
        生成的题材都会在列表里，<strong>进入故事初始化前请先选定一份</strong>。
      </p>

      <div style={{ display: "flex", gap: 8, marginBottom: 12 }}>
        <button
          type="button"
          onClick={() => setGenreTab("preference")}
          disabled={genreTab === "preference"}
          style={{ fontWeight: genreTab === "preference" ? 700 : 400 }}
        >
          路径 A：偏好推荐
        </button>
        <button
          type="button"
          onClick={() => setGenreTab("story_hook")}
          disabled={genreTab === "story_hook"}
          style={{ fontWeight: genreTab === "story_hook" ? 700 : 400 }}
        >
          路径 B：互动共创（采访）
        </button>
      </div>

      {genreTab === "preference" ? (
        <>
          <form onSubmit={onGenreSubmit} style={{ display: "flex", flexDirection: "column", gap: 10, maxWidth: 520 }}>
            <label>
              目标平台
              <input
                value={targetPlatform}
                onChange={(e) => setTargetPlatform(e.target.value)}
                required
                style={{ display: "block", width: "100%", marginTop: 4 }}
              />
            </label>
            <label>
              频道
              <input
                value={genderChannel}
                onChange={(e) => setGenderChannel(e.target.value)}
                required
                style={{ display: "block", width: "100%", marginTop: 4 }}
              />
            </label>
            <label>
              偏好题材（逗号分隔，可空）
              <input
                value={preferredGenresRaw}
                onChange={(e) => setPreferredGenresRaw(e.target.value)}
                style={{ display: "block", width: "100%", marginTop: 4 }}
              />
            </label>
            <label>
              避雷（逗号分隔）
              <input
                value={avoidRaw}
                onChange={(e) => setAvoidRaw(e.target.value)}
                style={{ display: "block", width: "100%", marginTop: 4 }}
              />
            </label>
            <label>
              写法强项（逗号分隔）
              <input
                value={writingStrengthRaw}
                onChange={(e) => setWritingStrengthRaw(e.target.value)}
                style={{ display: "block", width: "100%", marginTop: 4 }}
              />
            </label>
            <label>
              风险承受
              <select
                value={riskPreference}
                onChange={(e) => setRiskPreference(e.target.value)}
                style={{ display: "block", width: "100%", marginTop: 4 }}
              >
                <option value="low">low</option>
                <option value="medium">medium</option>
                <option value="high">high</option>
              </select>
            </label>
            <button type="submit" disabled={genreBusy}>
              {genreBusy ? "生成中…" : "生成题材推荐"}
            </button>
          </form>
          {!writerConnected && (
            <p style={{ color: "crimson", marginTop: 8 }}>
              Writer 未连通时建议先排除 Writer 故障再生成（仍可强行在后端直接调 Python 调试）。
            </p>
          )}
          {genreStreamLog.length > 0 && (
            <pre
              style={{
                marginTop: 12,
                maxHeight: 160,
                overflow: "auto",
                background: "#f0f7ff",
                padding: 10,
                borderRadius: 8,
                fontSize: 11,
                whiteSpace: "pre-wrap",
                wordBreak: "break-word",
              }}
            >
              {genreStreamLog}
            </pre>
          )}
          {genreErr && <p style={{ color: "crimson", marginTop: 8 }}>{genreErr}</p>}
        </>
      ) : (
        <>
          <div style={{ display: "flex", gap: 8, marginBottom: 12 }}>
            <button
              type="button"
              onClick={() => setPathBSub("interview")}
              disabled={pathBSub === "interview"}
              style={{ fontWeight: pathBSub === "interview" ? 700 : 400 }}
            >
              互动采访（默认）
            </button>
            <button
              type="button"
              onClick={() => setPathBSub("quick")}
              disabled={pathBSub === "quick"}
              style={{ fontWeight: pathBSub === "quick" ? 700 : 400 }}
            >
              一句话快速生成（高级）
            </button>
          </div>

          {pathBSub === "interview" ? (
            <>
              <p style={{ maxWidth: 560, fontSize: 14, color: "#333" }}>
                与「资深网文编辑」多轮对话补全设定；编辑会持续追问，直到输出约 100 字确认版故事线。
                完成后会自动保存为本作品的一份<strong>种子设定草稿</strong>，可在列表中查看。
              </p>
              <div
                style={{
                  border: "1px solid #ddd",
                  borderRadius: 8,
                  padding: 12,
                  maxWidth: 560,
                  minHeight: 200,
                  maxHeight: 320,
                  overflow: "auto",
                  background: "#fafafa",
                  marginBottom: 10,
                }}
              >
                {interviewMessages.length === 0 ? (
                  <p style={{ color: "#888", margin: 0 }}>在下方输入你的脑洞或第一句话，开始采访。</p>
                ) : (
                  interviewMessages.map((m, i) => (
                    <div
                      key={i}
                      style={{
                        marginBottom: 10,
                        textAlign: m.role === "user" ? "right" : "left",
                      }}
                    >
                      <span
                        style={{
                          display: "inline-block",
                          padding: "8px 12px",
                          borderRadius: 8,
                          background: m.role === "user" ? "#d6e8ff" : "#fff",
                          border: "1px solid #e0e0e0",
                          whiteSpace: "pre-wrap",
                          textAlign: "left",
                          maxWidth: "92%",
                        }}
                      >
                        <strong>{m.role === "user" ? "你" : "编辑"}：</strong>
                        {m.content}
                      </span>
                    </div>
                  ))
                )}
              </div>
              <form onSubmit={onInterviewSend} style={{ display: "flex", flexDirection: "column", gap: 8, maxWidth: 560 }}>
                <textarea
                  value={interviewInput}
                  onChange={(e) => setInterviewInput(e.target.value)}
                  rows={3}
                  placeholder="说说你的想法、设定或困惑…"
                  disabled={interviewBusy || interviewDone?.status === "complete"}
                  style={{ width: "100%" }}
                />
                <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                  <button type="submit" disabled={interviewBusy || interviewDone?.status === "complete"}>
                    {interviewBusy ? "等待回复…" : "发送"}
                  </button>
                  <button
                    type="button"
                    onClick={() => {
                      setInterviewMessages([]);
                      setInterviewInput("");
                      setInterviewErr(null);
                      setInterviewDone(null);
                    }}
                  >
                    清空会话
                  </button>
                  {interviewDone?.finalSummary && (
                    <button
                      type="button"
                      onClick={() => {
                        setStoryHookText(interviewDone.finalSummary ?? "");
                        setPathBSub("quick");
                      }}
                    >
                      用确认摘要填「一句话」并切到快速生成
                    </button>
                  )}
                </div>
              </form>
              {interviewErr && <p style={{ color: "crimson", marginTop: 8 }}>{interviewErr}</p>}
              {interviewDone?.status === "complete" && interviewDone.finalSummary && (
                <div
                  style={{
                    marginTop: 16,
                    padding: 14,
                    borderRadius: 10,
                    border: "2px solid #6b8cff",
                    background: "#f4f7ff",
                    maxWidth: 560,
                  }}
                >
                  <h3 style={{ marginTop: 0 }}>采访完成 · 故事线确认（约百字）</h3>
                  <p style={{ whiteSpace: "pre-wrap", lineHeight: 1.5 }}>{interviewDone.finalSummary}</p>
                  <p style={{ fontSize: 13 }}>
                    <strong>已保存的种子草稿编号：</strong>
                    <code>{interviewDone.persistedNovelSeedContractId ?? "—"}</code>
                  </p>
                  {interviewDone.coreSettings && (
                    <details style={{ marginTop: 8 }}>
                      <summary>coreSettings（结构化要点）</summary>
                      <pre style={{ fontSize: 12, overflow: "auto", maxHeight: 180 }}>
                        {JSON.stringify(interviewDone.coreSettings, null, 2)}
                      </pre>
                    </details>
                  )}
                </div>
              )}
            </>
          ) : (
            <>
              <form onSubmit={onStoryHookSubmit} style={{ display: "flex", flexDirection: "column", gap: 10, maxWidth: 520 }}>
                <label>
                  一两句故事线 / 创意（必填）
                  <textarea
                    value={storyHookText}
                    onChange={(e) => setStoryHookText(e.target.value)}
                    required
                    rows={4}
                    placeholder="例如：穿越成破产负债的小房东，每一间房都连着诡异副本；只想还债逃生却被迫经营万界旅馆。"
                    style={{ display: "block", width: "100%", marginTop: 4 }}
                  />
                </label>
                <label>
                  目标平台（可改）
                  <input
                    value={hookPlatform}
                    onChange={(e) => setHookPlatform(e.target.value)}
                    style={{ display: "block", width: "100%", marginTop: 4 }}
                  />
                </label>
                <label>
                  频道（可改）
                  <input
                    value={hookChannel}
                    onChange={(e) => setHookChannel(e.target.value)}
                    style={{ display: "block", width: "100%", marginTop: 4 }}
                  />
                </label>
                <label>
                  风险承受
                  <select value={hookRisk} onChange={(e) => setHookRisk(e.target.value)} style={{ display: "block", width: "100%", marginTop: 4 }}>
                    <option value="low">low</option>
                    <option value="medium">medium</option>
                    <option value="high">high</option>
                  </select>
                </label>
                <button type="submit" disabled={hookBusy}>
                  {hookBusy ? "生成中…" : "按故事线生成题材方案"}
                </button>
              </form>
              {hookStreamLog.length > 0 && (
                <pre
                  style={{
                    marginTop: 12,
                    maxHeight: 160,
                    overflow: "auto",
                    background: "#f7f0ff",
                    padding: 10,
                    borderRadius: 8,
                    fontSize: 11,
                    whiteSpace: "pre-wrap",
                    wordBreak: "break-word",
                  }}
                >
                  {hookStreamLog}
                </pre>
              )}
              {hookErr && <p style={{ color: "crimson", marginTop: 8 }}>{hookErr}</p>}
            </>
          )}
        </>
      )}

      <h2 style={{ marginTop: 32 }}>已保存的题材方案（数据库）</h2>
      {workspace && (
        <p style={{ marginBottom: 8 }}>
          当前用于初始化小说的选定方案 ID：<code>{workspace.selectedGenreContractId ?? "（未选定）"}</code>
        </p>
      )}
      {workspace && workspace.genreContracts.length === 0 ? (
        <p>暂无记录；请先用上方任一路径生成。</p>
      ) : (
        <form onSubmit={onConfirmGenreSelection}>
          <table style={{ borderCollapse: "collapse", width: "100%", maxWidth: 960, fontSize: 14 }}>
            <thead>
              <tr>
                <th style={{ textAlign: "left", borderBottom: "1px solid #ccc", padding: 6 }}>选用</th>
                <th style={{ textAlign: "left", borderBottom: "1px solid #ccc", padding: 6 }}>来源</th>
                <th style={{ textAlign: "left", borderBottom: "1px solid #ccc", padding: 6 }}>主推题材</th>
                <th style={{ textAlign: "left", borderBottom: "1px solid #ccc", padding: 6 }}>故事线摘要</th>
                <th style={{ textAlign: "left", borderBottom: "1px solid #ccc", padding: 6 }}>时间</th>
                <th style={{ textAlign: "left", borderBottom: "1px solid #ccc", padding: 6 }}>操作</th>
              </tr>
            </thead>
            <tbody>
              {(workspace?.genreContracts ?? []).map((g) => (
                <tr key={g.id}>
                  <td style={{ padding: 6, borderBottom: "1px solid #eee" }}>
                    <input
                      type="radio"
                      name="pickGenre"
                      checked={pickGenreId === g.id}
                      onChange={() => setPickGenreId(g.id)}
                    />
                  </td>
                  <td style={{ padding: 6, borderBottom: "1px solid #eee" }}>{sourceLabel(g.source)}</td>
                  <td style={{ padding: 6, borderBottom: "1px solid #eee" }}>{g.primaryGenreLabel || "—"}</td>
                  <td style={{ padding: 6, borderBottom: "1px solid #eee", maxWidth: 220 }}>{g.storyHookPreview || "—"}</td>
                  <td style={{ padding: 6, borderBottom: "1px solid #eee", whiteSpace: "nowrap" }}>{g.createdAt}</td>
                  <td style={{ padding: 6, borderBottom: "1px solid #eee", whiteSpace: "nowrap" }}>
                    <button type="button" style={{ marginRight: 8 }} onClick={() => openGenreModal(g.id)}>
                      详情 / 编辑
                    </button>
                    <button type="button" onClick={() => onDeleteGenreContractRow(g.id)}>
                      删除
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <button type="submit" disabled={pickBusy || !pickGenreId} style={{ marginTop: 10 }}>
            {pickBusy ? "保存中…" : "确认：用所选方案进入初始化"}
          </button>
        </form>
      )}
      {pickErr && <p style={{ color: "crimson", marginTop: 8 }}>{pickErr}</p>}

      {genreModalId && (
        <div
          role="dialog"
          aria-modal="true"
          style={{
            position: "fixed",
            inset: 0,
            background: "rgba(0,0,0,0.45)",
            zIndex: 1000,
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            padding: 16,
          }}
          onClick={(e) => {
            if (e.target === e.currentTarget) closeGenreModal();
          }}
        >
          <div
            style={{
              background: "#fff",
              maxWidth: 640,
              width: "100%",
              maxHeight: "90vh",
              overflow: "auto",
              borderRadius: 10,
              padding: 20,
              boxShadow: "0 8px 32px rgba(0,0,0,0.2)",
            }}
            onClick={(e) => e.stopPropagation()}
          >
            <h3 style={{ marginTop: 0 }}>题材方案 · 详情与手动编辑</h3>
            <p style={{ fontSize: 13, color: "#444" }}>
              <code>{genreModalId}</code>
            </p>
            {genreModalBusy ? (
              <p>加载中…</p>
            ) : genreModalErr && !genreModalRaw ? (
              <p style={{ color: "crimson" }}>{genreModalErr}</p>
            ) : genreModalRaw ? (
              <>
                <h4 style={{ marginBottom: 8 }}>编辑（保存后写入数据库）</h4>
                <label style={{ display: "block", marginBottom: 12 }}>
                  <div style={{ fontWeight: 600, marginBottom: 4 }}>核心钩子 recommendedCoreHook</div>
                  <textarea
                    value={genreModalHook}
                    onChange={(e) => setGenreModalHook(e.target.value)}
                    rows={4}
                    style={{ width: "100%", boxSizing: "border-box", fontFamily: "inherit", fontSize: 14 }}
                  />
                </label>
                <label style={{ display: "block", marginBottom: 8 }}>
                  <div style={{ fontWeight: 600, marginBottom: 4 }}>主推题材 genre</div>
                  <input
                    value={genreModalGenre}
                    onChange={(e) => setGenreModalGenre(e.target.value)}
                    style={{ width: "100%", boxSizing: "border-box", padding: 6 }}
                  />
                </label>
                <label style={{ display: "block", marginBottom: 8 }}>
                  <div style={{ fontWeight: 600, marginBottom: 4 }}>频道 channel</div>
                  <input
                    value={genreModalChannel}
                    onChange={(e) => setGenreModalChannel(e.target.value)}
                    style={{ width: "100%", boxSizing: "border-box", padding: 6 }}
                  />
                </label>
                <label style={{ display: "block", marginBottom: 8 }}>
                  <div style={{ fontWeight: 600, marginBottom: 4 }}>推荐理由 reason</div>
                  <textarea
                    value={genreModalReason}
                    onChange={(e) => setGenreModalReason(e.target.value)}
                    rows={3}
                    style={{ width: "100%", boxSizing: "border-box", fontFamily: "inherit", fontSize: 14 }}
                  />
                </label>
                <label style={{ display: "block", marginBottom: 16 }}>
                  <div style={{ fontWeight: 600, marginBottom: 4 }}>标签 subTags（逗号分隔）</div>
                  <input
                    value={genreModalSubTags}
                    onChange={(e) => setGenreModalSubTags(e.target.value)}
                    style={{ width: "100%", boxSizing: "border-box", padding: 6 }}
                  />
                </label>

                <h4 style={{ marginBottom: 8 }}>全局风险（一行一条）</h4>
                <textarea
                  value={genreModalRiskText}
                  onChange={(e) => setGenreModalRiskText(e.target.value)}
                  rows={5}
                  placeholder="每行一条风险说明"
                  style={{ width: "100%", boxSizing: "border-box", fontFamily: "inherit", fontSize: 14 }}
                />

                <h4 style={{ marginTop: 16, marginBottom: 8 }}>候选方向 candidateRankings</h4>
                <p style={{ fontSize: 13, color: "#64748b", marginTop: 0 }}>
                  可增删改；保存后写入题材 JSON。
                </p>
                {genreModalCandidates.map((c, i) => (
                  <div
                    key={i}
                    style={{
                      marginBottom: 14,
                      padding: 10,
                      border: "1px solid #e5e7eb",
                      borderRadius: 8,
                      background: "#fafafa",
                    }}
                  >
                    <label style={{ display: "block", marginBottom: 6 }}>
                      <span style={{ fontWeight: 600 }}>题材标签 genre</span>
                      <input
                        value={String(c.genre ?? "")}
                        onChange={(e) => {
                          const next = [...genreModalCandidates];
                          next[i] = { ...next[i], genre: e.target.value };
                          setGenreModalCandidates(next);
                        }}
                        style={{ display: "block", width: "100%", marginTop: 4, padding: 6 }}
                      />
                    </label>
                    <label style={{ display: "block", marginBottom: 6 }}>
                      <span style={{ fontWeight: 600 }}>推荐理由</span>
                      <textarea
                        value={String(
                          c.recommendReason ?? c.recommend_reason ?? "",
                        )}
                        onChange={(e) => {
                          const next = [...genreModalCandidates];
                          next[i] = {
                            ...next[i],
                            recommendReason: e.target.value,
                            recommend_reason: e.target.value,
                          };
                          setGenreModalCandidates(next);
                        }}
                        rows={2}
                        style={{ display: "block", width: "100%", marginTop: 4, fontFamily: "inherit", fontSize: 13 }}
                      />
                    </label>
                    <label style={{ display: "block", marginBottom: 6 }}>
                      <span style={{ fontWeight: 600 }}>风险说明</span>
                      <textarea
                        value={String(c.riskNote ?? c.risk_note ?? "")}
                        onChange={(e) => {
                          const next = [...genreModalCandidates];
                          next[i] = {
                            ...next[i],
                            riskNote: e.target.value,
                            risk_note: e.target.value,
                          };
                          setGenreModalCandidates(next);
                        }}
                        rows={2}
                        style={{ display: "block", width: "100%", marginTop: 4, fontFamily: "inherit", fontSize: 13 }}
                      />
                    </label>
                    <button
                      type="button"
                      onClick={() =>
                        setGenreModalCandidates((prev) => prev.filter((_, j) => j !== i))
                      }
                      style={{ fontSize: 13, color: "#b91c1c" }}
                    >
                      删除本条候选
                    </button>
                  </div>
                ))}
                <button
                  type="button"
                  onClick={() =>
                    setGenreModalCandidates((prev) => [
                      ...prev,
                      {
                        genre: "",
                        recommendReason: "",
                        recommend_reason: "",
                        riskNote: "",
                        risk_note: "",
                        finalScore: 0,
                        tokenCostLevel: "medium",
                      },
                    ])
                  }
                  style={{ marginBottom: 12 }}
                >
                  + 添加候选
                </button>

                {genreModalErr && <p style={{ color: "crimson" }}>{genreModalErr}</p>}
                <div style={{ display: "flex", gap: 10, marginTop: 16 }}>
                  <button type="button" disabled={genreModalSaveBusy} onClick={() => saveGenreModal()}>
                    {genreModalSaveBusy ? "保存中…" : "保存"}
                  </button>
                  <button type="button" disabled={genreModalSaveBusy} onClick={() => closeGenreModal()}>
                    取消
                  </button>
                </div>
              </>
            ) : (
              <p style={{ color: "crimson" }}>{genreModalErr ?? "未知错误"}</p>
            )}
            {!genreModalBusy && !genreModalRaw && genreModalErr ? (
              <button type="button" style={{ marginTop: 12 }} onClick={() => closeGenreModal()}>
                关闭
              </button>
            ) : null}
          </div>
        </div>
      )}

      {contract && (
        <div style={{ marginTop: 24 }}>
          <p>
            <strong>最近一次流式完成 · contractId：</strong>
            {genreResult?.contractId}
          </p>
          <h3>主推方向 selectedDirection</h3>
          <p>
            <strong>{contract.selectedDirection.genre}</strong>（{contract.selectedDirection.channel}）
          </p>
          <p>{contract.selectedDirection.reason}</p>
          <p>
            <strong>标签：</strong>
            {contract.selectedDirection.subTags.join("、")}
          </p>

          <h3>核心钩子 recommendedCoreHook</h3>
          <p>{contract.recommendedCoreHook}</p>

          <h3>全局风险 riskNotes</h3>
          {contract.riskNotes.length === 0 ? (
            <p>（无）</p>
          ) : (
            <ul>
              {contract.riskNotes.map((r, i) => (
                <li key={i}>{r}</li>
              ))}
            </ul>
          )}

          <h3>三个候选 candidateRankings</h3>
          <p style={{ fontSize: 13, color: "#64748b" }}>
            编辑请在上方题材列表中点击对应方案打开弹窗修改。
          </p>
          <ol>
            {contract.candidateRankings.map((c, i) => (
              <li key={i} style={{ marginBottom: 16 }}>
                <strong>{c.genre}</strong> — finalScore {c.finalScore} / token {c.tokenCostLevel}
                <div style={{ marginTop: 6 }}>
                  <strong>推荐理由：</strong>
                  {c.recommendReason}
                </div>
                <div style={{ marginTop: 6 }}>
                  <strong>风险：</strong>
                  {c.riskNote}
                </div>
              </li>
            ))}
          </ol>
        </div>
      )}
    </section>
  );
}
