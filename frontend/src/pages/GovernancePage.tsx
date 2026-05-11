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
    return <p style={{ color: "crimson" }}>缺少项目 ID</p>;
  }

  return (
    <section style={{ maxWidth: 720 }}>
      <p style={{ marginBottom: 12 }}>
        <Link to={`/projects/${encodeURIComponent(projectId)}`} style={{ color: "#2563eb", textDecoration: "none" }}>
          ← 返回作品主页
        </Link>
      </p>
      <h1 style={{ fontSize: 22, marginBottom: 8 }}>写作治理</h1>
      <p style={{ color: "#64748b", fontSize: 14, marginBottom: 20 }}>
        设定长期写作意图、硬约束与文风指纹；章节管线会把它们并入 story_canon。另可使用全书替换修正专名拼写。
      </p>

      {err && (
        <p style={{ color: "crimson", marginBottom: 12 }} role="alert">
          {err}
        </p>
      )}
      {savedHint && (
        <p style={{ color: "#14532d", marginBottom: 12, fontSize: 14 }}>
          {savedHint}
        </p>
      )}

      <form onSubmit={onSaveGovernance} style={{ marginBottom: 28 }}>
        <h2 style={{ fontSize: 17, marginBottom: 10 }}>作者意图与硬约束</h2>
        <label style={{ display: "block", fontSize: 14, marginBottom: 6 }}>
          长期意图（面向全书）
        </label>
        <textarea
          value={intent}
          onChange={(ev) => setIntent(ev.target.value)}
          rows={5}
          style={{ width: "100%", boxSizing: "border-box", marginBottom: 14 }}
          placeholder="例如：基调轻松、主角成长弧以「责任取代复仇」为核心……"
        />
        <label style={{ display: "block", fontSize: 14, marginBottom: 6 }}>
          不可违背（JSON 数组，每项一条短句）
        </label>
        <textarea
          value={nnJson}
          onChange={(ev) => setNnJson(ev.target.value)}
          rows={6}
          style={{
            width: "100%",
            boxSizing: "border-box",
            fontFamily: "ui-monospace, monospace",
            fontSize: 13,
            marginBottom: 14,
          }}
        />
        <label style={{ display: "block", fontSize: 14, marginBottom: 6 }}>
          风格指纹（Markdown，可由下方样本自动生成）
        </label>
        <textarea
          value={styleGuideDraft}
          onChange={(ev) => setStyleGuideDraft(ev.target.value)}
          rows={8}
          style={{ width: "100%", boxSizing: "border-box", marginBottom: 12 }}
        />
        <button type="submit" disabled={busy} style={{ padding: "8px 16px" }}>
          {busy ? "保存中…" : "保存治理"}
        </button>
      </form>

      <form onSubmit={onAnalyzeStyle} style={{ marginBottom: 28 }}>
        <h2 style={{ fontSize: 17, marginBottom: 10 }}>从样本提取风格</h2>
        <p style={{ fontSize: 13, color: "#64748b", marginBottom: 8 }}>
          粘贴一段你认可的代表性正文（≥20 字）。生成结果会填入上方「风格指纹」，保存治理后生效。
        </p>
        <textarea
          value={sampleForStyle}
          onChange={(ev) => setSampleForStyle(ev.target.value)}
          rows={5}
          style={{ width: "100%", boxSizing: "border-box", marginBottom: 8 }}
        />
        <button type="submit" disabled={busy} style={{ padding: "8px 16px" }}>
          分析风格并填入草稿
        </button>
      </form>

      <form onSubmit={onIntentPreview} style={{ marginBottom: 28 }}>
        <h2 style={{ fontSize: 17, marginBottom: 10 }}>指令预演（实验）</h2>
        <p style={{ fontSize: 13, color: "#64748b", marginBottom: 8 }}>
          用自然语言描述你想做的事，后端会返回建议操作类型（不自动执行）。
        </p>
        <textarea
          value={intentMsg}
          onChange={(ev) => setIntentMsg(ev.target.value)}
          rows={3}
          style={{ width: "100%", boxSizing: "border-box", marginBottom: 8 }}
          placeholder="例如：把反派改名并检查全书一致性"
        />
        <button type="submit" disabled={intentBusy} style={{ padding: "8px 16px" }}>
          {intentBusy ? "解析中…" : "生成建议"}
        </button>
        {intentPreview && (
          <pre
            style={{
              marginTop: 12,
              whiteSpace: "pre-wrap",
              background: "#f8fafc",
              padding: 12,
              borderRadius: 8,
              fontSize: 13,
            }}
          >
            {intentPreview}
          </pre>
        )}
      </form>

      <form onSubmit={onReplaceEntities}>
        <h2 style={{ fontSize: 17, marginBottom: 10 }}>全书专名替换</h2>
        <p style={{ fontSize: 13, color: "#92400e", marginBottom: 10 }}>
          越长字符串优先替换。仅修改数据库中的大纲与章节文本；世界观图谱需另行核对。
        </p>
        {replaceErr && <p style={{ color: "crimson", marginBottom: 8 }}>{replaceErr}</p>}
        {replaceRows.map((row, i) => (
          <div key={i} style={{ display: "flex", gap: 8, marginBottom: 8, flexWrap: "wrap" }}>
            <input
              type="text"
              value={row.from}
              placeholder="原名"
              onChange={(ev) => {
                const next = [...replaceRows];
                next[i] = { ...next[i], from: ev.target.value };
                setReplaceRows(next);
              }}
              style={{ flex: "1 1 140px", minWidth: 120 }}
            />
            <span style={{ alignSelf: "center" }}>→</span>
            <input
              type="text"
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
              onClick={() => setReplaceRows(replaceRows.filter((_, j) => j !== i))}
              disabled={replaceRows.length <= 1}
            >
              删行
            </button>
          </div>
        ))}
        <button
          type="button"
          onClick={() => setReplaceRows([...replaceRows, { from: "", to: "" }])}
          style={{ marginRight: 8, marginBottom: 8 }}
        >
          加一行
        </button>
        <button type="submit" disabled={replaceBusy} style={{ padding: "8px 16px" }}>
          {replaceBusy ? "替换中…" : "执行全书替换"}
        </button>
      </form>
    </section>
  );
}
