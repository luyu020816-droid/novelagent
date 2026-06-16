import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { fetchChapterUsageByChapter, type ChapterUsageAggregateRow } from "../api/chapters";
import { fetchGenerationRoi, type GenerationRoiJobRow } from "../api/roi";

function maxBar(values: number[]): number {
  return Math.max(1, ...values);
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
    return (
      <section className="mf-page">
        <p className="mf-alert mf-alert-error">缺少项目 ID</p>
      </section>
    );
  }

  return (
    <section className="mf-page mf-prose" style={{ maxWidth: 800 }}>
      <Link to={`/projects/${encodeURIComponent(projectId)}`} className="mf-back">
        ← 返回作品主页
      </Link>
      <h1 className="mf-page-title">全书用量统计</h1>
      <p className="mf-page-lede">
        统计每次自动写作大致消耗的 token（估算值），便于把握成本。下方表格分别来自「按任务汇总」与「按章累计」两种口径。
      </p>

      {err && (
        <p className="mf-alert mf-alert-error" role="alert">
          {err}
        </p>
      )}

      <h2 className="mf-section-title" style={{ marginTop: 8 }}>
        按章汇总（仅统计已成功的后台任务）
      </h2>
      {byChapterTokens.length === 0 ? (
        <p className="mf-muted mf-text-sm">暂无成功的异步生成记录。</p>
      ) : (
        <div className="mf-card mf-card-pad" style={{ marginTop: 4 }}>
          {byChapterTokens.map(([ch, tokens]) => (
            <div key={ch} style={{ marginBottom: 12 }}>
              <div style={{ display: "flex", justifyContent: "space-between", fontSize: 13 }}>
                <span>第 {ch} 章</span>
                <span className="mf-muted">{tokens.toLocaleString()} tokens</span>
              </div>
              <div className="mf-bar-track">
                <div className="mf-bar-fill" style={{ width: `${Math.round((tokens / barScale) * 100)}%` }} />
              </div>
            </div>
          ))}
        </div>
      )}

      <h2 className="mf-section-title">按章累计（写作流水明细）</h2>
      {usage.length === 0 ? (
        <p className="mf-muted mf-text-sm">暂无。</p>
      ) : (
        <div className="mf-table-wrap">
          <table className="mf-table">
            <thead>
              <tr>
                <th>章号</th>
                <th className="mf-table-num">调用次数</th>
                <th className="mf-table-num">累计 tokens</th>
              </tr>
            </thead>
            <tbody>
              {usage.map((row) => (
                <tr key={row.chapterNo}>
                  <td>{row.chapterNo}</td>
                  <td className="mf-table-num">{row.callCount}</td>
                  <td className="mf-table-num">{row.totalTokens.toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <h2 className="mf-section-title">每一次后台写作任务</h2>
      <div className="mf-table-wrap">
        <table className="mf-table" style={{ minWidth: 640 }}>
          <thead>
            <tr>
              <th>章</th>
              <th>状态</th>
              <th className="mf-table-num">总 tokens</th>
              <th className="mf-table-num">重试沉没</th>
              <th className="mf-table-num">裁剪条数</th>
              <th className="mf-table-num">Critic 重试轮</th>
            </tr>
          </thead>
          <tbody>
            {roi.map((row) => (
              <tr key={row.jobId}>
                <td>{row.chapterNo}</td>
                <td>{row.status}</td>
                <td className="mf-table-num">{(row.totalTokens ?? "—").toString()}</td>
                <td className="mf-table-num">{(row.retryWasteTokens ?? "—").toString()}</td>
                <td className="mf-table-num">{(row.trimmedOptionalCount ?? "—").toString()}</td>
                <td className="mf-table-num">{(row.criticRejectRounds ?? "—").toString()}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
