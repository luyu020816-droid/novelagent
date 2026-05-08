import { FormEvent, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { createProject } from "../api/projects";

export default function ProjectCreatePage() {
  const nav = useNavigate();
  const [name, setName] = useState("");
  const [err, setErr] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setErr(null);
    try {
      await createProject({ name });
      nav("/");
    } catch (ex) {
      setErr(ex instanceof Error ? ex.message : String(ex));
    }
  }

  return (
    <section>
      <h1>创建项目</h1>
      <form onSubmit={onSubmit} style={{ display: "flex", flexDirection: "column", gap: 12, maxWidth: 400 }}>
        <label>
          名称
          <input
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            style={{ display: "block", width: "100%", marginTop: 4 }}
          />
        </label>
        <button type="submit">保存</button>
      </form>
      {err && <p style={{ color: "crimson" }}>{err}</p>}
      <p>
        <Link to="/">返回列表</Link>
      </p>
    </section>
  );
}
