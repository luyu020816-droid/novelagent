from __future__ import annotations

from app.graph.sse_context import get_chapter_sse_emit


def sse_llm_delta(node: str, text: str) -> None:
    get_chapter_sse_emit()("llm_delta", {"node": node, "text": text})
