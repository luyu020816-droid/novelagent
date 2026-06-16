"""叙事任务单在 Token Budget 裁剪后仍须进入 Ghostwriter context_pack。"""

from __future__ import annotations

import json

from app.schemas.context_pack_item import ContextPackItem
from app.services.token_budget_service import (
    build_pack_items,
    items_to_context_pack,
    trim_pack_items,
)


def test_narrative_fields_survive_aggressive_trim() -> None:
    obligations = {
        "summaryLine": "本章为汇合章（1 处未 resolved）；[收敛期] 禁止新坑",
        "continuityBrief": "【逾期子文本 — 优先回收】\n- 第3章埋设：谁下的手？",
        "storyPhase": "convergence",
        "narrativePromptLines": ["━━━ 故事线上下文（本章）━━━", "● [主线] 「主线」"],
        "phaseRules": {"allowNewSubtext": False, "guidanceLine": "收敛期"},
    }
    items = build_pack_items(
        project_id="p1",
        chapter_no=10,
        story_contract={"title": "测试"},
        chapter_contract={"chapterGoal": "推进"},
        history_summaries=[{"chapterNo": 9, "summary": {"oneLiner": "上一章结局"}}],
        recent_summaries=[],
        vector_context={"keywords": [], "chunks": []},
        user_rewrite_notes="",
        narrative_obligations=obligations,
        narrative_prompt_lines=obligations["narrativePromptLines"],
        story_phase_rules=obligations["phaseRules"],
        previously_on="第9章：上一章结局",
        continuity_brief=obligations["continuityBrief"],
    )
    # 极小预算：应丢弃 vector/history 可选项，但保留 narrative PROTECTED
    trimmed, status = trim_pack_items(items, budget_tokens=8000)
    pack = items_to_context_pack(trimmed)
    assert pack.get("narrative_obligations"), "narrative_obligations missing after trim"
    assert pack.get("narrative_prompt_lines"), "narrative_prompt_lines missing after trim"
    assert pack.get("story_phase_rules"), "story_phase_rules missing after trim"
    assert pack.get("previously_on"), "previously_on missing after trim"
    assert pack.get("continuity_brief"), "continuity_brief missing after trim"
    assert "convergence" in json.dumps(pack.get("narrative_obligations"), ensure_ascii=False)
    assert "narrative_obligations" in status.get("protected_categories_kept", [])


def test_build_pack_items_includes_narrative_categories() -> None:
    items = build_pack_items(
        project_id="p1",
        chapter_no=2,
        story_contract={},
        chapter_contract={},
        history_summaries=[],
        recent_summaries=[],
        vector_context={},
        user_rewrite_notes="",
        narrative_obligations={"summaryLine": "活跃故事线 1 条。"},
    )
    cats = {x.category for x in items}
    assert "narrative_obligations" in cats
    assert "narrative_summary" in cats
