import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { getProjectDetail, type ProjectDetail } from "../api/projects";

export default function ProjectDetailPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const [data, setData] = useState<ProjectDetail | null>(null);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    if (!projectId) {
      setErr("缺少项目 ID");
      return;
    }
    setErr(null);
    getProjectDetail(projectId)
      .then(setData)
      .catch((e: Error) => setErr(e.message));
  }, [projectId]);

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

  return (
    <section>
      <h1>项目详情</h1>
      <p>
        <Link to="/">← 返回列表</Link>
      </p>

      <h2 style={{ marginTop: 24 }}>基本信息</h2>
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

      <h2 style={{ marginTop: 24 }}>Writer 引擎</h2>
      <p>
        <strong>连接状态：</strong>
        {writerConnected ? (
          <span style={{ color: "green" }}>正常（health 与 test 均成功）</span>
        ) : (
          <span style={{ color: "crimson" }}>异常（见下方明细）</span>
        )}
      </p>

      <h3>GET /api/writer/health（经 Java 转发探测）</h3>
      <pre
        style={{
          background: "#f6f6f6",
          padding: 12,
          borderRadius: 8,
          overflow: "auto",
          fontSize: 13,
        }}
      >
        {JSON.stringify(writerEngine.health, null, 2)}
      </pre>

      <h3>POST /api/writer/test（经 Java 转发探测）</h3>
      <pre
        style={{
          background: "#f6f6f6",
          padding: 12,
          borderRadius: 8,
          overflow: "auto",
          fontSize: 13,
        }}
      >
        {JSON.stringify(writerEngine.test, null, 2)}
      </pre>
    </section>
  );
}
