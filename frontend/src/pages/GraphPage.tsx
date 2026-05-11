import type { CSSProperties } from "react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getProjectLoreGraph, type LoreSnapshot } from "../api/lore";

function tableStyle(): CSSProperties {
  return {
    width: "100%",
    borderCollapse: "collapse",
    fontSize: 13,
    marginBottom: 24,
  };
}

function thStyle(): CSSProperties {
  return { textAlign: "left", borderBottom: "1px solid #ccc", padding: "6px 8px", background: "#f6f6f6" };
}

function tdStyle(): CSSProperties {
  return { borderBottom: "1px solid #eee", padding: "6px 8px", verticalAlign: "top" };
}

export default function GraphPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const [data, setData] = useState<LoreSnapshot | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    if (!projectId) {
      setErr("缺少项目 ID");
      return;
    }
    setErr(null);
    getProjectLoreGraph(projectId)
      .then(setData)
      .catch((e: Error) => setErr(e.message));
  }, [projectId]);

  if (!projectId) {
    return <p>缺少项目 ID</p>;
  }

  return (
    <div>
      <p style={{ marginBottom: 16 }}>
        <Link to={`/projects/${encodeURIComponent(projectId)}`}>← 返回项目</Link>
      </p>
      <h1 style={{ fontSize: 22, marginBottom: 8 }}>人物与世界观手册</h1>
      <p style={{ color: "#555", marginBottom: 16, maxWidth: 640 }}>
        您在章节写作台<strong>接受定稿</strong>后，系统会从正文里整理出人物、人物之间的关系、重要事件和未解决的伏笔。
        写作新书稿时会参考这些信息，减少人设崩坏或前后矛盾。
      </p>

      {err && <p style={{ color: "crimson" }}>{err}</p>}

      {!data && !err && <p>加载中…</p>}

      {data && (
        <>
          {!data.neo4j_enabled && (
            <p style={{ color: "#a60" }}>
              世界观手册暂不可用（后台存储未就绪）。若您自建环境，请确认写作服务与数据库已启动。
            </p>
          )}

          <h2 style={{ fontSize: 16 }}>登场人物</h2>
          <table style={tableStyle()}>
            <thead>
              <tr>
                <th style={thStyle()}>姓名</th>
                <th style={thStyle()}>最近章节</th>
                <th style={thStyle()}>角色提示</th>
                <th style={thStyle()}>证据摘录</th>
              </tr>
            </thead>
            <tbody>
              {(data.characters ?? []).map((r, i) => (
                <tr key={`c-${i}`}>
                  <td style={tdStyle()}>{r.name ?? ""}</td>
                  <td style={tdStyle()}>{r.last_chapter_no ?? ""}</td>
                  <td style={tdStyle()}>{r.role_hint ?? ""}</td>
                  <td style={tdStyle()}>{r.evidence ?? ""}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <h2 style={{ fontSize: 16 }}>人物之间的关系</h2>
          <table style={tableStyle()}>
            <thead>
              <tr>
                <th style={thStyle()}>From</th>
                <th style={thStyle()}>关系</th>
                <th style={thStyle()}>To</th>
                <th style={thStyle()}>章节</th>
                <th style={thStyle()}>证据</th>
              </tr>
            </thead>
            <tbody>
              {(data.relationships ?? []).map((r, i) => (
                <tr key={`rel-${i}`}>
                  <td style={tdStyle()}>{r.from_name ?? ""}</td>
                  <td style={tdStyle()}>{r.kind ?? ""}</td>
                  <td style={tdStyle()}>{r.to_name ?? ""}</td>
                  <td style={tdStyle()}>{r.chapter_no ?? ""}</td>
                  <td style={tdStyle()}>{r.evidence ?? ""}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <h2 style={{ fontSize: 16 }}>关键事件</h2>
          <table style={tableStyle()}>
            <thead>
              <tr>
                <th style={thStyle()}>摘要</th>
                <th style={thStyle()}>章节</th>
                <th style={thStyle()}>证据</th>
              </tr>
            </thead>
            <tbody>
              {(data.events ?? []).map((r, i) => (
                <tr key={`e-${i}`}>
                  <td style={tdStyle()}>{r.summary ?? ""}</td>
                  <td style={tdStyle()}>{r.chapter_no ?? ""}</td>
                  <td style={tdStyle()}>{r.evidence ?? ""}</td>
                </tr>
              ))}
            </tbody>
          </table>

          <h2 style={{ fontSize: 16 }}>伏笔与悬念</h2>
          <table style={tableStyle()}>
            <thead>
              <tr>
                <th style={thStyle()}>文本</th>
                <th style={thStyle()}>引入章节</th>
                <th style={thStyle()}>是否已收尾</th>
                <th style={thStyle()}>证据</th>
              </tr>
            </thead>
            <tbody>
              {(data.foreshadowing ?? []).map((r, i) => (
                <tr key={`f-${i}`}>
                  <td style={tdStyle()}>{r.text ?? ""}</td>
                  <td style={tdStyle()}>{r.chapter_no ?? ""}</td>
                  <td style={tdStyle()}>{String(r.resolved ?? false)}</td>
                  <td style={tdStyle()}>{r.evidence ?? ""}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </div>
  );
}
