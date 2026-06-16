"""PlotPilot MemoryEngine 简化版：从已定稿摘要生成 FACT_LOCK / COMPLETED_BEATS / REVEALED_CLUES。"""

from __future__ import annotations

import json
import re
from typing import Any


def _chapter_no(entry: dict[str, Any]) -> int:
    v = entry.get("chapterNo", entry.get("chapter_no"))
    try:
        return int(v) if v is not None else 0
    except (TypeError, ValueError):
        return 0


def _summary_obj(entry: dict[str, Any]) -> dict[str, Any]:
    s = entry.get("summary")
    return s if isinstance(s, dict) else {}


def build_memory_engine_blocks(
    history_summaries: list[Any],
    *,
    chapter_no: int,
    story_contract: dict[str, Any] | None = None,
    max_chars: int | None = None,
) -> dict[str, str]:
    from app.services.memory_engine_constants import (
        MEMORY_ENGINE_HISTORY_CHAPTERS,
        MEMORY_ENGINE_MAX_CHARS,
        MEMORY_ENGINE_PACK_MAX_CHARS,
    )

    if max_chars is None:
        max_chars = MEMORY_ENGINE_MAX_CHARS
    history_window = MEMORY_ENGINE_HISTORY_CHAPTERS
    """返回可注入 context_pack 的三段文本块。"""
    facts: list[str] = []
    beats: list[str] = []
    clues: list[str] = []

    sc = story_contract if isinstance(story_contract, dict) else {}
    mrf = sc.get("mustRetainFacts") or sc.get("must_retain_facts")
    if isinstance(mrf, list):
        for x in mrf[:12]:
            if isinstance(x, str) and x.strip():
                facts.append(x.strip()[:200])

    rows: list[tuple[int, dict[str, Any]]] = []
    for raw in history_summaries:
        if not isinstance(raw, dict):
            continue
        cn = _chapter_no(raw)
        if cn <= 0 or cn >= chapter_no:
            continue
        rows.append((cn, _summary_obj(raw)))
    rows.sort(key=lambda x: x[0])

    for cn, sm in rows[-history_window:]:
        for ev in sm.get("key_events") or []:
            if isinstance(ev, str) and ev.strip():
                beats.append(f"第{cn}章：{ev.strip()[:120]}")
        cs = sm.get("character_state")
        if isinstance(cs, str) and cs.strip():
            facts.append(f"第{cn}章人物态：{cs.strip()[:160]}")
        for fg in sm.get("pending_foreshadowing") or []:
            if isinstance(fg, str) and fg.strip():
                clues.append(fg.strip()[:120])
            elif isinstance(fg, dict):
                t = fg.get("text") or fg.get("question") or ""
                if isinstance(t, str) and t.strip():
                    clues.append(t.strip()[:120])
        for cb in sm.get("completed_beats") or []:
            if isinstance(cb, str) and cb.strip():
                beats.append(f"✓{cn} {cb.strip()[:100]}")

    prot = sc.get("protagonist") or {}
    if isinstance(prot, dict):
        name = prot.get("name") or prot.get("title")
        if isinstance(name, str) and name.strip():
            facts.insert(0, f"主角：{name.strip()}")

    def _block(title: str, lines: list[str], limit: int) -> str:
        if not lines:
            return ""
        uniq: list[str] = []
        seen: set[str] = set()
        for ln in lines:
            k = ln[:80]
            if k in seen:
                continue
            seen.add(k)
            uniq.append(ln)
        body = "\n".join(f"- {x}" for x in uniq[:limit])
        return f"【{title}】\n{body}"

    fact_lock = _block("FACT_LOCK 不可违背", facts, 14)
    completed = _block("COMPLETED_BEATS 已定稿节拍", beats, 16)
    revealed = _block("REVEALED_CLUES 未回收线索", clues, 12)

    out: dict[str, str] = {}
    if fact_lock:
        out["fact_lock"] = fact_lock[:max_chars]
    if completed:
        out["completed_beats"] = completed[:max_chars]
    if revealed:
        out["revealed_clues"] = revealed[:max_chars]
    return out


def format_memory_engine_for_pack(blocks: dict[str, str]) -> str:
    from app.services.memory_engine_constants import MEMORY_ENGINE_PACK_MAX_CHARS

    parts = [blocks[k] for k in ("fact_lock", "completed_beats", "revealed_clues") if blocks.get(k)]
    return "\n\n".join(parts)[:MEMORY_ENGINE_PACK_MAX_CHARS]
