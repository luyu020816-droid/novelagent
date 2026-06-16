import { useMemo, useState } from "react";
import type { LoreRelationshipRow, LoreSnapshot } from "../api/lore";

type Props = {
  data: LoreSnapshot | null;
  error: string | null;
  loading: boolean;
};

type EdgeMeta = {
  key: string;
  from: string;
  to: string;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  kinds: string[];
  evidences: string[];
};

function pickStr(row: Record<string, unknown>, keys: string[]): string {
  for (const k of keys) {
    const v = row[k];
    if (typeof v === "string" && v.trim()) return v.trim();
  }
  return "";
}

function relRow(r: LoreRelationshipRow): { from: string; to: string; kind: string; evidence: string } {
  const o = r as unknown as Record<string, unknown>;
  return {
    from: pickStr(o, ["from_name", "fromName"]),
    to: pickStr(o, ["to_name", "toName"]),
    kind: pickStr(o, ["kind"]),
    evidence: pickStr(o, ["evidence"]),
  };
}

/** 右栏小型关系图：人物为点，关系为线；点击粗线区域查看关系说明 */
export default function LoreMiniGraph({ data, error, loading }: Props) {
  const [picked, setPicked] = useState<EdgeMeta | null>(null);

  const layout = useMemo(() => {
    if (!data) return null;

    const names = new Set<string>();
    for (const c of data.characters ?? []) {
      const o = c as unknown as Record<string, unknown>;
      const n = pickStr(o, ["name"]);
      if (n) names.add(n);
    }
    const rawEdges: { from: string; to: string; kind: string; evidence: string }[] = [];
    for (const r of data.relationships ?? []) {
      const e = relRow(r);
      if (e.from && e.to) {
        rawEdges.push(e);
        names.add(e.from);
        names.add(e.to);
      }
    }

    const nodes = [...names].slice(0, 24);
    if (nodes.length === 0) {
      return { empty: true as const, neo4j: data.neo4j_enabled };
    }

    const w = 280;
    const h = 160;
    const cx = w / 2;
    const cy = h / 2;
    const rad = Math.min(w, h) * 0.36;
    const pos = new Map<string, { x: number; y: number }>();
    nodes.forEach((name, i) => {
      const ang = (2 * Math.PI * i) / nodes.length - Math.PI / 2;
      pos.set(name, { x: cx + rad * Math.cos(ang), y: cy + rad * Math.sin(ang) });
    });

    const byKey = new Map<string, EdgeMeta>();
    for (const e of rawEdges) {
      const a = pos.get(e.from);
      const b = pos.get(e.to);
      if (!a || !b) continue;
      const key = [e.from, e.to].sort().join("|");
      let m = byKey.get(key);
      if (!m) {
        m = {
          key,
          from: e.from,
          to: e.to,
          x1: a.x,
          y1: a.y,
          x2: b.x,
          y2: b.y,
          kinds: [],
          evidences: [],
        };
        byKey.set(key, m);
      }
      if (e.kind && !m.kinds.includes(e.kind)) m.kinds.push(e.kind);
      if (e.evidence && !m.evidences.includes(e.evidence)) m.evidences.push(e.evidence);
    }

    return { empty: false as const, edges: [...byKey.values()], nodes, pos, w, h };
  }, [data]);

  if (loading) {
    return <div style={{ fontSize: 12, color: "#64748b", padding: "8px 0" }}>加载图谱…</div>;
  }
  if (error) {
    return (
      <div style={{ fontSize: 12, color: "#b91c1c", padding: "4px 0" }} title={error}>
        图谱暂不可用
      </div>
    );
  }
  if (!data) {
    return <div style={{ fontSize: 12, color: "#64748b", padding: "6px 0" }}>暂无图谱数据</div>;
  }
  if (!layout || layout.empty) {
    return (
      <div style={{ fontSize: 12, color: "#64748b", padding: "6px 0" }}>
        {data?.neo4j_enabled === false
          ? "世界观存储未就绪或尚无摘录。定稿章节后将逐步累积人物与关系。"
          : "暂无人物节点。接受定稿后会在写作中累积。"}
      </div>
    );
  }

  const { edges, nodes, pos, w, h } = layout;

  return (
    <div style={{ display: "flex", flexDirection: "column", flex: 1, minHeight: 0 }}>
      <svg
        width="100%"
        height="100%"
        viewBox={`0 0 ${w} ${h}`}
        preserveAspectRatio="xMidYMid meet"
        style={{ display: "block", flex: 1, minHeight: 0, cursor: picked ? "default" : undefined }}
        onClick={(ev) => {
          if (ev.target === ev.currentTarget) setPicked(null);
        }}
      >
        <rect width={w} height={h} fill="#f1f5f9" rx={8} />
        {edges.map((ln) => (
          <g key={ln.key}>
            <line
              x1={ln.x1}
              y1={ln.y1}
              x2={ln.x2}
              y2={ln.y2}
              stroke="transparent"
              strokeWidth={14}
              style={{ cursor: "pointer" }}
              onClick={(e) => {
                e.stopPropagation();
                setPicked(ln);
              }}
            />
            <line
              x1={ln.x1}
              y1={ln.y1}
              x2={ln.x2}
              y2={ln.y2}
              stroke={picked?.key === ln.key ? "#6366f1" : "#94a3b8"}
              strokeWidth={picked?.key === ln.key ? 2.2 : 1.25}
              strokeOpacity={0.9}
              pointerEvents="none"
            />
          </g>
        ))}
        {nodes.map((name) => {
          const p = pos.get(name)!;
          return (
            <g key={name}>
              <circle cx={p.x} cy={p.y} r={10} fill="#e0e7ff" stroke="#6366f1" strokeWidth={1.5} />
              <text
                x={p.x}
                y={p.y + 22}
                textAnchor="middle"
                fontSize={9}
                fill="#334155"
                style={{ pointerEvents: "none" }}
              >
                {name.length > 5 ? `${name.slice(0, 4)}…` : name}
              </text>
            </g>
          );
        })}
      </svg>
      {picked && (
        <div
          style={{
            flexShrink: 0,
            fontSize: 11,
            lineHeight: 1.45,
            padding: "6px 8px",
            marginTop: 4,
            background: "#fff",
            border: "1px solid #c7d2fe",
            borderRadius: 8,
            maxHeight: 72,
            overflow: "auto",
          }}
        >
          <div style={{ fontWeight: 600, color: "#312e81", marginBottom: 2 }}>
            {picked.from} → {picked.to}
            {picked.kinds.length > 0 ? <span style={{ fontWeight: 500, color: "#475569" }}> · {picked.kinds.join("、")}</span> : null}
          </div>
          {picked.evidences.length > 0 ? (
            <div style={{ color: "#64748b", fontSize: 10 }}>{picked.evidences[0].slice(0, 280)}{picked.evidences[0].length > 280 ? "…" : ""}</div>
          ) : (
            <div style={{ color: "#94a3b8", fontSize: 10 }}>暂无摘录说明</div>
          )}
          <button
            type="button"
            onClick={() => setPicked(null)}
            style={{
              marginTop: 4,
              fontSize: 10,
              padding: "2px 8px",
              borderRadius: 6,
              border: "1px solid #e2e8f0",
              background: "#f8fafc",
              cursor: "pointer",
            }}
          >
            关闭
          </button>
        </div>
      )}
    </div>
  );
}
