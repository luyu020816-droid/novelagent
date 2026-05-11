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
    }
)

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
) -> list[ContextPackItem]:
    """将 curator 各类上下文拆成带优先级的条目（越远的历史摘要 priority 越低）。"""
    items: list[ContextPackItem] = []

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
    for idx, x in sorted(indexed_opt, key=lambda t: (-t[1].priority, t[0])):
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
