import { FormEvent, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  parseGenreForm,
  postGenreRecommend,
  type GenreDecisionContract,
  type GenreRecommendResponse,
} from "../api/genre";
import { getProjectDetail, type ProjectDetail } from "../api/projects";

export default function ProjectDetailPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const [data, setData] = useState<ProjectDetail | null>(null);
  const [err, setErr] = useState<string | null>(null);

  const [genreBusy, setGenreBusy] = useState(false);
  const [genreErr, setGenreErr] = useState<string | null>(null);
  const [genreResult, setGenreResult] = useState<GenreRecommendResponse | null>(null);
  const [targetPlatform, setTargetPlatform] = useState("番茄");
  const [genderChannel, setGenderChannel] = useState("男频");
  const [preferredGenresRaw, setPreferredGenresRaw] = useState("");
  const [avoidRaw, setAvoidRaw] = useState("强虐, 纯后宫");
  const [writingStrengthRaw, setWritingStrengthRaw] = useState("爽点, 反转");
  const [riskPreference, setRiskPreference] = useState("medium");

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

  async function onGenreSubmit(e: FormEvent) {
    e.preventDefault();
    if (!projectId) return;
    setGenreErr(null);
    setGenreBusy(true);
    try {
      const body = parseGenreForm({
        targetPlatform,
        genderChannel,
        preferredGenresRaw,
        avoidRaw,
        writingStrengthRaw,
        riskPreference,
      });
      const res = await postGenreRecommend(projectId, body);
      setGenreResult(res);
    } catch (ex) {
      setGenreErr(ex instanceof Error ? ex.message : String(ex));
    } finally {
      setGenreBusy(false);
    }
  }

  const contract: GenreDecisionContract | undefined = genreResult?.contract;

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

      <h2 style={{ marginTop: 32 }}>题材推荐（Day 4）</h2>
      <p>提交偏好后由 Java 调用 Writer，生成 3 个候选题材并写入数据库。</p>
      <form onSubmit={onGenreSubmit} style={{ display: "flex", flexDirection: "column", gap: 10, maxWidth: 520 }}>
        <label>
          目标平台
          <input
            value={targetPlatform}
            onChange={(e) => setTargetPlatform(e.target.value)}
            required
            style={{ display: "block", width: "100%", marginTop: 4 }}
          />
        </label>
        <label>
          频道
          <input
            value={genderChannel}
            onChange={(e) => setGenderChannel(e.target.value)}
            required
            style={{ display: "block", width: "100%", marginTop: 4 }}
          />
        </label>
        <label>
          偏好题材（逗号分隔，可空）
          <input
            value={preferredGenresRaw}
            onChange={(e) => setPreferredGenresRaw(e.target.value)}
            style={{ display: "block", width: "100%", marginTop: 4 }}
          />
        </label>
        <label>
          避雷（逗号分隔）
          <input
            value={avoidRaw}
            onChange={(e) => setAvoidRaw(e.target.value)}
            style={{ display: "block", width: "100%", marginTop: 4 }}
          />
        </label>
        <label>
          写法强项（逗号分隔）
          <input
            value={writingStrengthRaw}
            onChange={(e) => setWritingStrengthRaw(e.target.value)}
            style={{ display: "block", width: "100%", marginTop: 4 }}
          />
        </label>
        <label>
          风险承受
          <select
            value={riskPreference}
            onChange={(e) => setRiskPreference(e.target.value)}
            style={{ display: "block", width: "100%", marginTop: 4 }}
          >
            <option value="low">low</option>
            <option value="medium">medium</option>
            <option value="high">high</option>
          </select>
        </label>
        <button type="submit" disabled={genreBusy}>
          {genreBusy ? "生成中…" : "生成题材推荐"}
        </button>
      </form>
      {!writerConnected && (
        <p style={{ color: "crimson", marginTop: 8 }}>
          Writer 未连通时建议先排除 Writer 故障再生成（仍可强行在后端直接调 Python 调试）。
        </p>
      )}
      {genreErr && <p style={{ color: "crimson", marginTop: 8 }}>{genreErr}</p>}

      {contract && (
        <div style={{ marginTop: 24 }}>
          <p>
            <strong>contractId：</strong>
            {genreResult?.contractId}
          </p>
          <h3>主推方向 selectedDirection</h3>
          <p>
            <strong>{contract.selectedDirection.genre}</strong>（{contract.selectedDirection.channel}）
          </p>
          <p>{contract.selectedDirection.reason}</p>
          <p>
            <strong>标签：</strong>
            {contract.selectedDirection.subTags.join("、")}
          </p>

          <h3>核心钩子 recommendedCoreHook</h3>
          <p>{contract.recommendedCoreHook}</p>

          <h3>全局风险 riskNotes</h3>
          {contract.riskNotes.length === 0 ? (
            <p>（无）</p>
          ) : (
            <ul>
              {contract.riskNotes.map((r, i) => (
                <li key={i}>{r}</li>
              ))}
            </ul>
          )}

          <h3>三个候选 candidateRankings</h3>
          <ol>
            {contract.candidateRankings.map((c, i) => (
              <li key={i} style={{ marginBottom: 16 }}>
                <strong>{c.genre}</strong> — finalScore {c.finalScore} / token {c.tokenCostLevel}
                <div style={{ marginTop: 6 }}>
                  <strong>推荐理由：</strong>
                  {c.recommendReason}
                </div>
                <div style={{ marginTop: 6 }}>
                  <strong>风险：</strong>
                  {c.riskNote}
                </div>
              </li>
            ))}
          </ol>
        </div>
      )}
    </section>
  );
}
