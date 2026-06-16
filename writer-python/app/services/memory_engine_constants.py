"""MemoryEngine-lite 与 Curator / Token Budget 对齐的窗口与槽位常量（改窗口只改此处 + 单测）。"""

from __future__ import annotations

# Curator / narrative_feed_forward
PREVIOUSLY_ON_MAX_CHAPTERS = 3
PREVIOUSLY_ON_MAX_CHARS = 2400
RECENT_SUMMARY_TAIL_CHAPTERS = 3
HISTORY_SUMMARIES_MAX_CHAPTERS = 20

# memory_engine_lite.build_memory_engine_blocks
MEMORY_ENGINE_HISTORY_CHAPTERS = 8
MEMORY_ENGINE_MAX_CHARS = 3200
MEMORY_ENGINE_PACK_MAX_CHARS = 4800

# 注入槽 → token_budget TOKEN_TIER（须与 token_budget_service.TOKEN_TIER_BY_CATEGORY 一致）
MEMORY_SLOT_TIER: dict[str, int] = {
    "memory_engine": 1,
    "fact_lock": 1,
    "completed_beats_lock": 1,
    "revealed_clues": 1,
    "previously_on": 1,
    "recent_summary": 2,
    "history_summary": 2,
}

MIN_SCENE_PLAN_BEATS = 4
MAX_SCENE_PLAN_BEATS = 8
