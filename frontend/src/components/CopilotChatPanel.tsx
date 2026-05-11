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
};

export default function CopilotChatPanel({
  projectId,
  scene,
  title = "写作参谋",
  contextBlob = "",
  chapterNo,
}: Props) {
  const [open, setOpen] = useState(false);
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
      style={{
        marginTop: 12,
        border: "1px solid #e2e8f0",
        borderRadius: 10,
        background: "#fafafa",
        overflow: "hidden",
      }}
    >
      <button
        type="button"
        onClick={() => setOpen((v) => !v)}
        style={{
          width: "100%",
          textAlign: "left",
          padding: "10px 14px",
          border: "none",
          background: open ? "#f1f5f9" : "#fafafa",
          cursor: "pointer",
          fontWeight: 600,
          fontSize: 14,
        }}
      >
        {open ? "▼" : "▶"} {title}
        <span style={{ fontWeight: 400, color: "#64748b", marginLeft: 8, fontSize: 13 }}>
          提出修改想法 → 参谋给草案 → 你再粘贴到正文或向导里确认
        </span>
      </button>
      {open && (
        <div style={{ padding: "0 14px 14px" }}>
          {messages.length === 0 && (
            <p style={{ fontSize: 13, color: "#64748b", margin: "8px 0 12px" }}>
              说明你的顾虑或想要的改动；参谋会基于上方页面里的上下文回复（每次发送都会带上当前上下文）。
            </p>
          )}
          <div
            style={{
              maxHeight: 220,
              overflow: "auto",
              fontSize: 13,
              marginBottom: 10,
              background: "#fff",
              borderRadius: 8,
              padding: 10,
              border: "1px solid #e5e7eb",
            }}
          >
            {messages.map((m, i) => (
              <p key={i} style={{ margin: "0 0 10px", whiteSpace: "pre-wrap" }}>
                <strong style={{ color: m.role === "user" ? "#1d4ed8" : "#047857" }}>
                  {m.role === "user" ? "你" : "参谋"}
                  ：
                </strong>
                {m.content}
              </p>
            ))}
          </div>
          {err && <p style={{ color: "crimson", fontSize: 13, marginBottom: 8 }}>{err}</p>}
          <form onSubmit={onSubmit} style={{ display: "flex", gap: 8, alignItems: "flex-end", flexWrap: "wrap" }}>
            <textarea
              value={input}
              onChange={(ev) => setInput(ev.target.value)}
              placeholder="例如：希望开局更压抑一点，女主动机改成……"
              rows={3}
              style={{
                flex: "1 1 240px",
                minWidth: 200,
                fontFamily: "inherit",
                fontSize: 14,
                padding: 8,
                borderRadius: 8,
                border: "1px solid #cbd5e1",
              }}
            />
            <button type="submit" disabled={busy} style={{ padding: "10px 16px", alignSelf: "stretch" }}>
              {busy ? "思考中…" : "发送"}
            </button>
          </form>
        </div>
      )}
    </div>
  );
}
