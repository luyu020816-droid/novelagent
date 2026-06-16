import { Link, useParams, useSearchParams } from "react-router-dom";
import SetupStudio from "../components/SetupStudio";

export default function SetupStudioPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const [search] = useSearchParams();
  const stage = search.get("stage");

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
        ← 返回项目主页
      </Link>
      <h1 className="mf-page-title">创作设定</h1>
      <p className="mf-page-lede">
        新书按步骤确认题材、故事契约与故事结构；已开始写作的作品仅可在此查看已定设定，请在工作台继续写章。
      </p>
      <SetupStudio projectId={projectId} initialStage={stage} />
    </section>
  );
}
