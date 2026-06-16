import { FormEvent, useState } from "react";
import { postCopilotChat, type CopilotChatMessage, type CopilotScene } from "../api/copilot";

type Props = {
  projectId: string;
  scene: CopilotScene;
  /** 展示在折叠按钮上 */
  title?: string;
  /** 传给模型的静态上下文（大纲正文、章节片段等） */
  contextBlob?: string;
  chapterNo?: number;
  /** 嵌入侧栏：始终展开、无折叠条 */
  embedded?: boolean;
  /** 对话区最大高度（px），嵌入时可加大 */
  messagesMaxHeight?: number;
};

export default function CopilotChatPanel({
  projectId,
  scene,
  title = "写作参谋",
  contextBlob = "",
  chapterNo,
  embedded = false,
  messagesMaxHeight = 220,
}: Props) {
  const [open, setOpen] = useState(embedded);
  const [messages, setMessages] = useState<CopilotChatMessage[]>([]);
  const [input, setInput] = useState("");
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const text = input.trim();
    if (!text || busy) return;

    let ctx = contextBlob.trim();
    if (scene === "chapter_coach" && chapterNo != null) {
      ctx = `【第 ${chapterNo} 章】\n${ctx}`;
    }

    const nextMsgs: CopilotChatMessage[] = [...messages, { role: "user", content: text }];
    setBusy(true);
    setErr(null);
    setInput("");
    try {
      const res = await postCopilotChat({
        projectId,
        scene,
        messages: nextMsgs,
        contextBlob: ctx || undefined,
      });
      setMessages([...nextMsgs, { role: "assistant", content: res.reply }]);
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : String(ex));
      setInput(text);
    } finally {
      setBusy(false);
    }
  }

  return (
    <div
      className={embedded ? "mf-copilot-embedded" : "mf-copilot-shell"}
      style={{
        marginTop: embedded ? 0 : 12,
        overflow: "hidden",
      }}
    >
      {!embedded && (
        <button
          type="button"
          className="mf-copilot-toggle"
          onClick={() => setOpen((v) => !v)}
          style={{
            background: open ? "var(--mf-surface-muted)" : "transparent",
          }}
        >
          {open ? "▼" : "▶"} {title}
          <span style={{ fontWeight: 400, color: "#64748b", marginLeft: 8, fontSize: 13 }}>
            提出修改想法 → 参谋给草案 → 你再粘贴到正文或向导里确认
          </span>
        </button>
      )}
      {embedded && (
        <div
          style={{
            fontWeight: 600,
            fontSize: 14,
            color: "var(--mf-text-secondary)",
            marginBottom: 8,
            paddingBottom: 6,
            borderBottom: "1px solid var(--mf-border)",
          }}
        >
          {title}
          <span style={{ fontWeight: 400, color: "var(--mf-muted)", marginLeft: 8, fontSize: 12 }}>
            发送时会带上当前正文节选（若有）与下方「修改意见」里已填写的内容
          </span>
        </div>
      )}
      {(embedded || open) && (
        <div style={{ padding: embedded ? "0" : "0 14px 14px" }}>
          {messages.length === 0 && (
            <p className="mf-muted mf-text-sm" style={{ margin: embedded ? "0 0 8px" : "8px 0 12px" }}>
              说明你的顾虑或想要的改动；参谋会基于当前章节上下文回复。
            </p>
          )}
          <div className="mf-msg-box" style={{ maxHeight: messagesMaxHeight, overflow: "auto" }}>
            {messages.map((m, i) => (
              <p key={i} style={{ margin: "0 0 10px", whiteSpace: "pre-wrap" }}>
                <strong style={{ color: m.role === "user" ? "var(--mf-accent)" : "var(--mf-success)" }}>
                  {m.role === "user" ? "你" : "参谋"}
                  ：
                </strong>
                {m.content}
              </p>
            ))}
          </div>
          {err && <p className="mf-alert mf-alert-error" style={{ fontSize: 13, marginBottom: 8 }}>{err}</p>}
          <form onSubmit={onSubmit} style={{ display: "flex", gap: 8, alignItems: "flex-end", flexWrap: "wrap" }}>
            <textarea
              value={input}
              onChange={(ev) => setInput(ev.target.value)}
              placeholder="例如：希望开局更压抑一点，女主动机改成……"
              rows={3}
              className="mf-textarea"
              style={{
                flex: "1 1 240px",
                minWidth: embedded ? 0 : 200,
                minHeight: 72,
              }}
            />
            <button type="submit" disabled={busy} className="mf-btn mf-btn-primary" style={{ alignSelf: "stretch" }}>
              {busy ? "思考中…" : "发送"}
            </button>
          </form>
        </div>
      )}
    </div>
  );
}
