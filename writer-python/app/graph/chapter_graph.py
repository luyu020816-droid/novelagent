"""章节生成 LangGraph — 由 PlotPilot 风格 DAG 编译（默认 dag_default_chapter）。

定稿后由 Java 调 Writer /chapters/aftermath；见 docs/aftermath-pipeline.md、docs/dag-pipeline.md。
"""

from __future__ import annotations

from langgraph.graph import StateGraph

from app.dag.compiler import build_chapter_graph_from_dag
from app.dag.defaults import get_default_dag
from app.dag.models import DAGDefinition
from app.services.llm_gateway import LLMGateway

# 兼容 SSE node 名解析：实例 id + 旧节点名
CHAPTER_NODE_NAMES: frozenset[str] = frozenset(
    {
        "context_curator",
        "planner",
        "scene_director",
        "budget",
        "ghostwriter",
        "critic",
        "decision_gate",
        "bump_retry",
        "stylist",
        *(n.id for n in get_default_dag().nodes),
        *(n.type for n in get_default_dag().nodes),
    }
)


def build_chapter_graph(
    gateway: LLMGateway,
    dag: DAGDefinition | None = None,
) -> StateGraph:
    """从 DAG 定义编译 LangGraph；未传则使用默认章节全流程。"""
    return build_chapter_graph_from_dag(gateway, dag=dag)


def bump_retry_node(state: dict) -> dict:
    n = int(state.get("retry_count") or 0)
    return {"retry_count": n + 1}


def should_continue(state: dict) -> str:
    if state.get("accepted"):
        return "stylist"
    if int(state.get("retry_count") or 0) < 3:
        return "retry"
    return "end"
