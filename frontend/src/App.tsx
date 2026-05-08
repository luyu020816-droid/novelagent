import { Link, Route, Routes } from "react-router-dom";
import ProjectCreatePage from "./pages/ProjectCreatePage";
import ProjectDetailPage from "./pages/ProjectDetailPage";
import ProjectListPage from "./pages/ProjectListPage";

export default function App() {
  return (
    <div style={{ fontFamily: "system-ui", maxWidth: 720, margin: "0 auto", padding: 16 }}>
      <header style={{ marginBottom: 24 }}>
        <strong>MythosForge</strong>
        <nav style={{ marginTop: 8, display: "flex", gap: 16 }}>
          <Link to="/">项目列表</Link>
          <Link to="/projects/new">创建项目</Link>
        </nav>
      </header>
      <Routes>
        <Route path="/" element={<ProjectListPage />} />
        <Route path="/projects/new" element={<ProjectCreatePage />} />
        <Route path="/projects/:projectId" element={<ProjectDetailPage />} />
      </Routes>
    </div>
  );
}
