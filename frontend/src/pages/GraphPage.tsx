import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getProjectLoreGraph, type LoreSnapshot } from "../api/lore";

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
    return (
      <section className="mf-page">
        <p className="mf-alert mf-alert-error">缺少项目 ID</p>
      </section>
    );
  }

  return (
    <section className="mf-page mf-prose">
      <Link to={`/projects/${encodeURIComponent(projectId)}`} className="mf-back">
        ← 返回作品主页
      </Link>
      <h1 className="mf-page-title">人物与世界观手册</h1>
      <p className="mf-page-lede">
        您在章节写作台<strong>接受定稿</strong>后，系统会从正文里整理出人物、人物之间的关系、重要事件和未解决的伏笔。
        写作新书稿时会参考这些信息，减少人设崩坏或前后矛盾。
      </p>

      {err && (
        <p className="mf-alert mf-alert-error" role="alert">
          {err}
        </p>
      )}

      {!data && !err && <p className="mf-muted">加载中…</p>}

      {data && (
        <>
          {!data.neo4j_enabled && (
            <p className="mf-alert mf-alert-warn">
              世界观手册暂不可用（后台存储未就绪）。若您自建环境，请确认写作服务与数据库已启动。
            </p>
          )}

          <h2 className="mf-section-title" style={{ marginTop: 8 }}>
            登场人物
          </h2>
          <div className="mf-table-wrap">
            <table className="mf-table">
              <thead>
                <tr>
                  <th>姓名</th>
                  <th>最近章节</th>
                  <th>角色提示</th>
                  <th>证据摘录</th>
                </tr>
              </thead>
              <tbody>
                {(data.characters ?? []).map((r, i) => (
                  <tr key={`c-${i}`}>
                    <td>{r.name ?? ""}</td>
                    <td>{r.last_chapter_no ?? ""}</td>
                    <td>{r.role_hint ?? ""}</td>
                    <td>{r.evidence ?? ""}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <h2 className="mf-section-title">人物之间的关系</h2>
          <div className="mf-table-wrap">
            <table className="mf-table">
              <thead>
                <tr>
                  <th>From</th>
                  <th>关系</th>
                  <th>To</th>
                  <th>章节</th>
                  <th>证据</th>
                </tr>
              </thead>
              <tbody>
                {(data.relationships ?? []).map((r, i) => (
                  <tr key={`rel-${i}`}>
                    <td>{r.from_name ?? ""}</td>
                    <td>{r.kind ?? ""}</td>
                    <td>{r.to_name ?? ""}</td>
                    <td>{r.chapter_no ?? ""}</td>
                    <td>{r.evidence ?? ""}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <h2 className="mf-section-title">关键事件</h2>
          <div className="mf-table-wrap">
            <table className="mf-table">
              <thead>
                <tr>
                  <th>摘要</th>
                  <th>章节</th>
                  <th>证据</th>
                </tr>
              </thead>
              <tbody>
                {(data.events ?? []).map((r, i) => (
                  <tr key={`e-${i}`}>
                    <td>{r.summary ?? ""}</td>
                    <td>{r.chapter_no ?? ""}</td>
                    <td>{r.evidence ?? ""}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <h2 className="mf-section-title">伏笔与悬念</h2>
          <div className="mf-table-wrap">
            <table className="mf-table">
              <thead>
                <tr>
                  <th>文本</th>
                  <th>引入章节</th>
                  <th>是否已收尾</th>
                  <th>证据</th>
                </tr>
              </thead>
              <tbody>
                {(data.foreshadowing ?? []).map((r, i) => (
                  <tr key={`f-${i}`}>
                    <td>{r.text ?? ""}</td>
                    <td>{r.chapter_no ?? ""}</td>
                    <td>{r.resolved ? "是" : "否"}</td>
                    <td>{r.evidence ?? ""}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}
    </section>
  );
}
