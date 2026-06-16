"""单次执行章节 Graph：`astream_events` 驱动 + LLMGateway 通过 ContextVar 发 llm_delta。"""

from __future__ import annotations

import asyncio
from typing import Any, Callable

from app.graph.chapter_graph import CHAPTER_NODE_NAMES, build_chapter_graph
from app.dag.compiler import compile_dag_to_langgraph, validate_dag
from app.dag.models import DAGDefinition
from app.graph.chapter_usage_accumulator import (
    chapter_usage_accumulator_build_summary,
    chapter_usage_accumulator_reset,
    chapter_usage_accumulator_set_active,
)
from app.graph.sse_context import reset_chapter_sse_emit, set_chapter_sse_emit
from app.schemas.graph_state import ChapterGenerationState
from app.services.llm_gateway import LLMGateway


Emit = Callable[[str, dict[str, Any]], None]

_MERGE_KEYS = frozenset(
    {
        "context_pack",
        "context_pack_items",
        "token_budget_status",
        "llm_usage_summary",
        "scene_plan",
        "chapter_text",
        "styled_text",
        "critic_report",
        "accepted",
        "rejected",
        "retry_count",
        "user_rewrite_notes",
        "rewrite_mode",
        "confirmed_chapter_plan_summary",
        "chapter_obligations",
    }
)


def _resolve_graph_node_name(raw: str) -> str | None:
    if not raw:
        return None
    if raw in CHAPTER_NODE_NAMES:
        return raw
    for n in CHAPTER_NODE_NAMES:
        if raw.endswith(n):
            return n
    return None


async def run_chapter_graph_async(initial: dict[str, Any], emit: Emit) -> dict[str, Any]:
    gateway = LLMGateway()
    dag_raw = initial.get("dag_definition") or initial.get("dagDefinition")
    if isinstance(dag_raw, dict) and dag_raw.get("nodes"):
        dag = DAGDefinition.model_validate(dag_raw)
        errs = validate_dag(dag)
        if errs:
            raise ValueError("dagDefinition 无效: " + "; ".join(errs))
        graph = compile_dag_to_langgraph(dag, gateway=gateway)
    else:
        graph = build_chapter_graph(gateway)
    compiled = graph.compile()
    acc: dict[str, Any] = dict(initial)

    def bridging_emit(event: str, payload: dict[str, Any]) -> None:
        emit(event, payload)

    usage_acc_tok = chapter_usage_accumulator_set_active()
    token = set_chapter_sse_emit(bridging_emit)
    # LangGraph 接受与 LangChain RunnableConfig 兼容的 dict；避免强依赖 langchain_core 仅作类型导入。
    config: dict[str, Any] = {}
    try:
        stream_input = dict(initial)
        async for ev in compiled.astream_events(stream_input, version="v2", config=config):
            et = ev.get("event")
            raw_name = str(ev.get("name") or "")
            short = _resolve_graph_node_name(raw_name)
            if et == "on_chain_start" and short:
                emit("node_start", {"node": short})
            elif et == "on_chat_model_stream":
                # 当前流水线使用自建 LLMGateway，一般不会触发；保留映射以兼容未来接入 LangChain ChatModel。
                chunk = (ev.get("data") or {}).get("chunk")
                text = ""
                if chunk is not None:
                    text = getattr(chunk, "content", "") or ""
                    if isinstance(chunk, dict):
                        text = str(chunk.get("content") or "")
                if text:
                    emit("llm_delta", {"node": short or "chat_model", "text": text})
            elif et == "on_chain_end" and short:
                emit("node_end", {"node": short, "ok": True})
                data = ev.get("data") or {}
                out = data.get("output")
                if isinstance(out, dict):
                    for k in _MERGE_KEYS:
                        if k in out:
                            acc[k] = out[k]
                    if short == "planner" and isinstance(out.get("scene_plan"), dict):
                        emit("artifact", {"kind": "scene_plan", "data": out["scene_plan"]})

        acc["llm_usage_summary"] = chapter_usage_accumulator_build_summary()
        validated = ChapterGenerationState.model_validate(acc)
        payload = validated.model_dump()
        emit("artifact", {"kind": "chapter_generation_final", "data": payload})
        emit("done", {"ok": True})
        return payload
    except Exception as e:
        emit("error", {"message": str(e)})
        emit("done", {"ok": False})
        raise
    finally:
        reset_chapter_sse_emit(token)
        chapter_usage_accumulator_reset(usage_acc_tok)


def run_chapter_graph(initial: dict[str, Any], emit: Emit) -> dict[str, Any]:
    """同步入口（SSE worker 线程内调用）。内部用 asyncio 跑 `astream_events`。"""
    return asyncio.run(run_chapter_graph_async(initial, emit))
