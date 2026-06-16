"""Day 11：Token 估算与上下文预算裁剪（cl100k_base）。"""

from __future__ import annotations

import json
import logging
from typing import Any

import tiktoken

from app.schemas.context_pack_item import ContextPackItem
from app.services.story_canon_service import build_story_canon, shrink_story_canon_json
from app.skills.series_presets import get_series_preset

logger = logging.getLogger(__name__)

# 强制保留类别（超限时可截断 story_contract / chapter_contract / story_canon；不截断 forbidden_moves）
PROTECTED_CATEGORIES: frozenset[str] = frozenset(
    {
        "chapter_contract",
        "story_contract",
        "story_canon",
        "fan_series_digest",
        "forbidden_moves",
        "human_instruction",
        "chapter_plan_confirmed",
        # PG 叙事任务单：Budget 后必须保留，否则 Ghostwriter/Critic 丢失汇合/子文本约束
        "narrative_obligations",
        "narrative_prompt_lines",
        "story_phase_rules",
        "narrative_summary",
        "previously_on",  # tier 1，与 memory_engine_constants.MEMORY_SLOT_TIER 对齐
        "continuity_brief",
        "story_anchor",
        "scars_and_motivations",
        "debt_due",
        "causal_chains",
        "active_entity_memory",
        "memory_engine",
        "fact_lock",
        "anti_ai_protocol",
    }
)

# PlotPilot T0–T3：可选条目裁剪时先丢 T3，再 T2（protected 见 PROTECTED_CATEGORIES）
TOKEN_TIER_BY_CATEGORY: dict[str, int] = {
    "meta_scope": 3,
    "vector_context": 2,
    "relationship_graph": 2,
    "unresolved_events": 2,
    "history_summary": 2,
    "recent_summary": 2,
    "memory_engine": 1,
    "fact_lock": 1,
    "completed_beats_lock": 1,
    "revealed_clues": 1,
    "previously_on": 1,
    "anti_ai_protocol": 1,
}

_ENC: tiktoken.Encoding | None = None


def get_encoding() -> tiktoken.Encoding:
    global _ENC
    if _ENC is None:
        _ENC = tiktoken.get_encoding("cl100k_base")
    return _ENC


def count_tokens(text: str) -> int:
    if not text:
        return 0
    return len(get_encoding().encode(text))


def estimate_item_tokens(content: str) -> int:
    return count_tokens(content)


def truncate_content_to_tokens(content: str, max_tokens: int) -> tuple[str, int]:
    if max_tokens <= 0:
        return "", 0
    enc = get_encoding()
    ids = enc.encode(content)
    if len(ids) <= max_tokens:
        return content, len(ids)
    return enc.decode(ids[:max_tokens]) + "\n…[token_budget_truncated]", max_tokens


def build_pack_items(
    *,
    project_id: str,
    chapter_no: int,
    story_contract: dict[str, Any],
    chapter_contract: dict[str, Any],
    history_summaries: list[dict[str, Any]],
    recent_summaries: list[dict[str, Any]],
    vector_context: dict[str, Any],
    relationship_graph: dict[str, Any] | None = None,
    unresolved_events: dict[str, Any] | None = None,
    user_rewrite_notes: str,
    fan_series_preset: str | None = None,
    confirmed_chapter_plan: str | None = None,
    narrative_obligations: dict[str, Any] | None = None,
    narrative_prompt_lines: list[str] | None = None,
    story_phase_rules: dict[str, Any] | None = None,
    previously_on: str | None = None,
    continuity_brief: str | None = None,
    story_anchor: str | None = None,
    scars_and_motivations: str | None = None,
    debt_due: str | None = None,
    causal_chains: str | None = None,
    active_entity_memory: str | None = None,
    memory_engine: str | None = None,
    fact_lock: str | None = None,
    anti_ai_protocol: str | None = None,
    beat_sheet_hints: list[str] | None = None,
) -> list[ContextPackItem]:
    """将 curator 各类上下文拆成带优先级的条目（越远的历史摘要 priority 越低）。"""
    items: list[ContextPackItem] = []

    co = narrative_obligations if isinstance(narrative_obligations, dict) else {}
    summary = co.get("summaryLine") or co.get("summary_line")
    if isinstance(summary, str) and summary.strip():
        c = summary.strip()[:4000]
        items.append(
            ContextPackItem(
                category="narrative_summary",
                content=c,
                priority=12,
                estimated_tokens=estimate_item_tokens(c),
            )
        )
    brief = (continuity_brief or co.get("continuityBrief") or co.get("continuity_brief") or "").strip()
    if not brief and isinstance(co.get("continuityBrief"), str):
        brief = str(co.get("continuityBrief")).strip()
    if brief:
        c = brief[:6000]
        items.append(
            ContextPackItem(
                category="continuity_brief",
                content=c,
                priority=12,
                estimated_tokens=estimate_item_tokens(c),
            )
        )
    prev = (previously_on or "").strip()
    if prev:
        c = prev[:6000]
        items.append(
            ContextPackItem(
                category="previously_on",
                content=c,
                priority=12,
                estimated_tokens=estimate_item_tokens(c),
            )
        )
    lines = narrative_prompt_lines
    if lines is None:
        raw_lines = co.get("narrativePromptLines") or co.get("narrative_prompt_lines")
        lines = raw_lines if isinstance(raw_lines, list) else None
    if isinstance(lines, list) and lines:
        blob = "\n".join(str(x).strip() for x in lines if x and str(x).strip())[:12000]
        if blob:
            items.append(
                ContextPackItem(
                    category="narrative_prompt_lines",
                    content=blob,
                    priority=12,
                    estimated_tokens=estimate_item_tokens(blob),
                )
            )
    phase = story_phase_rules
    if phase is None:
        phase = co.get("phaseRules") or co.get("phase_rules")
    if isinstance(phase, dict) and phase:
        phase_json = json.dumps(phase, ensure_ascii=False)[:4000]
        items.append(
            ContextPackItem(
                category="story_phase_rules",
                content=phase_json,
                priority=12,
                estimated_tokens=estimate_item_tokens(phase_json),
            )
        )
    if co:
        co_json = json.dumps(co, ensure_ascii=False)[:16000]
        items.append(
            ContextPackItem(
                category="narrative_obligations",
                content=co_json,
                priority=12,
                estimated_tokens=estimate_item_tokens(co_json),
            )
        )

    for category, text in (
        ("story_anchor", story_anchor or co.get("storyAnchor") or co.get("story_anchor")),
        (
            "scars_and_motivations",
            scars_and_motivations or co.get("scarsAndMotivations") or co.get("scars_and_motivations"),
        ),
        ("debt_due", debt_due or co.get("debtDueBlock") or co.get("debt_due")),
        ("causal_chains", causal_chains or co.get("causalChainsBlock") or co.get("causal_chains")),
    ):
        if isinstance(text, str) and text.strip():
            c = text.strip()[:8000]
            items.append(
                ContextPackItem(
                    category=category,
                    content=c,
                    priority=12,
                    estimated_tokens=estimate_item_tokens(c),
                )
            )
    aem = active_entity_memory
    if isinstance(aem, str) and aem.strip():
        c = aem.strip()[:6000]
        items.append(
            ContextPackItem(
                category="active_entity_memory",
                content=c,
                priority=11,
                estimated_tokens=estimate_item_tokens(c),
            )
        )

    mem = (memory_engine or "").strip()
    if mem:
        c = mem[:6000]
        items.append(
            ContextPackItem(
                category="memory_engine",
                content=c,
                priority=12,
                estimated_tokens=estimate_item_tokens(c),
            )
        )
    fl = (fact_lock or "").strip()
    if fl and not mem:
        items.append(
            ContextPackItem(
                category="fact_lock",
                content=fl[:4000],
                priority=12,
                estimated_tokens=estimate_item_tokens(fl[:4000]),
            )
        )
    aip = (anti_ai_protocol or "").strip()
    if aip:
        c = aip[:4000]
        items.append(
            ContextPackItem(
                category="anti_ai_protocol",
                content=c,
                priority=11,
                estimated_tokens=estimate_item_tokens(c),
            )
        )
    if isinstance(beat_sheet_hints, list) and beat_sheet_hints:
        blob = "\n".join(f"- {x}" for x in beat_sheet_hints[:12])[:3000]
        items.append(
            ContextPackItem(
                category="beat_sheet_hints",
                content=blob,
                priority=11,
                estimated_tokens=estimate_item_tokens(blob),
            )
        )

    plan = (confirmed_chapter_plan or "").strip()
    if plan:
        c = plan[:12000]
        items.append(
            ContextPackItem(
                category="chapter_plan_confirmed",
                content=c,
                priority=12,
                estimated_tokens=estimate_item_tokens(c),
            )
        )

    notes = (user_rewrite_notes or "").strip()
    if notes:
        c = notes[:50000]
        items.append(
            ContextPackItem(
                category="human_instruction",
                content=c,
                priority=11,
                estimated_tokens=estimate_item_tokens(c),
            )
        )

    ch_json = json.dumps(chapter_contract, ensure_ascii=False)
    items.append(
        ContextPackItem(
            category="chapter_contract",
            content=ch_json,
            priority=11,
            estimated_tokens=estimate_item_tokens(ch_json),
        )
    )

    st_json = json.dumps(story_contract, ensure_ascii=False)
    items.append(
        ContextPackItem(
            category="story_contract",
            content=st_json,
            priority=11,
            estimated_tokens=estimate_item_tokens(st_json),
        )
    )

    forbidden_parts: list[str] = []
    fm_s = story_contract.get("forbiddenMoves")
    if isinstance(fm_s, list):
        forbidden_parts.extend(str(x) for x in fm_s if x)
    fm_c = chapter_contract.get("forbiddenMoves")
    if isinstance(fm_c, list):
        forbidden_parts.extend(str(x) for x in fm_c if x)
    forbidden_blob = "\n".join(forbidden_parts) if forbidden_parts else "(无禁忌条目)"
    items.append(
        ContextPackItem(
            category="forbidden_moves",
            content=forbidden_blob[:80000],
            priority=11,
            estimated_tokens=estimate_item_tokens(forbidden_blob[:80000]),
        )
    )

    canon_obj = build_story_canon(story_contract)
    canon_json = json.dumps(canon_obj, ensure_ascii=False)
    items.append(
        ContextPackItem(
            category="story_canon",
            content=canon_json[:80000],
            priority=11,
            estimated_tokens=estimate_item_tokens(canon_json[:80000]),
        )
    )

    sp = (fan_series_preset or "").strip()
    preset = get_series_preset(sp)
    if preset is not None:
        digest = str(preset.get("chapter_digest") or "").strip()
        if digest:
            items.append(
                ContextPackItem(
                    category="fan_series_digest",
                    content=digest[:8000],
                    priority=11,
                    estimated_tokens=estimate_item_tokens(digest[:8000]),
                )
            )

    rg = relationship_graph if isinstance(relationship_graph, dict) else {}
    rg_json = json.dumps(rg, ensure_ascii=False)
    items.append(
        ContextPackItem(
            category="relationship_graph",
            content=rg_json[:80000],
            priority=10,
            estimated_tokens=estimate_item_tokens(rg_json[:80000]),
        )
    )

    ue = unresolved_events if isinstance(unresolved_events, dict) else {}
    ue_json = json.dumps(ue, ensure_ascii=False)
    items.append(
        ContextPackItem(
            category="unresolved_events",
            content=ue_json[:80000],
            priority=10,
            estimated_tokens=estimate_item_tokens(ue_json[:80000]),
        )
    )

    meta = json.dumps({"project_id": project_id, "chapter_no": chapter_no}, ensure_ascii=False)
    items.append(
        ContextPackItem(
            category="meta_scope",
            content=meta,
            priority=9,
            estimated_tokens=estimate_item_tokens(meta),
        )
    )

    vc_json = json.dumps(vector_context, ensure_ascii=False)
    items.append(
        ContextPackItem(
            category="vector_context",
            content=vc_json[:80000],
            priority=6,
            estimated_tokens=estimate_item_tokens(vc_json[:80000]),
        )
    )

    n_hist = len(history_summaries)
    for idx, entry in enumerate(history_summaries):
        chunk = json.dumps(entry, ensure_ascii=False)
        recency = (idx + 1) / max(n_hist, 1)
        priority = int(3 + recency * 6)
        priority = max(3, min(9, priority))
        items.append(
            ContextPackItem(
                category="history_summary",
                content=chunk[:60000],
                priority=priority,
                estimated_tokens=estimate_item_tokens(chunk[:60000]),
            )
        )

    for idx, entry in enumerate(recent_summaries):
        chunk = json.dumps(entry, ensure_ascii=False)
        pr = 8 + min(2, idx)
        items.append(
            ContextPackItem(
                category="recent_summary",
                content=chunk[:60000],
                priority=min(11, pr),
                estimated_tokens=estimate_item_tokens(chunk[:60000]),
            )
        )

    return items


def _shrink_protected_until_budget(protected: list[ContextPackItem], budget_tokens: int) -> list[ContextPackItem]:
    """PROTECTED 总和仍超预算时，轮换压缩 story_contract → chapter_contract → story_canon；不动 forbidden / human_instruction。"""
    out = list(protected)

    def total() -> int:
        return sum(x.estimated_tokens for x in out)

    guard = 0
    while total() > budget_tokens and guard < 56:
        guard += 1
        shrunk = False
        for cat in ("story_contract", "chapter_contract", "story_canon", "fan_series_digest"):
            idx = next((i for i, x in enumerate(out) if x.category == cat), None)
            if idx is None:
                continue
            it = out[idx]
            prev_tk = it.estimated_tokens
            new_cap = max(384, prev_tk * 3 // 4)
            if cat == "story_canon":
                ct, tk = shrink_story_canon_json(it.content, new_cap, count_tokens)
            elif cat == "fan_series_digest":
                ct, tk = truncate_content_to_tokens(it.content, new_cap)
            else:
                ct, tk = truncate_content_to_tokens(it.content, new_cap)
            if tk < prev_tk:
                out[idx] = ContextPackItem(
                    category=it.category,
                    content=ct,
                    priority=it.priority,
                    estimated_tokens=tk,
                )
                shrunk = True
                break
        if shrunk:
            continue
        drop_ci = next((i for i, x in enumerate(out) if x.category == "story_canon"), None)
        if drop_ci is not None:
            out.pop(drop_ci)
            continue
        drop_fd = next((i for i, x in enumerate(out) if x.category == "fan_series_digest"), None)
        if drop_fd is not None:
            out.pop(drop_fd)
            continue
        break
    if total() > budget_tokens:
        logger.warning("[TokenBudget] protected still exceeds budget after shrink: %s > %s", total(), budget_tokens)
    return out


def trim_pack_items(
    items: list[ContextPackItem],
    budget_tokens: int,
) -> tuple[list[ContextPackItem], dict[str, Any]]:
    """先保留所有 PROTECTED；可选条目按 priority 从高到低贪心装入；超出则从低 priority 可选条目剔除。"""
    protected = [x for x in items if x.category in PROTECTED_CATEGORIES]
    optional = [x for x in items if x.category not in PROTECTED_CATEGORIES]

    total_before = sum(x.estimated_tokens for x in items)

    def prot_sum(xs: list[ContextPackItem]) -> int:
        return sum(x.estimated_tokens for x in xs)

    ps = prot_sum(protected)
    dropped_optional: list[str] = []

    if ps > budget_tokens:
        protected = _shrink_protected_until_budget(protected, budget_tokens)
        ps = prot_sum(protected)

    remaining = max(0, budget_tokens - ps)
    indexed_opt = list(enumerate(optional))
    kept_indices: list[int] = []
    used = 0
    for idx, x in sorted(
        indexed_opt,
        key=lambda t: (-t[1].priority, TOKEN_TIER_BY_CATEGORY.get(t[1].category, 2), t[0]),
    ):
        if used + x.estimated_tokens <= remaining:
            kept_indices.append(idx)
            used += x.estimated_tokens
        else:
            dropped_optional.append(x.category)
    kept_opt = [optional[i] for i in sorted(kept_indices)]

    merged = protected + kept_opt
    total_after = sum(x.estimated_tokens for x in merged)
    status = {
        "budget_tokens": budget_tokens,
        "estimated_tokens_before": total_before,
        "estimated_tokens_after": total_after,
        "dropped_optional_categories": dropped_optional,
        "protected_categories_kept": [x.category for x in protected],
    }
    logger.info(
        "[TokenBudget] before=%s after=%s budget=%s dropped=%s",
        total_before,
        total_after,
        budget_tokens,
        dropped_optional,
    )
    return merged, status


def _chapter_no_key(entry: dict[str, Any]) -> int:
    v = entry.get("chapterNo", entry.get("chapter_no"))
    try:
        return int(v) if v is not None else 0
    except (TypeError, ValueError):
        return 0


def items_to_context_pack(items: list[ContextPackItem]) -> dict[str, Any]:
    """将裁剪后的条目还原为 Planner/Ghostwriter 使用的 context_pack 扁平 dict。"""
    pack: dict[str, Any] = {
        "history_summaries": [],
        "recent_summaries": [],
        "vector_context": {"keywords": [], "chunks": []},
        "relationship_graph": {},
        "unresolved_events": {},
    }
    hist_buf: list[dict[str, Any]] = []
    recent_buf: list[dict[str, Any]] = []
    for it in items:
        if it.category == "chapter_contract":
            try:
                pack["chapter_contract"] = json.loads(it.content)
            except json.JSONDecodeError:
                pack["chapter_contract"] = {}
        elif it.category == "story_contract":
            try:
                pack["story_contract"] = json.loads(it.content)
            except json.JSONDecodeError:
                pack["story_contract"] = {}
        elif it.category == "story_canon":
            try:
                pack["story_canon"] = json.loads(it.content)
            except json.JSONDecodeError:
                pack["story_canon"] = {}
        elif it.category == "fan_series_digest":
            pack["fan_series_digest"] = it.content
        elif it.category == "forbidden_moves":
            pack["forbidden_moves"] = it.content
        elif it.category == "human_instruction":
            pack["human_instruction"] = it.content
        elif it.category == "chapter_plan_confirmed":
            pack["author_confirmed_chapter_plan"] = it.content
        elif it.category == "narrative_obligations":
            try:
                pack["narrative_obligations"] = json.loads(it.content)
            except json.JSONDecodeError:
                pack["narrative_obligations"] = {}
        elif it.category == "narrative_prompt_lines":
            pack["narrative_prompt_lines"] = [
                ln for ln in it.content.split("\n") if ln.strip()
            ]
        elif it.category == "story_phase_rules":
            try:
                pack["story_phase_rules"] = json.loads(it.content)
            except json.JSONDecodeError:
                pack["story_phase_rules"] = {}
        elif it.category == "narrative_summary":
            pack["narrative_obligations"] = pack.get("narrative_obligations") or {}
            if isinstance(pack["narrative_obligations"], dict):
                pack["narrative_obligations"]["summaryLine"] = it.content
        elif it.category == "continuity_brief":
            pack["continuity_brief"] = it.content
        elif it.category == "previously_on":
            pack["previously_on"] = it.content
        elif it.category == "story_anchor":
            pack["story_anchor"] = it.content
        elif it.category == "scars_and_motivations":
            pack["scars_and_motivations"] = it.content
        elif it.category == "debt_due":
            pack["debt_due"] = it.content
        elif it.category == "causal_chains":
            pack["causal_chains"] = it.content
        elif it.category == "active_entity_memory":
            pack["active_entity_memory"] = it.content
        elif it.category == "memory_engine":
            pack["memory_engine"] = it.content
        elif it.category == "fact_lock":
            pack["fact_lock"] = it.content
        elif it.category == "anti_ai_protocol":
            pack["anti_ai_protocol"] = it.content
        elif it.category == "beat_sheet_hints":
            pack["beat_sheet_hints"] = [ln[2:].strip() for ln in it.content.split("\n") if ln.startswith("- ")]
        elif it.category == "meta_scope":
            try:
                meta = json.loads(it.content)
                pack["project_id"] = meta.get("project_id")
                pack["chapter_no"] = meta.get("chapter_no")
            except json.JSONDecodeError:
                pass
        elif it.category == "vector_context":
            try:
                pack["vector_context"] = json.loads(it.content)
            except json.JSONDecodeError:
                pack["vector_context"] = {"keywords": [], "chunks": []}
        elif it.category == "relationship_graph":
            try:
                pack["relationship_graph"] = json.loads(it.content)
            except json.JSONDecodeError:
                pack["relationship_graph"] = {}
        elif it.category == "unresolved_events":
            try:
                pack["unresolved_events"] = json.loads(it.content)
            except json.JSONDecodeError:
                pack["unresolved_events"] = {}
        elif it.category == "history_summary":
            try:
                hist_buf.append(json.loads(it.content))
            except json.JSONDecodeError:
                pass
        elif it.category == "recent_summary":
            try:
                recent_buf.append(json.loads(it.content))
            except json.JSONDecodeError:
                pass
    hist_buf.sort(key=_chapter_no_key)
    recent_buf.sort(key=_chapter_no_key)
    pack["history_summaries"] = hist_buf
    pack["recent_summaries"] = recent_buf
    return pack
