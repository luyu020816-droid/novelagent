import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import CopilotChatPanel from "../components/CopilotChatPanel";
import { Link, useParams } from "react-router-dom";
import ChapterContractList from "../components/ChapterContractList";
import { getProjectWorkspace, type ProjectWorkspace } from "../api/projects";
import { postChapterGenerateStream } from "../api/chapters";
import {
  getSelectedStoryBundle,
  postStoryInitStream,
  putFirstVolumeOutline,
  putStorySelection,
  type StoryInitResponse,
} from "../api/story";

type ProtagonistView = {
  name?: string;
  desire?: string;
  weakness?: string;
  secret?: string;
  growthArc?: string;
  goldenFinger?: string;
};

type StyleGuideView = {
  narrativeVoice?: string;
  pacing?: string;
  dialogueRatio?: string;
  tabooTopics?: string[];
};

function asRecord(v: unknown): Record<string, unknown> | undefined {
  return v && typeof v === "object" && !Array.isArray(v) ? (v as Record<string, unknown>) : undefined;
}

function stringList(v: unknown): string[] {
  return Array.isArray(v) ? v.filter((x): x is string => typeof x === "string") : [];
}

export default function StoryInitPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [result, setResult] = useState<StoryInitResponse | null>(null);
  const [initStreamLog, setInitStreamLog] = useState("");
  const [workspace, setWorkspace] = useState<ProjectWorkspace | null>(null);
  const [pickStoryId, setPickStoryId] = useState<string | null>(null);
  const [pickBusy, setPickBusy] = useState(false);
  const [pickErr, setPickErr] = useState<string | null>(null);
  const [reloadBusy, setReloadBusy] = useState(false);

  const [outlineDraft, setOutlineDraft] = useState("");
  const [outlineSaveBusy, setOutlineSaveBusy] = useState(false);
  const [outlineErr, setOutlineErr] = useState<string | null>(null);

  const [chapterGenNo, setChapterGenNo] = useState(1);
  const [chapterGenBusy, setChapterGenBusy] = useState(false);
  const [chapterGenErr, setChapterGenErr] = useState<string | null>(null);
  const [chapterGenLog, setChapterGenLog] = useState("");
  const [chapterGenFinal, setChapterGenFinal] = useState<Record<string, unknown> | null>(null);
  const [wizardNotes, setWizardNotes] = useState("");

  const reloadFromServer = useCallback(async () => {
    if (!projectId) return;
    setReloadBusy(true);
    try {
      const [w, bundle] = await Promise.all([getProjectWorkspace(projectId), getSelectedStoryBundle(projectId)]);
      setWorkspace(w);
      setPickStoryId(w.selectedStoryContractId);
      if (bundle) {
        setResult(bundle);
        setOutlineDraft(bundle.firstVolumeOutline ?? "");
      }
    } catch (e) {
      setErr(e instanceof Error ? e.message : String(e));
    } finally {
      setReloadBusy(false);
    }
  }, [projectId]);

  useEffect(() => {
    if (!projectId) return;
    void reloadFromServer();
  }, [projectId, reloadFromServer]);

  useEffect(() => {
    if (result?.firstVolumeOutline !== undefined) {
      setOutlineDraft(result.firstVolumeOutline ?? "");
    }
  }, [result?.storyContractId, result?.firstVolumeOutline]);

  const outlineCopilotContext = useMemo(() => {
    const parts = [outlineDraft];
    const pos = result?.storyContract
      ? asRecord((result.storyContract as Record<string, unknown>).positioning)
      : undefined;
    if (pos?.genre) parts.push(`体裁：${String(pos.genre)}`);
    const ch = pos ? (pos.coreHook ?? pos.core_hook) : undefined;
    if (ch) parts.push(`核心钩子：${String(ch)}`);
    return parts.filter(Boolean).join("\n\n");
  }, [outlineDraft, result?.storyContract]);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!projectId) return;
    setErr(null);
    setBusy(true);
    setInitStreamLog("");
    setResult(null);
    try {
      await postStoryInitStream(
        projectId,
        (eventName, payload) => {
          if (eventName === "llm_delta") {
            const node = typeof payload.node === "string" ? payload.node : "";
            const text = typeof payload.text === "string" ? payload.text : "";
            setInitStreamLog((prev) => prev + `[${node}] ${text}`);
          }
          if (eventName === "artifact") {
            const kind = payload.kind;
            const data = payload.data as Record<string, unknown> | undefined;
            if (kind === "InitNovelBundle" && data) {
              setResult({
                novelSeedContractId: "（persist 前）",
                storyContractId: "（persist 前）",
                novelSeed: (data.novelSeed as Record<string, unknown>) ?? {},
                storyContract: (data.storyContract as Record<string, unknown>) ?? {},
                firstVolumeOutline: String(data.firstVolumeOutline ?? ""),
                chapterContracts: Array.isArray(data.chapterContracts) ? data.chapterContracts : [],
              });
            }
          }
          if (eventName === "persisted") {
            const ns = typeof payload.novelSeedContractId === "string" ? payload.novelSeedContractId : "";
            const st = typeof payload.storyContractId === "string" ? payload.storyContractId : "";
            setResult((prev) =>
              prev
                ? { ...prev, novelSeedContractId: ns || prev.novelSeedContractId, storyContractId: st || prev.storyContractId }
                : prev
            );
            void reloadFromServer();
          }
          if (eventName === "error") {
            const msg = typeof payload.message === "string" ? payload.message : JSON.stringify(payload);
            setErr(msg);
          }
        },
        { wizardNotes: wizardNotes.trim() || undefined }
      );
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setBusy(false);
    }
  }

  async function onChapterGenerate(e: FormEvent) {
    e.preventDefault();
    if (!projectId) return;
    setChapterGenErr(null);
    setChapterGenLog("");
    setChapterGenFinal(null);
    setChapterGenBusy(true);
    try {
      await postChapterGenerateStream(projectId, chapterGenNo, (eventName, payload) => {
        if (eventName === "llm_delta") {
          const node = typeof payload.node === "string" ? payload.node : "";
          const text = typeof payload.text === "string" ? payload.text : "";
          setChapterGenLog((prev) => prev + `[${node}] ${text}`);
        }
        if (eventName === "artifact" && payload.kind === "rolling_memory_meta") {
          const data = payload.data as Record<string, unknown> | undefined;
          const ch = data?.historySummaryChapters;
          if (Array.isArray(ch) && ch.length > 0) {
            setChapterGenLog((prev) => prev + `\n已载入前几章摘要（章节号 ${ch.join("、")}）\n`);
          }
        }
        if (eventName === "artifact" && payload.kind === "chapter_version_pending") {
          const data = payload.data as Record<string, unknown> | undefined;
          const vid = data?.versionId;
          setChapterGenLog((prev) => prev + `\n已写入待审核草稿（版本 ${String(vid ?? "—")}）\n`);
        }
        if (eventName === "artifact" && payload.kind === "chapter_generation_final") {
          const data = payload.data as Record<string, unknown> | undefined;
          if (data) setChapterGenFinal(data);
        }
        if (eventName === "error") {
          const msg = typeof payload.message === "string" ? payload.message : JSON.stringify(payload);
          setChapterGenErr(msg);
        }
      });
    } catch (ex) {
      setChapterGenErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setChapterGenBusy(false);
    }
  }

  async function onConfirmStorySelection(e: FormEvent) {
    e.preventDefault();
    if (!projectId || !pickStoryId) {
      setPickErr("请选择一个已保存的初始化快照。");
      return;
    }
    setPickErr(null);
    setPickBusy(true);
    try {
      await putStorySelection(projectId, pickStoryId);
      await reloadFromServer();
    } catch (ex) {
      setPickErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setPickBusy(false);
    }
  }

  if (!projectId) {
    return <p style={{ color: "crimson" }}>缺少项目 ID</p>;
  }

  const sc = result?.storyContract;
  const positioning = sc ? asRecord(sc.positioning) : undefined;
  const protagonist = sc ? (asRecord(sc.protagonist) as ProtagonistView | undefined) : undefined;
  const styleGuide = sc ? (asRecord(sc.styleGuide) as StyleGuideView | undefined) : undefined;

  return (
    <section>
      <h1>初始化小说（Story Contract）</h1>
      <p>
        <Link to={`/projects/${encodeURIComponent(projectId)}`}>← 返回项目详情</Link>
      </p>
      <p>
        依赖：在项目详情页<strong>选定一份题材方案</strong>。初始化结果会<strong>写入数据库</strong>；刷新本页后可通过下方快照列表恢复查看。
      </p>

      <h2 style={{ marginTop: 20 }}>已保存的初始化快照</h2>
      <p style={{ marginBottom: 8 }}>
        <button type="button" onClick={() => void reloadFromServer()} disabled={reloadBusy}>
          {reloadBusy ? "刷新中…" : "从数据库刷新"}
        </button>
        {workspace && (
          <span style={{ marginLeft: 12 }}>
            当前查看：<code>{workspace.selectedStoryContractId ?? "（未选定）"}</code>
          </span>
        )}
      </p>
      {workspace && workspace.storyInits.length === 0 ? (
        <p>暂无快照；运行一次流水线后即会出现在此列表。</p>
      ) : (
        <form onSubmit={onConfirmStorySelection}>
          <table style={{ borderCollapse: "collapse", width: "100%", maxWidth: 640, fontSize: 14 }}>
            <thead>
              <tr>
                <th style={{ textAlign: "left", borderBottom: "1px solid #ccc", padding: 6 }}>查看</th>
                <th style={{ textAlign: "left", borderBottom: "1px solid #ccc", padding: 6 }}>storyContractId</th>
                <th style={{ textAlign: "left", borderBottom: "1px solid #ccc", padding: 6 }}>novelSeedContractId</th>
                <th style={{ textAlign: "left", borderBottom: "1px solid #ccc", padding: 6 }}>创建时间</th>
              </tr>
            </thead>
            <tbody>
              {(workspace?.storyInits ?? []).map((s) => (
                <tr key={s.storyContractId}>
                  <td style={{ padding: 6, borderBottom: "1px solid #eee" }}>
                    <input
                      type="radio"
                      name="pickStory"
                      checked={pickStoryId === s.storyContractId}
                      onChange={() => setPickStoryId(s.storyContractId)}
                    />
                  </td>
                  <td style={{ padding: 6, borderBottom: "1px solid #eee", wordBreak: "break-all" }}>{s.storyContractId}</td>
                  <td style={{ padding: 6, borderBottom: "1px solid #eee", wordBreak: "break-all" }}>{s.novelSeedContractId ?? "—"}</td>
                  <td style={{ padding: 6, borderBottom: "1px solid #eee", whiteSpace: "nowrap" }}>{s.createdAt}</td>
                </tr>
              ))}
            </tbody>
          </table>
          <button type="submit" disabled={pickBusy || !pickStoryId} style={{ marginTop: 10 }}>
            {pickBusy ? "切换中…" : "加载所选快照到下方预览"}
          </button>
        </form>
      )}
      {pickErr && <p style={{ color: "crimson", marginTop: 8 }}>{pickErr}</p>}

      <h2 style={{ marginTop: 28 }}>开机向导（可选）</h2>
      <p style={{ fontSize: 14, color: "#475569", maxWidth: 720 }}>
        正式跑流水线前，先写下背景时代、口吻或禁区；会与题材决策合并，减轻后续设定「突然出现」的违和感。也可先与参谋对话打磨，再把定稿放进文本框。
      </p>
      <textarea
        value={wizardNotes}
        onChange={(e) => setWizardNotes(e.target.value)}
        rows={6}
        placeholder="例如：民国晚期小城；叙事克制；主角最怕亏欠人情；不要系统文套路……"
        style={{
          width: "100%",
          boxSizing: "border-box",
          fontFamily: "inherit",
          fontSize: 14,
          marginTop: 10,
        }}
      />
      <CopilotChatPanel projectId={projectId} scene="init_wizard" title="向导参谋" contextBlob={wizardNotes} />

      <form onSubmit={onSubmit} style={{ marginTop: 24 }}>
        <button type="submit" disabled={busy}>
          {busy ? "生成中…" : "运行新的初始化流水线"}
        </button>
      </form>

      <p style={{ marginTop: 8, fontSize: 13, color: "#444", maxWidth: 640 }}>
        初始化会依次调用策划、人物、世界观、卷纲与初审等环节；进度流式展示，页面需保持打开。详细步骤见下方日志。
      </p>

      {initStreamLog.length > 0 && (
        <pre
          style={{
            marginTop: 12,
            maxHeight: 200,
            overflow: "auto",
            background: "#f0f7ff",
            padding: 10,
            borderRadius: 8,
            fontSize: 11,
            whiteSpace: "pre-wrap",
            wordBreak: "break-word",
          }}
        >
          {initStreamLog}
        </pre>
      )}
      {err && (
        <p style={{ color: "crimson", marginTop: 12 }}>
          {err}
        </p>
      )}

      {result && sc && (
        <div style={{ marginTop: 24 }}>
          <p style={{ fontSize: 13, color: "#64748b" }}>
            快照编号（内部）：种子 {result.novelSeedContractId} · 故事 {result.storyContractId}
          </p>

          <h2 style={{ marginTop: 24 }}>核心卖点 / 定位（positioning）</h2>
          {positioning && (
            <ul>
              <li>
                <strong>体裁 genre：</strong>
                {String(positioning.genre ?? "")}
              </li>
              <li>
                <strong>目标读者：</strong>
                {String(positioning.targetReader ?? "")}
              </li>
              <li>
                <strong>核心钩子 coreHook：</strong>
                {String(positioning.coreHook ?? "")}
              </li>
              <li>
                <strong>基调 tone：</strong>
                {String(positioning.tone ?? "")}
              </li>
              <li>
                <strong>标题候选：</strong>
                {stringList(positioning.titleCandidates).join(" / ") || "（无）"}
              </li>
            </ul>
          )}

          <h2 style={{ marginTop: 24 }}>主角设定（protagonist）</h2>
          {protagonist && (
            <dl style={{ display: "grid", gridTemplateColumns: "160px 1fr", gap: 8 }}>
              <dt>姓名</dt>
              <dd style={{ margin: 0 }}>{protagonist.name ?? ""}</dd>
              <dt>欲望</dt>
              <dd style={{ margin: 0 }}>{protagonist.desire ?? ""}</dd>
              <dt>弱点</dt>
              <dd style={{ margin: 0 }}>{protagonist.weakness ?? ""}</dd>
              <dt>秘密</dt>
              <dd style={{ margin: 0 }}>{protagonist.secret ?? ""}</dd>
              <dt>成长弧</dt>
              <dd style={{ margin: 0 }}>{protagonist.growthArc ?? ""}</dd>
              <dt>优势 / 特殊处境（非系统文可写「无」）</dt>
              <dd style={{ margin: 0 }}>{protagonist.goldenFinger ?? ""}</dd>
            </dl>
          )}

          <h2 style={{ marginTop: 24 }}>世界规则（worldRules）</h2>
          {stringList(sc.worldRules).length === 0 ? (
            <p>（无）</p>
          ) : (
            <ul>
              {stringList(sc.worldRules).map((r, i) => (
                <li key={i}>{r}</li>
              ))}
            </ul>
          )}

          <h2 style={{ marginTop: 24 }}>能力与世界边界（abilityRules）</h2>
          {stringList(sc.abilityRules).length === 0 ? (
            <p>（无）</p>
          ) : (
            <ul>
              {stringList(sc.abilityRules).map((r, i) => (
                <li key={i}>{r}</li>
              ))}
            </ul>
          )}

          <h2 style={{ marginTop: 24 }}>禁忌事项 Forbidden Moves</h2>
          {stringList(sc.forbiddenMoves).length === 0 ? (
            <p>（无）</p>
          ) : (
            <ul>
              {stringList(sc.forbiddenMoves).map((r, i) => (
                <li key={i}>{r}</li>
              ))}
            </ul>
          )}

          <h2 style={{ marginTop: 24 }}>Style Guide</h2>
          {styleGuide ? (
            <ul>
              <li>
                <strong>叙事声音：</strong>
                {styleGuide.narrativeVoice ?? ""}
              </li>
              <li>
                <strong>节奏：</strong>
                {styleGuide.pacing ?? ""}
              </li>
              <li>
                <strong>对话占比：</strong>
                {styleGuide.dialogueRatio ?? ""}
              </li>
              <li>
                <strong>忌讳话题：</strong>
                {(styleGuide.tabooTopics ?? []).join("、") || "（无）"}
              </li>
            </ul>
          ) : (
            <p>（无）</p>
          )}

          <h2 style={{ marginTop: 24 }}>第一卷走向（Story Contract）</h2>
          <p>{String(sc.firstVolumeDirection ?? "")}</p>

          <h3 style={{ marginTop: 16 }}>核心配角（characters）</h3>
          {Array.isArray(sc.characters) && sc.characters.length > 0 ? (
            <ul>
              {(sc.characters as unknown[]).map((c, i) => {
                const ch = asRecord(c);
                if (!ch) return null;
                return (
                  <li key={i} style={{ marginBottom: 8 }}>
                    <strong>{String(ch.name ?? "")}</strong>（{String(ch.role ?? "")}）—{" "}
                    {String(ch.relationshipToProtagonist ?? "")}：{String(ch.oneLineHook ?? "")}
                  </li>
                );
              })}
            </ul>
          ) : (
            <p>（无）</p>
          )}

          <h2 style={{ marginTop: 32 }}>第一卷大纲</h2>
          <p style={{ fontSize: 13, color: "#64748b" }}>
            主阅读体验：约五百字的卷纲叙事；章纲在下方列表保持轻量索引。可直接修改下方文字并保存到当前选中快照（不改章纲 JSON）。
          </p>
          {outlineErr && <p style={{ color: "crimson", fontSize: 14 }}>{outlineErr}</p>}
          <textarea
            value={outlineDraft}
            onChange={(e) => setOutlineDraft(e.target.value)}
            rows={12}
            style={{ width: "100%", boxSizing: "border-box", fontFamily: "inherit", fontSize: 14 }}
          />
          <CopilotChatPanel
            projectId={projectId}
            scene="outline_edit"
            title="卷纲参谋"
            contextBlob={outlineCopilotContext}
          />
          <button
            type="button"
            disabled={outlineSaveBusy || !projectId}
            style={{ marginTop: 8 }}
            onClick={async () => {
              if (!projectId) return;
              setOutlineErr(null);
              setOutlineSaveBusy(true);
              try {
                await putFirstVolumeOutline(projectId, outlineDraft);
                const b = await getSelectedStoryBundle(projectId);
                if (b) setResult(b);
              } catch (e) {
                setOutlineErr(e instanceof Error ? e.message : String(e));
              } finally {
                setOutlineSaveBusy(false);
              }
            }}
          >
            {outlineSaveBusy ? "保存中…" : "保存第一卷大纲"}
          </button>

          <h2 style={{ marginTop: 24 }}>前 20 章章纲（Chapter Contracts）</h2>
          <ChapterContractList
            chapters={Array.isArray(result.chapterContracts) ? result.chapterContracts : []}
          />

          <h2 style={{ marginTop: 32 }}>试写一章（可选）</h2>
          <p style={{ fontSize: 14, color: "#444", maxWidth: 640 }}>
            这里会用当前选定的故事快照与章纲<strong>在线生成</strong>一章（页面需保持打开直至完成）。
            日常写作更推荐使用{" "}
            <Link to={`/projects/${encodeURIComponent(projectId!)}/chapters/${chapterGenNo}/workspace`}>
              章节写作台
            </Link>
            ：后台生成、可审核后再定稿。
          </p>
          <form onSubmit={onChapterGenerate} style={{ marginTop: 8 }}>
            <label>
              章节号{" "}
              <input
                type="number"
                min={1}
                max={200}
                value={chapterGenNo}
                onChange={(ev) => setChapterGenNo(Number(ev.target.value) || 1)}
                style={{ width: 72, marginRight: 12 }}
              />
            </label>
            <button type="submit" disabled={chapterGenBusy}>
              {chapterGenBusy ? "生成中…" : "在本页试写该章"}
            </button>
          </form>
          {chapterGenErr && <p style={{ color: "crimson", marginTop: 8 }}>{chapterGenErr}</p>}
          {chapterGenLog && (
            <pre
              style={{
                marginTop: 12,
                background: "#f6f6f6",
                padding: 12,
                borderRadius: 8,
                fontSize: 11,
                whiteSpace: "pre-wrap",
                wordBreak: "break-word",
                maxHeight: 240,
                overflow: "auto",
              }}
            >
              {chapterGenLog}
            </pre>
          )}
          {chapterGenFinal && (
            <div style={{ marginTop: 16, fontSize: 14 }}>
              <p>
                <strong>闸门是否通过：</strong>
                {String(chapterGenFinal.accepted ?? "")}{" "}
                <strong>是否被拒：</strong>
                {String(chapterGenFinal.rejected ?? "")}
              </p>
              <p>
                <strong>正文预览：</strong>
              </p>
              <pre style={{ whiteSpace: "pre-wrap", background: "#fafafa", padding: 10, borderRadius: 8 }}>
                {String(chapterGenFinal.chapter_text ?? "").slice(0, 4000)}
                {(String(chapterGenFinal.chapter_text ?? "").length > 4000 ? "\n…" : "")}
              </pre>
              <details style={{ marginTop: 12 }}>
                <summary style={{ cursor: "pointer", color: "#64748b" }}>审查明细（可选展开）</summary>
                <pre style={{ fontSize: 12, overflow: "auto", marginTop: 8 }}>
                  {JSON.stringify(chapterGenFinal.critic_report ?? {}, null, 2)}
                </pre>
              </details>
            </div>
          )}

          <details style={{ marginTop: 24 }}>
            <summary style={{ cursor: "pointer", fontWeight: 600 }}>原始设定 JSON（技术人员）</summary>
            <p style={{ fontSize: 13, color: "#64748b", marginTop: 8 }}>排查问题时再展开即可。</p>
            <h4 style={{ marginTop: 12, marginBottom: 8 }}>种子设定</h4>
            <pre
              style={{
                background: "#f6f6f6",
                padding: 12,
                borderRadius: 8,
                overflow: "auto",
                fontSize: 12,
              }}
            >
              {JSON.stringify(result.novelSeed, null, 2)}
            </pre>
            <h4 style={{ marginTop: 16, marginBottom: 8 }}>故事合约</h4>
            <pre
              style={{
                background: "#f6f6f6",
                padding: 12,
                borderRadius: 8,
                overflow: "auto",
                fontSize: 12,
              }}
            >
              {JSON.stringify(result.storyContract, null, 2)}
            </pre>
          </details>
        </div>
      )}
    </section>
  );
}
