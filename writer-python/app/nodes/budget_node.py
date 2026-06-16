"""Day 11：在 Ghostwriter 前按 Token 预算裁剪 context_pack。"""

from __future__ import annotations

import logging
from typing import Any

from app.config import get_settings
from app.graph.sse_context import get_chapter_sse_emit
from app.schemas.context_pack_item import ContextPackItem
from app.services.token_budget_service import items_to_context_pack, trim_pack_items

logger = logging.getLogger(__name__)


def budget_node(state: dict[str, Any]) -> dict[str, Any]:
    settings = get_settings()
    budget = max(
        4096,
        int(settings.llm_context_window_tokens * settings.llm_context_input_fraction),
    )
    raw = state.get("context_pack_items") or []
    items: list[ContextPackItem] = []
    for x in raw:
        if isinstance(x, ContextPackItem):
            items.append(x)
        elif isinstance(x, dict):
            items.append(ContextPackItem.model_validate(x))

    trimmed, status = trim_pack_items(items, budget)
    new_pack = items_to_context_pack(trimmed)
    # 兜底：若条目未纳入 pack_items，从 Curator 原包保留叙事侧车字段
    orig = state.get("context_pack")
    if isinstance(orig, dict):
        for key in (
            "narrative_obligations",
            "narrative_prompt_lines",
            "story_phase_rules",
            "previously_on",
            "continuity_brief",
            "story_anchor",
            "scars_and_motivations",
            "debt_due",
            "causal_chains",
            "active_entity_memory",
            "narrative_graph_recall",
            "memory_engine",
            "fact_lock",
            "completed_beats_lock",
            "revealed_clues",
            "anti_ai_protocol",
            "beat_sheet_hints",
        ):
            if key not in new_pack and orig.get(key) is not None:
                new_pack[key] = orig[key]
    pid = state.get("project_id")
    ch = state.get("chapter_no")
    if pid is not None:
        new_pack["project_id"] = new_pack.get("project_id") or pid
    if ch is not None:
        new_pack["chapter_no"] = new_pack.get("chapter_no", ch)

    serial = [x.model_dump() for x in trimmed]
    try:
        get_chapter_sse_emit()(
            "artifact",
            {"kind": "token_budget_meta", "data": status},
        )
    except Exception:
        pass

    logger.info("[BudgetNode] budget=%s status=%s", budget, status)
    return {
        "context_pack": new_pack,
        "context_pack_items": serial,
        "token_budget_status": status,
    }
