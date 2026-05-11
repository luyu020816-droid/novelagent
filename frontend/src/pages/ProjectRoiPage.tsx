import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { fetchChapterUsageByChapter, type ChapterUsageAggregateRow } from "../api/chapters";
import { fetchGenerationRoi, type GenerationRoiJobRow } from "../api/roi";

function maxBar(values: number[]): number {
  const m = Math.max(1, ...values);
  return m;
}

export default function ProjectRoiPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const [roi, setRoi] = useState<GenerationRoiJobRow[]>([]);
  const [usage, setUsage] = useState<ChapterUsageAggregateRow[]>([]);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    if (!projectId) return;
    setErr(null);
    Promise.all([fetchGenerationRoi(projectId), fetchChapterUsageByChapter(projectId)])
      .then(([r, u]) => {
        setRoi(r);
        setUsage(u);
      })
      .catch((e: Error) => setErr(e.message));
  }, [projectId]);

  const succeeded = useMemo(() => roi.filter((x) => x.status === "SUCCEEDED"), [roi]);

  const byChapterTokens = useMemo(() => {
    const m = new Map<number, number>();
    for (const row of succeeded) {
      const t = row.totalTokens ?? 0;
      m.set(row.chapterNo, (m.get(row.chapterNo) ?? 0) + t);
    }
    return Array.from(m.entries()).sort((a, b) => a[0] - b[0]);
  }, [succeeded]);

  const barScale = maxBar(byChapterTokens.map(([, v]) => v));

  if (!projectId) {
    return <p>缺少项目 ID</p>;
  }

  return (
    <section style={{ maxWidth: 760 }}>
      <p>
        <Link to={`/projects/${encodeURIComponent(projectId)}`}>← 返回项目</Link>
      </p>
      <h1 style={{ fontSize: 22 }}>全书用量统计</h1>
      <p style={{ color: "#555", fontSize: 14, maxWidth: 640 }}>
        统计每次自动写作大致消耗的 token（估算值），便于把握成本。下方表格分别来自「按任务汇总」与「按章累计」两种口径。
      </p>

      {err && <p style={{ color: "crimson" }}>{err}</p>}

      <h2 style={{ marginTop: 24, fontSize: 18 }}>按章汇总（仅统计已成功的后台任务）</h2>
      {byChapterTokens.length === 0 ? (
        <p style={{ fontSize: 14 }}>暂无成功的异步生成记录。</p>
      ) : (
        <div style={{ marginTop: 12 }}>
          {byChapterTokens.map(([ch, tokens]) => (
            <div key={ch} style={{ marginBottom: 10 }}>
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13 }}>
                <span>第 {ch} 章</span>
                <span>{tokens.toLocaleString()} tokens</span>
              </div>
              <div
                style={{
                  height: 10,
                  background: "#eee",
                  borderRadius: 4,
                  overflow: "hidden",
                  marginTop: 4,
                }}
              >
                <div
                  style={{
                    width: `${Math.round((tokens / barScale) * 100)}%`,
                    height: "100%",
                    background: "linear-gradient(90deg,#3b82f6,#6366f1)",
                  }}
                />
              </div>
            </div>
          ))}
        </div>
      )}

      <h2 style={{ marginTop: 28, fontSize: 18 }}>按章累计（写作流水明细）</h2>
      {usage.length === 0 ? (
        <p style={{ fontSize: 14 }}>暂无。</p>
      ) : (
        <table style={{ borderCollapse: "collapse", fontSize: 13, marginTop: 8, width: "100%" }}>
          <thead>
            <tr>
              <th style={{ border: "1px solid #ddd", padding: 6, textAlign: "left" }}>章号</th>
              <th style={{ border: "1px solid #ddd", padding: 6, textAlign: "right" }}>调用次数</th>
              <th style={{ border: "1px solid #ddd", padding: 6, textAlign: "right" }}>累计 tokens</th>
            </tr>
          </thead>
          <tbody>
            {usage.map((row) => (
              <tr key={row.chapterNo}>
                <td style={{ border: "1px solid #ddd", padding: 6 }}>{row.chapterNo}</td>
                <td style={{ border: "1px solid #ddd", padding: 6, textAlign: "right" }}>
                  {row.callCount}
                </td>
                <td style={{ border: "1px solid #ddd", padding: 6, textAlign: "right" }}>
                  {row.totalTokens.toLocaleString()}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h2 style={{ marginTop: 28, fontSize: 18 }}>每一次后台写作任务</h2>
      <div style={{ overflowX: "auto" }}>
        <table style={{ borderCollapse: "collapse", fontSize: 12, marginTop: 8, minWidth: 640 }}>
          <thead>
            <tr>
              <th style={{ border: "1px solid #ddd", padding: 6, textAlign: "left" }}>章</th>
              <th style={{ border: "1px solid #ddd", padding: 6, textAlign: "left" }}>状态</th>
              <th style={{ border: "1px solid #ddd", padding: 6, textAlign: "right" }}>总 tokens</th>
              <th style={{ border: "1px solid #ddd", padding: 6, textAlign: "right" }}>重试沉没</th>
              <th style={{ border: "1px solid #ddd", padding: 6, textAlign: "right" }}>裁剪条数</th>
              <th style={{ border: "1px solid #ddd", padding: 6, textAlign: "right" }}>Critic 重试轮</th>
            </tr>
          </thead>
          <tbody>
            {roi.map((row) => (
              <tr key={row.jobId}>
                <td style={{ border: "1px solid #ddd", padding: 6 }}>{row.chapterNo}</td>
                <td style={{ border: "1px solid #ddd", padding: 6 }}>{row.status}</td>
                <td style={{ border: "1px solid #ddd", padding: 6, textAlign: "right" }}>
                  {(row.totalTokens ?? "—").toString()}
                </td>
                <td style={{ border: "1px solid #ddd", padding: 6, textAlign: "right" }}>
                  {(row.retryWasteTokens ?? "—").toString()}
                </td>
                <td style={{ border: "1px solid #ddd", padding: 6, textAlign: "right" }}>
                  {(row.trimmedOptionalCount ?? "—").toString()}
                </td>
                <td style={{ border: "1px solid #ddd", padding: 6, textAlign: "right" }}>
                  {(row.criticRejectRounds ?? "—").toString()}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
