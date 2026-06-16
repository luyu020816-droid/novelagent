import { Navigate, useParams } from "react-router-dom";

export default function StoryInitPage() {
  const { projectId } = useParams<{ projectId: string }>();

  if (!projectId) {
    return (
      <section className="mf-page">
        <p className="mf-alert mf-alert-error">缺少项目 ID</p>
      </section>
    );
  }

  return <Navigate to={`/projects/${encodeURIComponent(projectId)}/setup`} replace />;
}
