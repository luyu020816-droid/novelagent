"""章节生成 LangGraph：Curator→Planner→Budget→Ghostwriter↔Critic；通过后 Stylist；定稿摘要由 Java accept 调 summarize。"""

from __future__ import annotations

from functools import partial

from langgraph.graph import END, START, StateGraph

from app.nodes.budget_node import budget_node
from app.nodes.context_curator_node import context_curator_node
from app.nodes.critic_node import critic_node
from app.nodes.decision_gate_node import decision_gate_node
from app.nodes.ghostwriter_node import ghostwriter_node
from app.nodes.planner_node import planner_node
from app.nodes.stylist_node import stylist_node
from app.schemas.graph_state import ChapterGraphState
from app.services.llm_gateway import LLMGateway

CHAPTER_NODE_NAMES: frozenset[str] = frozenset(
    {
        "context_curator",
        "planner",
        "budget",
        "ghostwriter",
        "critic",
        "decision_gate",
        "bump_retry",
        "stylist",
    }
)


def bump_retry_node(state: dict) -> dict:
    n = int(state.get("retry_count") or 0)
    return {"retry_count": n + 1}


def should_continue(state: dict) -> str:
    if state.get("accepted"):
        return "stylist"
    if int(state.get("retry_count") or 0) < 3:
        return "retry"
    return "end"


def build_chapter_graph(gateway: LLMGateway) -> StateGraph:
    g = StateGraph(ChapterGraphState)
    g.add_node("context_curator", partial(context_curator_node, gateway=gateway))
    g.add_node("planner", partial(planner_node, gateway=gateway))
    g.add_node("budget", budget_node)
    g.add_node("ghostwriter", partial(ghostwriter_node, gateway=gateway))
    g.add_node("critic", partial(critic_node, gateway=gateway))
    g.add_node("decision_gate", decision_gate_node)
    g.add_node("bump_retry", bump_retry_node)
    g.add_node("stylist", partial(stylist_node, gateway=gateway))
    g.add_edge(START, "context_curator")
    g.add_edge("context_curator", "planner")
    g.add_edge("planner", "budget")
    g.add_edge("budget", "ghostwriter")
    g.add_edge("ghostwriter", "critic")
    g.add_edge("critic", "decision_gate")
    g.add_conditional_edges(
        "decision_gate",
        should_continue,
        {"stylist": "stylist", "retry": "bump_retry", "end": END},
    )
    g.add_edge("bump_retry", "ghostwriter")
    g.add_edge("stylist", END)
    return g
