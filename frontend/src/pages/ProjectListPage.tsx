import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listProjects, type Project } from "../api/projects";

export default function ProjectListPage() {
  const [items, setItems] = useState<Project[]>([]);
  const [err, setErr] = useState<string | null>(null);

  useEffect(() => {
    listProjects()
      .then(setItems)
      .catch((e: Error) => setErr(e.message));
  }, []);

  if (err) {
    return <p style={{ color: "crimson" }}>加载失败：{err}</p>;
  }

  return (
    <section>
      <h1>项目列表</h1>
      {items.length === 0 ? (
        <p>暂无项目。<Link to="/projects/new">创建一个</Link></p>
      ) : (
        <ul>
          {items.map((p) => (
            <li key={p.id}>
              <strong>{p.name}</strong> — {p.status} — {p.language}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
