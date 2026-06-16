"""PlotPilot 式反哺：PREVIOUSLY_ON + 与 PG 任务单衔接的连续性提示。"""

from __future__ import annotations

import json
from typing import Any


def _chapter_no(entry: dict[str, Any]) -> int:
    v = entry.get("chapterNo", entry.get("chapter_no"))
    try:
        return int(v) if v is not None else 0
    except (TypeError, ValueError):
        return 0


def _summary_text(entry: dict[str, Any]) -> str:
    s = entry.get("summary")
    if isinstance(s, str) and s.strip():
        return s.strip()
    if isinstance(s, dict):
        for key in ("oneLiner", "one_liner", "plotBeat", "plot_beat", "text", "body"):
            v = s.get(key)
            if isinstance(v, str) and v.strip():
                return v.strip()
        return json.dumps(s, ensure_ascii=False)[:800]
    return ""


def build_previously_on(
    history_summaries: list[Any],
    *,
    chapter_no: int,
    max_chapters: int | None = None,
    max_chars: int | None = None,
) -> str:
    from app.services.memory_engine_constants import (
        PREVIOUSLY_ON_MAX_CHARS,
        PREVIOUSLY_ON_MAX_CHAPTERS,
    )

    if max_chapters is None:
        max_chapters = PREVIOUSLY_ON_MAX_CHAPTERS
    if max_chars is None:
        max_chars = PREVIOUSLY_ON_MAX_CHARS
    """从已定稿章摘要拼「前情提要」块，供 Ghostwriter 承接因果与人物状态。"""
    if not history_summaries:
        return ""
    rows: list[tuple[int, str]] = []
    for raw in history_summaries:
        if not isinstance(raw, dict):
            continue
        cn = _chapter_no(raw)
        if cn <= 0 or cn >= chapter_no:
            continue
        text = _summary_text(raw)
        if text:
            rows.append((cn, text))
    if not rows:
        return ""
    rows.sort(key=lambda x: x[0])
    tail = rows[-max_chapters:]
    lines = ["━━━ 前情提要（已定稿章节）━━━"]
    for cn, text in tail:
        lines.append(f"第{cn}章：{text[:500]}")
    blob = "\n".join(lines)
    return blob[:max_chars]


def merge_continuity_into_pack(pack: dict[str, Any], obligations: dict[str, Any] | None) -> None:
    """将 PG 任务单中的 continuityBrief 写入 context_pack（若 Curator 尚未写入）。"""
    if not isinstance(obligations, dict) or not obligations:
        return
    brief = obligations.get("continuityBrief") or obligations.get("continuity_brief")
    if isinstance(brief, str) and brief.strip() and not pack.get("continuity_brief"):
        pack["continuity_brief"] = brief.strip()[:6000]


def build_active_entity_memory(neo4j_recall: dict[str, Any] | None, *, max_chars: int = 2000) -> str:
    """PlotPilot ACTIVE_ENTITY_MEMORY：活跃实体 + 近期事件 + 未回收伏笔（来自 Neo4j 召回）。"""
    if not isinstance(neo4j_recall, dict) or not neo4j_recall:
        return ""
    lines = ["【🧠 活跃实体记忆（图谱召回）】"]
    nodes = neo4j_recall.get("nodes_touched") or []
    for n in nodes[:8]:
        if not isinstance(n, dict):
            continue
        name = n.get("name") or "?"
        hint = n.get("role_hint") or n.get("evidence") or ""
        lines.append(f"- 人物：{name}" + (f"（{str(hint)[:80]}）" if hint else ""))
    events = neo4j_recall.get("recent_events") or []
    for ev in events[:6]:
        if not isinstance(ev, dict):
            continue
        ch = ev.get("chapter_no")
        summ = ev.get("summary") or ev.get("evidence") or ""
        if summ:
            lines.append(f"- 事件[第{ch}章]：{str(summ)[:120]}")
    fss = neo4j_recall.get("open_foreshadowing") or []
    for fs in fss[:5]:
        if not isinstance(fs, dict):
            continue
        text = fs.get("text") or ""
        if text:
            lines.append(f"- 未回收伏笔：{str(text)[:100]}")
    if len(lines) <= 1:
        return ""
    return "\n".join(lines)[:max_chars]


def merge_plotpilot_slots(pack: dict[str, Any], obligations: dict[str, Any] | None) -> None:
    """将 Java 任务单中的 A/B 槽写入 context_pack。"""
    if not isinstance(obligations, dict) or not obligations:
        return
    merge_continuity_into_pack(pack, obligations)
    for src, dst in (
        ("storyAnchor", "story_anchor"),
        ("scarsAndMotivations", "scars_and_motivations"),
        ("debtDueBlock", "debt_due"),
        ("causalChainsBlock", "causal_chains"),
    ):
        val = obligations.get(src) or obligations.get(dst)
        if isinstance(val, str) and val.strip() and not pack.get(dst):
            pack[dst] = val.strip()[:8000]
