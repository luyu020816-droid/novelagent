import { FormEvent, useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getSelectedStoryBundle, putStoryGovernance } from "../api/story";
import { postEntityReplace } from "../api/projects";
import { postWriterIntentPreview, postWriterStyleAnalyze } from "../api/writerSkills";

function defaultNonNegotiablesJson(bundle: Awaited<ReturnType<typeof getSelectedStoryBundle>>): string {
  const raw = bundle?.nonNegotiables;
  if (raw == null) {
    return "[]";
  }
  try {
    return JSON.stringify(raw, null, 2);
  } catch {
    return "[]";
  }
}

export default function GovernancePage() {
  const { projectId } = useParams<{ projectId: string }>();
  const [intent, setIntent] = useState("");
  const [nnJson, setNnJson] = useState("[]");
  const [styleGuideDraft, setStyleGuideDraft] = useState("");
  const [sampleForStyle, setSampleForStyle] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [savedHint, setSavedHint] = useState<string | null>(null);

  const [replaceRows, setReplaceRows] = useState<{ from: string; to: string }[]>([{ from: "", to: "" }]);
  const [replaceBusy, setReplaceBusy] = useState(false);
  const [replaceErr, setReplaceErr] = useState<string | null>(null);

  const [intentMsg, setIntentMsg] = useState("");
  const [intentPreview, setIntentPreview] = useState<string | null>(null);
  const [intentBusy, setIntentBusy] = useState(false);

  const load = useCallback(async () => {
    if (!projectId) return;
    setErr(null);
    try {
      const bundle = await getSelectedStoryBundle(projectId);
      if (!bundle) {
        setErr("尚未加载初始化快照，请先在「题材与大纲」完成或选定一套快照。");
        return;
      }
      setIntent(bundle.authorIntent ?? "");
      setNnJson(defaultNonNegotiablesJson(bundle));
      const fp = bundle.storyContract && typeof bundle.storyContract === "object"
        ? (bundle.storyContract as Record<string, unknown>).styleFingerprint ??
          (bundle.storyContract as Record<string, unknown>).style_fingerprint
        : null;
      const guide =
        fp && typeof fp === "object"
          ? String((fp as Record<string, unknown>).styleGuideMd ?? (fp as Record<string, unknown>).style_guide_md ?? "")
          : "";
      setStyleGuideDraft(guide);
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : String(e));
    }
  }, [projectId]);

  useEffect(() => {
    void load();
  }, [load]);

  async function onSaveGovernance(e: FormEvent) {
    e.preventDefault();
    if (!projectId) return;
    let parsed: unknown = [];
    try {
      parsed = JSON.parse(nnJson || "[]") as unknown;
    } catch {
      setErr("「不可违背」须为合法 JSON（推荐数组，如 [\"主线不得洗白反派\"]）。");
      return;
    }
    setBusy(true);
    setErr(null);
    setSavedHint(null);
    try {
      await putStoryGovernance(projectId, {
        authorIntent: intent,
        nonNegotiables: parsed,
        styleGuideMd: styleGuideDraft.trim() || "",
      });
      setSavedHint("已保存。后续章节生成会携带上述治理与风格约束。");
      await load();
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setBusy(false);
    }
  }

  async function onAnalyzeStyle(e: FormEvent) {
    e.preventDefault();
    const t = sampleForStyle.trim();
    if (t.length < 20) {
      setErr("风格样本至少 20 字。");
      return;
    }
    setBusy(true);
    setErr(null);
    try {
      const r = await postWriterStyleAnalyze(t);
      setStyleGuideDraft(r.styleGuideMd);
      setSavedHint("已根据样本生成风格约束，可继续编辑后点「保存治理」。");
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setBusy(false);
    }
  }

  async function onIntentPreview(e: FormEvent) {
    e.preventDefault();
    if (!projectId) return;
    const m = intentMsg.trim();
    if (!m) return;
    setIntentBusy(true);
    setIntentPreview(null);
    setErr(null);
    try {
      const r = await postWriterIntentPreview(projectId, m);
      const lines = r.suggestedActions.map((a) => `• ${a.action}: ${a.detail}`);
      setIntentPreview(lines.join("\n"));
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setIntentBusy(false);
    }
  }

  async function onReplaceEntities(e: FormEvent) {
    e.preventDefault();
    if (!projectId) return;
    const reps = replaceRows
      .map((r) => ({ from: r.from.trim(), to: r.to.trim() }))
      .filter((r) => r.from.length > 0);
    if (reps.length === 0) {
      setReplaceErr("至少填写一行「原名 → 新名」。");
      return;
    }
    if (!window.confirm("将在全书已定稿/草稿正文与章纲 JSON 中替换字符串，且不会自动同步 Neo4j。确定继续？")) {
      return;
    }
    setReplaceBusy(true);
    setReplaceErr(null);
    try {
      await postEntityReplace(projectId, reps);
      setReplaceRows([{ from: "", to: "" }]);
      await load();
    } catch (ex) {
      setReplaceErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setReplaceBusy(false);
    }
  }

  if (!projectId) {
    return (
      <section className="mf-page">
        <p className="mf-alert mf-alert-error">缺少项目 ID</p>
      </section>
    );
  }

  return (
    <section className="mf-page mf-prose" style={{ maxWidth: 720 }}>
      <Link to={`/projects/${encodeURIComponent(projectId)}`} className="mf-back">
        ← 返回作品主页
      </Link>
      <h1 className="mf-page-title">写作治理</h1>
      <p className="mf-page-lede">
        设定长期写作意图、硬约束与文风指纹；章节管线会把它们并入 story_canon。另可使用全书替换修正专名拼写。
      </p>

      {err && (
        <p className="mf-alert mf-alert-error" role="alert">
          {err}
        </p>
      )}
      {savedHint && <p className="mf-alert mf-alert-success">{savedHint}</p>}

      <form onSubmit={onSaveGovernance} className="mf-card mf-card-pad" style={{ marginBottom: 20 }}>
        <h2 className="mf-subsection-title" style={{ marginTop: 0 }}>
          作者意图与硬约束
        </h2>
        <label className="mf-label">长期意图（面向全书）</label>
        <textarea
          className="mf-textarea"
          value={intent}
          onChange={(ev) => setIntent(ev.target.value)}
          rows={5}
          style={{ marginBottom: 14 }}
          placeholder="例如：基调轻松、主角成长弧以「责任取代复仇」为核心……"
        />
        <label className="mf-label">不可违背（JSON 数组，每项一条短句）</label>
        <textarea
          className="mf-textarea"
          value={nnJson}
          onChange={(ev) => setNnJson(ev.target.value)}
          rows={6}
          style={{ marginBottom: 14, fontFamily: "var(--mf-mono)", fontSize: 13 }}
        />
        <label className="mf-label">风格指纹（Markdown，可由下方样本自动生成）</label>
        <textarea
          className="mf-textarea"
          value={styleGuideDraft}
          onChange={(ev) => setStyleGuideDraft(ev.target.value)}
          rows={8}
          style={{ marginBottom: 12 }}
        />
        <button type="submit" disabled={busy} className="mf-btn mf-btn-primary">
          {busy ? "保存中…" : "保存治理"}
        </button>
      </form>

      <form onSubmit={onAnalyzeStyle} className="mf-card mf-card-pad" style={{ marginBottom: 20 }}>
        <h2 className="mf-subsection-title" style={{ marginTop: 0 }}>
          从样本提取风格
        </h2>
        <p className="mf-muted mf-text-sm" style={{ marginBottom: 8 }}>
          粘贴一段你认可的代表性正文（≥20 字）。生成结果会填入上方「风格指纹」，保存治理后生效。
        </p>
        <textarea
          className="mf-textarea"
          value={sampleForStyle}
          onChange={(ev) => setSampleForStyle(ev.target.value)}
          rows={5}
          style={{ marginBottom: 8 }}
        />
        <button type="submit" disabled={busy} className="mf-btn mf-btn-primary">
          分析风格并填入草稿
        </button>
      </form>

      <form onSubmit={onIntentPreview} className="mf-card mf-card-pad" style={{ marginBottom: 20 }}>
        <h2 className="mf-subsection-title" style={{ marginTop: 0 }}>
          指令预演（实验）
        </h2>
        <p className="mf-muted mf-text-sm" style={{ marginBottom: 8 }}>
          用自然语言描述你想做的事，后端会返回建议操作类型（不自动执行）。
        </p>
        <textarea
          className="mf-textarea"
          value={intentMsg}
          onChange={(ev) => setIntentMsg(ev.target.value)}
          rows={3}
          style={{ marginBottom: 8 }}
          placeholder="例如：把反派改名并检查全书一致性"
        />
        <button type="submit" disabled={intentBusy} className="mf-btn mf-btn-primary">
          {intentBusy ? "解析中…" : "生成建议"}
        </button>
        {intentPreview && (
          <pre className="mf-pre" style={{ marginTop: 12, whiteSpace: "pre-wrap", fontSize: 13 }}>
            {intentPreview}
          </pre>
        )}
      </form>

      <form onSubmit={onReplaceEntities} className="mf-card mf-card-pad mf-panel-warn" style={{ borderStyle: "solid" }}>
        <h2 className="mf-subsection-title" style={{ marginTop: 0 }}>
          全书专名替换
        </h2>
        <p className="mf-text-sm" style={{ color: "#92400e", marginBottom: 10 }}>
          越长字符串优先替换。仅修改数据库中的大纲与章节文本；世界观图谱需另行核对。
        </p>
        {replaceErr && (
          <p className="mf-alert mf-alert-error" style={{ marginBottom: 8 }}>
            {replaceErr}
          </p>
        )}
        {replaceRows.map((row, i) => (
          <div key={i} style={{ display: "flex", gap: 8, marginBottom: 8, flexWrap: "wrap", alignItems: "center" }}>
            <input
              type="text"
              className="mf-input"
              value={row.from}
              placeholder="原名"
              onChange={(ev) => {
                const next = [...replaceRows];
                next[i] = { ...next[i], from: ev.target.value };
                setReplaceRows(next);
              }}
              style={{ flex: "1 1 140px", minWidth: 120 }}
            />
            <span className="mf-muted">→</span>
            <input
              type="text"
              className="mf-input"
              value={row.to}
              placeholder="新名"
              onChange={(ev) => {
                const next = [...replaceRows];
                next[i] = { ...next[i], to: ev.target.value };
                setReplaceRows(next);
              }}
              style={{ flex: "1 1 140px", minWidth: 120 }}
            />
            <button
              type="button"
              className="mf-btn"
              onClick={() => setReplaceRows(replaceRows.filter((_, j) => j !== i))}
              disabled={replaceRows.length <= 1}
            >
              删行
            </button>
          </div>
        ))}
        <div style={{ display: "flex", flexWrap: "wrap", gap: 8, marginTop: 4 }}>
          <button type="button" className="mf-btn" onClick={() => setReplaceRows([...replaceRows, { from: "", to: "" }])}>
            加一行
          </button>
          <button type="submit" disabled={replaceBusy} className="mf-btn mf-btn-primary">
            {replaceBusy ? "替换中…" : "执行全书替换"}
          </button>
        </div>
      </form>
    </section>
  );
}
