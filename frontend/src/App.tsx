import { Link, Route, Routes, useLocation } from "react-router-dom";
import ProjectCreatePage from "./pages/ProjectCreatePage";
import ProjectDetailPage from "./pages/ProjectDetailPage";
import ProjectListPage from "./pages/ProjectListPage";
import ChapterWorkspacePage from "./pages/ChapterWorkspacePage";
import StoryInitPage from "./pages/StoryInitPage";
import SetupStudioPage from "./pages/SetupStudioPage";
import GraphPage from "./pages/GraphPage";
import WorkflowDagPage from "./pages/WorkflowDagPage";
import ProjectRoiPage from "./pages/ProjectRoiPage";
import GovernancePage from "./pages/GovernancePage";
import DualBooksPage from "./pages/DualBooksPage";

function chapterWorkspacePath(pathname: string): boolean {
  return /\/projects\/[^/]+\/chapters\/\d+\/workspace\/?$/.test(pathname);
}

export default function App() {
  const location = useLocation();
  const fullBleed = chapterWorkspacePath(location.pathname);

  return (
    <div
      className={fullBleed ? "mf-app" : "mf-app mf-app-inner"}
      style={{
        fontFamily: "inherit",
        maxWidth: fullBleed ? "none" : 1180,
        margin: fullBleed ? 0 : "0 auto",
        padding: fullBleed ? 0 : "20px 20px 56px",
        minHeight: "100vh",
        background: fullBleed ? "#e8edf5" : "transparent",
        color: "var(--mf-text)",
        boxSizing: "border-box",
      }}
    >
      {!fullBleed && (
        <header className="mf-app-header">
          <div className="mf-brand">MythosForge</div>
          <nav className="mf-nav">
            <Link to="/">我的作品</Link>
            <Link to="/projects/new">新建作品</Link>
            <Link to="/books/dual">双书对照</Link>
          </nav>
        </header>
      )}
      <Routes>
        <Route path="/" element={<ProjectListPage />} />
        <Route path="/books/dual" element={<DualBooksPage />} />
        <Route path="/projects/new" element={<ProjectCreatePage />} />
        <Route path="/projects/:projectId" element={<ProjectDetailPage />} />
        <Route path="/projects/:projectId/setup" element={<SetupStudioPage />} />
        <Route path="/projects/:projectId/story/init" element={<StoryInitPage />} />
        <Route path="/projects/:projectId/graph" element={<GraphPage />} />
        <Route path="/projects/:projectId/workflow" element={<WorkflowDagPage />} />
        <Route path="/projects/:projectId/roi" element={<ProjectRoiPage />} />
        <Route path="/projects/:projectId/governance" element={<GovernancePage />} />
        <Route path="/projects/:projectId/chapters/:chapterNo/workspace" element={<ChapterWorkspacePage />} />
      </Routes>
    </div>
  );
}
