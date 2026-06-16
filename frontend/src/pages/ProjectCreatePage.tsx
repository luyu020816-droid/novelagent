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
    <section className="mf-page mf-prose">
      <Link to="/" className="mf-back">
        ← 返回作品列表
      </Link>
      <h1 className="mf-page-title">新建作品</h1>
      <p className="mf-page-lede">为一部小说创建工程空间；之后可在作品页配置题材、初始化与章节写作。</p>

      <div className="mf-card mf-card-pad" style={{ maxWidth: 440 }}>
        <form onSubmit={onSubmit} style={{ display: "flex", flexDirection: "column", gap: 16 }}>
          <div className="mf-field" style={{ marginBottom: 0 }}>
            <label className="mf-label" htmlFor="project-name">
              作品名称
            </label>
            <input
              id="project-name"
              className="mf-input"
              value={name}
              onChange={(e) => setName(e.target.value)}
              required
              placeholder="例如：长安夜行录"
              autoComplete="off"
            />
          </div>
          <div style={{ display: "flex", gap: 10, flexWrap: "wrap" }}>
            <button type="submit" className="mf-btn mf-btn-primary">
              创建并进入列表
            </button>
            <Link to="/" className="mf-btn" style={{ textDecoration: "none" }}>
              取消
            </Link>
          </div>
        </form>
      </div>
      {err && (
        <p className="mf-alert mf-alert-error" style={{ marginTop: 16, maxWidth: 440 }} role="alert">
          {err}
        </p>
      )}
    </section>
  );
}
