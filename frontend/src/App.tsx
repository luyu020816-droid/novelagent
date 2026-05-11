import { Link, Route, Routes } from "react-router-dom";
import ProjectCreatePage from "./pages/ProjectCreatePage";
import ProjectDetailPage from "./pages/ProjectDetailPage";
import ProjectListPage from "./pages/ProjectListPage";
import ChapterWorkspacePage from "./pages/ChapterWorkspacePage";
import StoryInitPage from "./pages/StoryInitPage";
import GraphPage from "./pages/GraphPage";
import ProjectRoiPage from "./pages/ProjectRoiPage";
import GovernancePage from "./pages/GovernancePage";
import DualBooksPage from "./pages/DualBooksPage";

export default function App() {
  return (
    <div
      style={{
        fontFamily: 'system-ui, "Segoe UI", sans-serif',
        maxWidth: 1120,
        margin: "0 auto",
        padding: "20px 18px 48px",
        minHeight: "100vh",
        background: "#f8f9fb",
        color: "#1a1a1a",
      }}
    >
      <header
        style={{
          marginBottom: 28,
          paddingBottom: 16,
          borderBottom: "1px solid #e2e5ea",
          background: "#fff",
          marginLeft: -18,
          marginRight: -18,
          paddingLeft: 18,
          paddingRight: 18,
          paddingTop: 8,
        }}
      >
        <strong style={{ fontSize: 18, letterSpacing: "-0.02em" }}>MythosForge</strong>
        <nav style={{ marginTop: 10, display: "flex", flexWrap: "wrap", gap: 20, fontSize: 14 }}>
          <Link to="/" style={{ color: "#2563eb", textDecoration: "none" }}>
            我的作品
          </Link>
          <Link to="/projects/new" style={{ color: "#2563eb", textDecoration: "none" }}>
            新建作品
          </Link>
          <Link to="/books/dual" style={{ color: "#2563eb", textDecoration: "none" }}>
            双书对照
          </Link>
        </nav>
      </header>
      <Routes>
        <Route path="/" element={<ProjectListPage />} />
        <Route path="/books/dual" element={<DualBooksPage />} />
        <Route path="/projects/new" element={<ProjectCreatePage />} />
        <Route path="/projects/:projectId" element={<ProjectDetailPage />} />
        <Route path="/projects/:projectId/story/init" element={<StoryInitPage />} />
        <Route path="/projects/:projectId/graph" element={<GraphPage />} />
        <Route path="/projects/:projectId/roi" element={<ProjectRoiPage />} />
        <Route path="/projects/:projectId/governance" element={<GovernancePage />} />
        <Route path="/projects/:projectId/chapters/:chapterNo/workspace" element={<ChapterWorkspacePage />} />
      </Routes>
    </div>
  );
}
