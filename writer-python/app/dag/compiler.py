"""DAG 编译与强校验。"""

from __future__ import annotations

import logging
from collections import deque
from typing import Any

from langgraph.graph import END, START, StateGraph

from app.dag.models import DAGDefinition, EdgeCondition
from app.dag.registry import NodeRegistry
from app.schemas.graph_state import ChapterGraphState
from app.services.llm_gateway import LLMGateway

logger = logging.getLogger(__name__)

GATEWAY_CONDITIONAL_IDS = frozenset({"gw_circuit"})

REQUIRED_NODE_TYPES = frozenset(
    {
        "ctx_assemble",
        "exec_writer",
        "val_narrative",
        "gw_circuit",
        "gw_retry",
        "exec_polish",
    }
)


def _route_after_circuit(state: dict[str, Any]) -> str:
    if state.get("accepted"):
        return "exec_polish"
    if int(state.get("retry_count") or 0) < 3:
        return "gw_retry"
    return "end"


def validate_dag(dag: DAGDefinition) -> list[str]:
    errors: list[str] = []
    NodeRegistry.ensure_builtins_loaded()
    enabled = [n for n in dag.nodes if n.enabled]
    ids = {n.id for n in enabled}
    types = {n.type for n in enabled}

    for n in enabled:
        if not NodeRegistry.has(n.type):
            errors.append(f"节点 {n.id} 类型未注册: {n.type}")

    for e in dag.edges:
        src = dag.get_node(e.source)
        tgt = dag.get_node(e.target)
        if not src or not tgt or not src.enabled or not tgt.enabled:
            continue
        if e.source not in ids or e.target not in ids:
            errors.append(f"边 {e.id} 引用未启用节点: {e.source} -> {e.target}")

    entries = dag.get_entry_nodes()
    if not entries:
        errors.append("无入口节点")
    elif len(entries) > 1:
        errors.append(f"入口节点应为 1 个，当前 {len(entries)}: {[n.id for n in entries]}")

    missing_types = REQUIRED_NODE_TYPES - types
    if missing_types:
        errors.append("缺少必需节点类型: " + ", ".join(sorted(missing_types)))

    if "gw_circuit" in ids:
        succ = dag.get_successors("gw_circuit")
        if not succ:
            errors.append("gw_circuit 缺少出边（需 exec_polish / gw_retry）")

    if entries:
        start_id = entries[0].id
        reachable: set[str] = set()
        q = deque([start_id])
        while q:
            cur = q.popleft()
            if cur in reachable:
                continue
            reachable.add(cur)
            for t in dag.get_successors(cur):
                if t in ids:
                    q.append(t)
        unreachable = ids - reachable
        if unreachable:
            errors.append("不可达节点: " + ", ".join(sorted(unreachable)))

    if "exec_writer" in ids and "gw_retry" in ids:
        if "exec_writer" not in reachable and entries:
            errors.append("exec_writer 从入口不可达")

    return errors


def compile_dag_to_langgraph(
    dag: DAGDefinition,
    *,
    gateway: LLMGateway,
) -> StateGraph:
    errs = validate_dag(dag)
    if errs:
        raise ValueError("DAG 校验失败: " + "; ".join(errs))

    g = StateGraph(ChapterGraphState)
    enabled = [n for n in dag.nodes if n.enabled]
    enabled_ids = {n.id for n in enabled}

    for node_def in enabled:
        fn = NodeRegistry.create_executor(
            node_def.type, node_def.id, node_def.config, gateway=gateway
        )
        g.add_node(node_def.id, fn)

    entries = dag.get_entry_nodes()
    entry_id = entries[0].id
    g.add_edge(START, entry_id)

    conditional_sources: set[str] = set()
    for edge in dag.edges:
        src = dag.get_node(edge.source)
        tgt = dag.get_node(edge.target)
        if not src or not tgt or not src.enabled or not tgt.enabled:
            continue
        if edge.source in GATEWAY_CONDITIONAL_IDS:
            if edge.source not in conditional_sources:
                conditional_sources.add(edge.source)
                g.add_conditional_edges(
                    edge.source,
                    _route_after_circuit,
                    {"exec_polish": "exec_polish", "gw_retry": "gw_retry", "end": END},
                )
            continue
        if edge.source in conditional_sources:
            continue
        g.add_edge(edge.source, edge.target)

    if "gw_retry" in enabled_ids and "exec_writer" in enabled_ids:
        g.add_edge("gw_retry", "exec_writer")

    for nid in enabled_ids:
        if nid in conditional_sources:
            continue
        if not dag.get_successors(nid) and nid != "gw_retry":
            g.add_edge(nid, END)

    return g


def build_chapter_graph_from_dag(
    gateway: LLMGateway,
    dag: DAGDefinition | None = None,
) -> StateGraph:
    from app.dag.defaults import get_default_dag

    definition = dag or get_default_dag()
    return compile_dag_to_langgraph(definition, gateway=gateway)
