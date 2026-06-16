import { useCallback, useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import {
  Background,
  Controls,
  MiniMap,
  ReactFlow,
  type Connection,
  type Edge,
  type Node,
  addEdge,
  useEdgesState,
  useNodesState,
} from "@xyflow/react";
import "@xyflow/react/dist/style.css";
import {
  getDefaultDagTemplate,
  getProjectDag,
  listDagNodeTypes,
  saveProjectDag,
  scaffoldDagNode,
  validateProjectDag,
  type DagDefinition,
  type DagNodeType,
} from "../api/dag";

const EDGE_CONDITIONS = ["always", "on_accept", "on_reject", "on_retry"] as const;

function dagToFlow(dag: DagDefinition): { nodes: Node[]; edges: Edge[] } {
  const nodes: Node[] = dag.nodes.map((n) => ({
    id: n.id,
    position: { x: n.position?.x ?? 0, y: n.position?.y ?? 0 },
    data: { label: n.label || n.type, type: n.type, enabled: n.enabled !== false },
    type: "default",
    style: n.enabled === false ? { opacity: 0.45 } : undefined,
  }));
  const edges: Edge[] = dag.edges.map((e) => ({
    id: e.id,
    source: e.source,
    target: e.target,
    label: e.condition && e.condition !== "always" ? e.condition : undefined,
    animated: e.condition === "on_reject" || e.condition === "on_retry",
    data: { condition: e.condition ?? "always" },
  }));
  return { nodes, edges };
}

function flowToDag(base: DagDefinition, nodes: Node[], edges: Edge[]): DagDefinition {
  const posById = new Map(nodes.map((n) => [n.id, n.position]));
  const enabledById = new Map(nodes.map((n) => [n.id, n.data?.enabled !== false]));
  const mergedNodes = base.nodes.map((n) => {
    const p = posById.get(n.id);
    const enabled = enabledById.get(n.id);
    return {
      ...n,
      ...(p ? { position: { x: p.x, y: p.y } } : {}),
      ...(enabled !== undefined ? { enabled } : {}),
    };
  });
  for (const n of nodes) {
    if (!mergedNodes.some((x) => x.id === n.id)) {
      mergedNodes.push({
        id: n.id,
        type: String(n.data?.type ?? "generic_llm"),
        label: String(n.data?.label ?? n.id),
        enabled: n.data?.enabled !== false,
        position: { x: n.position.x, y: n.position.y },
      });
    }
  }
  const edgeById = new Map(edges.map((e) => [e.id, e]));
  const mergedEdges = base.edges
    .filter((e) => edgeById.has(e.id))
    .map((e) => {
      const fe = edgeById.get(e.id)!;
      const cond = String(fe.data?.condition ?? fe.label ?? e.condition ?? "always");
      return { ...e, condition: cond };
    });
  for (const e of edges) {
    if (!mergedEdges.some((x) => x.id === e.id)) {
      mergedEdges.push({
        id: e.id,
        source: e.source,
        target: e.target,
        condition: String(e.data?.condition ?? e.label ?? "always"),
      });
    }
  }
  return { ...base, nodes: mergedNodes, edges: mergedEdges };
}

export default function WorkflowDagPage() {
  const { projectId } = useParams<{ projectId: string }>();
  const [baseDag, setBaseDag] = useState<DagDefinition | null>(null);
  const [nodeTypes, setNodeTypes] = useState<DagNodeType[]>([]);
  const [manualTypes, setManualTypes] = useState<string[]>([]);
  const [err, setErr] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);
  const [newDesc, setNewDesc] = useState("");
  const [newId, setNewId] = useState("val_custom");
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null);
  const [nodes, setNodes, onNodesChange] = useNodesState<Node>([]);
  const [edges, setEdges, onEdgesChange] = useEdgesState<Edge>([]);

  const loadDag = useCallback(
    (dag: DagDefinition) => {
      setBaseDag(dag);
      const flow = dagToFlow(dag);
      setNodes(flow.nodes);
      setEdges(flow.edges);
      setSelectedNodeId(null);
      setSelectedEdgeId(null);
    },
    [setNodes, setEdges]
  );

  useEffect(() => {
    if (!projectId) return;
    setErr(null);
    getProjectDag(projectId)
      .then((res) => loadDag(res.dag))
      .catch((e: Error) => setErr(e.message));
    listDagNodeTypes(projectId)
      .then((r) => {
        setNodeTypes(r.types);
        setManualTypes(r.manual_add_types);
      })
      .catch(() => {});
  }, [projectId, loadDag]);

  const onConnect = useCallback(
    (conn: Connection) => {
      const id = `edge_${conn.source}_${conn.target}`;
      setEdges((eds) =>
        addEdge({ ...conn, id, data: { condition: "always" } }, eds)
      );
    },
    [setEdges]
  );

  const dagSnapshot = useMemo(() => {
    if (!baseDag) return null;
    return flowToDag(baseDag, nodes, edges);
  }, [baseDag, nodes, edges]);

  const selectedNode = nodes.find((n) => n.id === selectedNodeId);
  const selectedEdge = edges.find((e) => e.id === selectedEdgeId);

  const toggleNodeEnabled = (nodeId: string, enabled: boolean) => {
    setNodes((ns) =>
      ns.map((n) =>
        n.id === nodeId
          ? {
              ...n,
              data: { ...n.data, enabled },
              style: enabled ? undefined : { opacity: 0.45 },
            }
          : n
      )
    );
    if (baseDag) {
      setBaseDag({
        ...baseDag,
        nodes: baseDag.nodes.map((n) => (n.id === nodeId ? { ...n, enabled } : n)),
      });
    }
  };

  const setEdgeCondition = (edgeId: string, condition: string) => {
    setEdges((es) =>
      es.map((e) =>
        e.id === edgeId
          ? {
              ...e,
              data: { ...e.data, condition },
              label: condition !== "always" ? condition : undefined,
              animated: condition === "on_reject" || condition === "on_retry",
            }
          : e
      )
    );
  };

  const handleSave = async () => {
    if (!projectId || !dagSnapshot) return;
    setErr(null);
    setMsg(null);
    const v = await validateProjectDag(projectId, dagSnapshot);
    if (!v.ok) {
      setErr(v.errors?.join("; ") || "DAG 校验失败");
      return;
    }
    await saveProjectDag(projectId, dagSnapshot, "画布保存");
    setMsg("已保存为本书 active DAG 版本");
  };

  const handleResetDefault = async () => {
    if (!projectId) return;
    setErr(null);
    setMsg(null);
    try {
      const dag = await getDefaultDagTemplate(projectId);
      loadDag(dag);
      setMsg("已恢复系统默认 DAG（未保存，点「校验并保存」生效）");
    } catch (e) {
      setErr(e instanceof Error ? e.message : "加载默认 DAG 失败");
    }
  };

  const handleAddBuiltin = (type: string) => {
    const meta = nodeTypes.find((t) => t.node_type === type);
    const id = `${type}_${nodes.length + 1}`.replace(/[^a-z0-9_]/g, "_");
    setNodes((ns) => [
      ...ns,
      {
        id,
        position: { x: 120 + ns.length * 24, y: 120 + ns.length * 18 },
        data: { label: meta?.display_name ?? type, type, enabled: true },
      },
    ]);
    if (baseDag) {
      setBaseDag({
        ...baseDag,
        nodes: [
          ...baseDag.nodes,
          {
            id,
            type,
            label: meta?.display_name ?? type,
            enabled: true,
            position: { x: 120, y: 120 },
          },
        ],
      });
    }
  };

  const handleScaffold = async () => {
    if (!projectId || !newDesc.trim() || !newId.trim()) return;
    const res = await scaffoldDagNode(projectId, newDesc.trim(), newId.trim());
    const n = res.node;
    setNodes((ns) => [
      ...ns,
      {
        id: n.id,
        position: { x: n.position?.x ?? 200, y: n.position?.y ?? 200 },
        data: { label: n.label || res.meta.display_name, type: n.type, enabled: true },
      },
    ]);
    if (baseDag) {
      setBaseDag({ ...baseDag, nodes: [...baseDag.nodes, n] });
    }
    setMsg(`已生成节点 ${n.id}，请拖到合适位置并连线后保存`);
  };

  if (!projectId) {
    return <p className="mf-alert mf-alert-error">缺少项目 ID</p>;
  }

  return (
    <section className="mf-page">
      <Link to={`/projects/${encodeURIComponent(projectId)}`} className="mf-back">← 返回作品主页</Link>
      <h1 className="mf-page-title">章节流水线（DAG 画布）</h1>
      <p className="mf-page-lede">
        拖拽节点调整布局；点击节点可禁用/启用；选中边可改条件（on_accept / on_reject）。保存前会经 Writer 强校验。
      </p>

      {err && <p className="mf-alert mf-alert-error">{err}</p>}
      {msg && <p className="mf-alert mf-alert-ok">{msg}</p>}

      <div style={{ display: "flex", gap: 16, alignItems: "stretch" }}>
        <div style={{ flex: 1, height: 560, border: "1px solid var(--mf-border)", borderRadius: 8 }}>
          <ReactFlow
            nodes={nodes}
            edges={edges}
            onNodesChange={onNodesChange}
            onEdgesChange={onEdgesChange}
            onConnect={onConnect}
            onNodeClick={(_, n) => {
              setSelectedNodeId(n.id);
              setSelectedEdgeId(null);
            }}
            onEdgeClick={(_, e) => {
              setSelectedEdgeId(e.id);
              setSelectedNodeId(null);
            }}
            fitView
          >
            <MiniMap />
            <Controls />
            <Background />
          </ReactFlow>
        </div>
        <aside style={{ width: 280, fontSize: 14 }}>
          {selectedNode && (
            <div style={{ marginBottom: 16, padding: 8, border: "1px solid var(--mf-border)", borderRadius: 6 }}>
              <strong>{String(selectedNode.data?.label ?? selectedNode.id)}</strong>
              <label style={{ display: "flex", gap: 8, marginTop: 8, alignItems: "center" }}>
                <input
                  type="checkbox"
                  checked={selectedNode.data?.enabled !== false}
                  onChange={(e) => toggleNodeEnabled(selectedNode.id, e.target.checked)}
                />
                启用节点
              </label>
            </div>
          )}
          {selectedEdge && (
            <div style={{ marginBottom: 16, padding: 8, border: "1px solid var(--mf-border)", borderRadius: 6 }}>
              <strong>边 {selectedEdge.id}</strong>
              <select
                className="mf-input"
                style={{ marginTop: 8, width: "100%" }}
                value={String(selectedEdge.data?.condition ?? selectedEdge.label ?? "always")}
                onChange={(e) => setEdgeCondition(selectedEdge.id, e.target.value)}
              >
                {EDGE_CONDITIONS.map((c) => (
                  <option key={c} value={c}>
                    {c}
                  </option>
                ))}
              </select>
            </div>
          )}
          <h2 className="mf-section-title">可添加节点</h2>
          <ul style={{ listStyle: "none", padding: 0, margin: 0 }}>
            {manualTypes.slice(0, 12).map((t) => (
              <li key={t} style={{ marginBottom: 6 }}>
                <button type="button" className="mf-btn mf-btn-secondary mf-btn-sm" onClick={() => handleAddBuiltin(t)}>
                  + {nodeTypes.find((x) => x.node_type === t)?.display_name ?? t}
                </button>
              </li>
            ))}
          </ul>
          <h2 className="mf-section-title" style={{ marginTop: 16 }}>描述生成节点</h2>
          <input className="mf-input" placeholder="节点 id（小写）" value={newId} onChange={(e) => setNewId(e.target.value)} />
          <textarea
            className="mf-input"
            rows={3}
            placeholder="简短描述职责，如：检查师徒关系是否矛盾"
            value={newDesc}
            onChange={(e) => setNewDesc(e.target.value)}
            style={{ marginTop: 8 }}
          />
          <button type="button" className="mf-btn mf-btn-secondary" style={{ marginTop: 8 }} onClick={() => handleScaffold()}>
            生成 generic_llm 节点
          </button>
          <button type="button" className="mf-btn mf-btn-secondary" style={{ marginTop: 12, width: "100%" }} onClick={() => handleResetDefault()}>
            恢复系统默认
          </button>
          <button type="button" className="mf-btn mf-btn-primary" style={{ marginTop: 8, width: "100%" }} onClick={() => handleSave()}>
            校验并保存
          </button>
        </aside>
      </div>
    </section>
  );
}
