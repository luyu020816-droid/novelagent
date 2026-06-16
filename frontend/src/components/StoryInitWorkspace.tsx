import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import CopilotChatPanel from "./CopilotChatPanel";
import { Link } from "react-router-dom";
import { getProjectWorkspace, type ProjectWorkspace } from "../api/projects";
import {
  deleteStoryContract,
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

export type StoryInitWorkspaceProps = {
  projectId: string;
  /** 嵌入双栏时不显示页面级大标题，并缩小顶部留白 */
  embedded?: boolean;
};

/** 单部作品：初始化快照、向导、流水线、预览与试写（可多实例并行，radio 按 projectId 隔离） */
export function StoryInitWorkspace({ projectId, embedded }: StoryInitWorkspaceProps) {
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);
  const [result, setResult] = useState<StoryInitResponse | null>(null);
  const [initStreamLog, setInitStreamLog] = useState("");
  const [workspace, setWorkspace] = useState<ProjectWorkspace | null>(null);
  const [pickStoryId, setPickStoryId] = useState<string | null>(null);
  const [pickBusy, setPickBusy] = useState(false);
  const [pickErr, setPickErr] = useState<string | null>(null);
  const [deleteBusyId, setDeleteBusyId] = useState<string | null>(null);
  const [deleteErr, setDeleteErr] = useState<string | null>(null);
  const [reloadBusy, setReloadBusy] = useState(false);

  const [outlineDraft, setOutlineDraft] = useState("");
  const [outlineSaveBusy, setOutlineSaveBusy] = useState(false);
  const [outlineErr, setOutlineErr] = useState<string | null>(null);

  const [wizardNotes, setWizardNotes] = useState("");

  const pickStoryGroup = `pickStory-${projectId}`;

  const reloadFromServer = useCallback(async () => {
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
    void reloadFromServer();
  }, [projectId, reloadFromServer]);

  useEffect(() => {
    if (result?.firstVolumeOutline !== undefined) {
      setOutlineDraft(result.firstVolumeOutline ?? "");
    }
  }, [result?.storyContractId, result?.firstVolumeOutline]);

  const outlineCopilotContext = useMemo(() => {
    const parts = [`【当前剧情走向（一段式，可整段替换）】\n${outlineDraft}`];
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

  async function onConfirmStorySelection(e: FormEvent) {
    e.preventDefault();
    if (!pickStoryId) {
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

  const sc = result?.storyContract;
  const positioning = sc ? asRecord(sc.positioning) : undefined;
  const protagonist = sc ? (asRecord(sc.protagonist) as ProtagonistView | undefined) : undefined;
  const styleGuide = sc ? (asRecord(sc.styleGuide) as StyleGuideView | undefined) : undefined;

  const sectionGap = embedded ? 16 : 20;

  return (
    <section className="mf-prose" style={embedded ? { fontSize: 13 } : undefined}>
      <p style={{ fontSize: embedded ? 12 : 14, color: "var(--mf-muted)", maxWidth: embedded ? "none" : 720, marginTop: 0 }}>
        依赖：在项目详情页<strong>选定一份题材方案</strong>。初始化结果会<strong>写入数据库</strong>；刷新后可通过下方快照列表恢复。
      </p>

      <h2 className="mf-subsection-title" style={{ marginTop: sectionGap, fontSize: embedded ? 15 : 18 }}>
        已保存的初始化快照
      </h2>
      <p style={{ marginBottom: 8, display: "flex", flexWrap: "wrap", alignItems: "center", gap: 10 }}>
        <button
          type="button"
          className="mf-btn"
          onClick={() => void reloadFromServer()}
          disabled={reloadBusy}
        >
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
          <div className="mf-table-wrap" style={{ maxWidth: embedded ? "100%" : 900 }}>
            <table className="mf-table" style={{ fontSize: embedded ? 12 : 14 }}>
              <thead>
                <tr>
                  <th>查看</th>
                  <th>storyContractId</th>
                  <th>novelSeedContractId</th>
                  <th>创建时间</th>
                  <th>删除</th>
                </tr>
              </thead>
              <tbody>
                {(workspace?.storyInits ?? []).map((s) => (
                  <tr key={s.storyContractId}>
                    <td>
                    <input
                      type="radio"
                      name={pickStoryGroup}
                      checked={pickStoryId === s.storyContractId}
                      onChange={() => setPickStoryId(s.storyContractId)}
                    />
                    </td>
                    <td style={{ wordBreak: "break-all" }}>{s.storyContractId}</td>
                    <td style={{ wordBreak: "break-all" }}>{s.novelSeedContractId ?? "—"}</td>
                    <td style={{ whiteSpace: "nowrap" }}>{s.createdAt}</td>
                    <td>
                      <button
                        type="button"
                        className="mf-btn mf-btn-danger"
                        style={{ padding: "6px 10px", fontSize: 12 }}
                        disabled={deleteBusyId !== null}
                      onClick={async () => {
                        const isSel = s.storyContractId === workspace?.selectedStoryContractId;
                        if (
                          !window.confirm(
                            isSel
                              ? "该条为当前选中的快照。删除后会清空项目的「当前选中」，需再选其它快照后才能继续写作。确定删除？"
                              : "确定删除该初始化快照？将同时删除其章纲与动笔前摘要记录，且不可恢复。"
                          )
                        ) {
                          return;
                        }
                        setDeleteErr(null);
                        setDeleteBusyId(s.storyContractId);
                        try {
                          await deleteStoryContract(projectId, s.storyContractId);
                          if (result?.storyContractId === s.storyContractId) {
                            setResult(null);
                          }
                          if (pickStoryId === s.storyContractId) {
                            setPickStoryId(null);
                          }
                          await reloadFromServer();
                        } catch (e) {
                          setDeleteErr(e instanceof Error ? e.message : String(e));
                        } finally {
                          setDeleteBusyId(null);
                        }
                      }}
                      >
                        {deleteBusyId === s.storyContractId ? "删除中…" : "删除"}
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <button type="submit" className="mf-btn mf-btn-primary" disabled={pickBusy || !pickStoryId} style={{ marginTop: 12 }}>
            {pickBusy ? "切换中…" : "加载所选快照到下方预览"}
          </button>
        </form>
      )}
      {pickErr && (
        <p className="mf-alert mf-alert-error" style={{ marginTop: 8 }}>
          {pickErr}
        </p>
      )}
      {deleteErr && (
        <p className="mf-alert mf-alert-error" style={{ marginTop: 8 }}>
          {deleteErr}
        </p>
      )}

      <h2 className="mf-subsection-title" style={{ marginTop: sectionGap + 8, fontSize: embedded ? 15 : 18 }}>
        开机向导（可选）
      </h2>
      <p style={{ fontSize: 13, color: "#475569", maxWidth: embedded ? "none" : 720 }}>
        背景时代、口吻或禁区会与题材决策合并。也可先与参谋对话，再把定稿放进文本框。
      </p>
      <textarea
        className="mf-textarea"
        value={wizardNotes}
        onChange={(e) => setWizardNotes(e.target.value)}
        rows={embedded ? 4 : 6}
        placeholder="例如：民国晚期小城；叙事克制；主角最怕亏欠人情；不要系统文套路……"
        style={{ marginTop: 10 }}
      />
      <CopilotChatPanel projectId={projectId} scene="init_wizard" title="向导参谋" contextBlob={wizardNotes} />

      <form onSubmit={onSubmit} style={{ marginTop: 20 }}>
        <button type="submit" className="mf-btn mf-btn-primary" disabled={busy}>
          {busy ? "生成中…" : "运行新的初始化流水线"}
        </button>
      </form>

      <p style={{ marginTop: 8, fontSize: 12, color: "#444", maxWidth: 640 }}>
        初始化会依次调用策划、人物、世界观、卷纲与初审等环节；页面需保持打开。
      </p>

      {initStreamLog.length > 0 && (
        <pre
          className="mf-pre mf-pre-stream"
          style={{
            marginTop: 12,
            maxHeight: embedded ? 120 : 200,
            fontSize: 11,
            whiteSpace: "pre-wrap",
            wordBreak: "break-word",
          }}
        >
          {initStreamLog}
        </pre>
      )}
      {err && (
        <p className="mf-alert mf-alert-error" style={{ marginTop: 12 }}>
          {err}
        </p>
      )}

      {result && sc && (
        <div style={{ marginTop: 20 }}>
          <p style={{ fontSize: 12, color: "#64748b" }}>
            快照编号（内部）：种子 {result.novelSeedContractId} · 故事 {result.storyContractId}
          </p>

          <h2 style={{ marginTop: 16, fontSize: embedded ? 15 : 18 }}>核心卖点 / 定位（positioning）</h2>
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

          <h2 style={{ marginTop: 16, fontSize: embedded ? 15 : 18 }}>主角设定（protagonist）</h2>
          {protagonist && (
            <dl style={{ display: "grid", gridTemplateColumns: embedded ? "120px 1fr" : "160px 1fr", gap: 8 }}>
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
              <dt>优势 / 特殊处境</dt>
              <dd style={{ margin: 0 }}>{protagonist.goldenFinger ?? ""}</dd>
            </dl>
          )}

          <h2 style={{ marginTop: 16, fontSize: embedded ? 15 : 18 }}>世界规则（worldRules）</h2>
          {stringList(sc.worldRules).length === 0 ? (
            <p>（无）</p>
          ) : (
            <ul>
              {stringList(sc.worldRules).map((r, i) => (
                <li key={i}>{r}</li>
              ))}
            </ul>
          )}

          <h2 style={{ marginTop: 16, fontSize: embedded ? 15 : 18 }}>能力与世界边界（abilityRules）</h2>
          {stringList(sc.abilityRules).length === 0 ? (
            <p>（无）</p>
          ) : (
            <ul>
              {stringList(sc.abilityRules).map((r, i) => (
                <li key={i}>{r}</li>
              ))}
            </ul>
          )}

          <h2 style={{ marginTop: 16, fontSize: embedded ? 15 : 18 }}>禁忌事项 Forbidden Moves</h2>
          {stringList(sc.forbiddenMoves).length === 0 ? (
            <p>（无）</p>
          ) : (
            <ul>
              {stringList(sc.forbiddenMoves).map((r, i) => (
                <li key={i}>{r}</li>
              ))}
            </ul>
          )}

          <h2 style={{ marginTop: 16, fontSize: embedded ? 15 : 18 }}>Style Guide</h2>
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

          <h2 style={{ marginTop: 16, fontSize: embedded ? 15 : 18 }}>第一卷走向（Story Contract）</h2>
          <p>{String(sc.firstVolumeDirection ?? "")}</p>

          <h3 style={{ marginTop: 12 }}>核心配角（characters）</h3>
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

          <h2 style={{ marginTop: 24, fontSize: embedded ? 15 : 18 }}>剧情走向（一段式）</h2>
          <p style={{ fontSize: 12, color: "#64748b" }}>
            初始化会生成约 <strong>500～1000 字</strong> 的<strong>一段式</strong>主线走向（不分章）。可直接编辑保存；下方「剧情走向参谋」用对话把你的评价与修改意图迭代进文本，再点保存写回快照。
          </p>
          {outlineErr && <p style={{ color: "crimson", fontSize: 14 }}>{outlineErr}</p>}
          <textarea
            className="mf-textarea"
            value={outlineDraft}
            onChange={(e) => setOutlineDraft(e.target.value)}
            rows={embedded ? 8 : 12}
            style={{ fontSize: 14 }}
          />
          <CopilotChatPanel
            projectId={projectId}
            scene="outline_edit"
            title="剧情走向参谋"
            contextBlob={outlineCopilotContext}
          />
          <button
            type="button"
            className="mf-btn mf-btn-primary"
            disabled={outlineSaveBusy}
            style={{ marginTop: 8 }}
            onClick={async () => {
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
            {outlineSaveBusy ? "保存中…" : "保存剧情走向"}
          </button>

          <p style={{ marginTop: 20, fontSize: 14, color: "#334155" }}>
            机器写正文请使用{" "}
            <Link to={`/projects/${encodeURIComponent(projectId)}/chapters/1/workspace`}>章节写作台</Link>
            ：先确认本章「动笔前摘要」，再排队生成；不再依赖本页的逐章章纲列表。
          </p>

          <details style={{ marginTop: 24 }}>
            <summary style={{ cursor: "pointer", fontWeight: 600 }}>原始设定 JSON（技术人员）</summary>
            <p style={{ fontSize: 12, color: "#64748b", marginTop: 8 }}>排查问题时再展开。</p>
            <h4 style={{ marginTop: 12, marginBottom: 8 }}>种子设定</h4>
            <pre className="mf-pre" style={{ fontSize: 12 }}>
              {JSON.stringify(result.novelSeed, null, 2)}
            </pre>
            <h4 style={{ marginTop: 16, marginBottom: 8 }}>故事合约</h4>
            <pre className="mf-pre" style={{ fontSize: 12 }}>
              {JSON.stringify(result.storyContract, null, 2)}
            </pre>
          </details>
        </div>
      )}
    </section>
  );
}
