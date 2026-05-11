"""Context Curator：滚动摘要 + 意图关键词 + Qdrant 向量召回（Day 10）。"""

from __future__ import annotations

import json
import logging
import re
from typing import Any

from app.graph.sse_context import get_chapter_sse_emit
from app.services.llm_gateway import LLMGateway
from app.services.token_budget_service import build_pack_items
from app.services.neo4j_lore_store import recall_for_chapter
from app.services.story_canon_service import build_story_canon
from app.services.vector_service import query_knowledge

logger = logging.getLogger(__name__)


def _fallback_keywords(chapter_contract: dict[str, Any]) -> list[str]:
    out: list[str] = []
    for key in ("titleHint", "chapterGoal", "cliffhanger"):
        v = chapter_contract.get(key)
        if isinstance(v, str) and v.strip():
            out.append(v.strip()[:120])
    mc = chapter_contract.get("mustCover")
    if isinstance(mc, list):
        for x in mc[:5]:
            if isinstance(x, str) and x.strip():
                out.append(x.strip()[:120])
    # 简单中文短语抽词（2–6 字常见专名）
    blob = " ".join(out)
    for m in re.findall(r"[\u4e00-\u9fff]{2,8}", blob):
        if m not in out:
            out.append(m)
        if len(out) >= 8:
            break
    return out[:5]


def _fallback_outline_characters(chapter_contract: dict[str, Any]) -> list[str]:
    out: list[str] = []
    for key in ("povCharacter", "pov", "protagonist", "focusCharacter"):
        v = chapter_contract.get(key)
        if isinstance(v, str) and v.strip():
            out.append(v.strip()[:64])
    for key in ("cast", "characters", "characterList", "outlineCharacters"):
        v = chapter_contract.get(key)
        if isinstance(v, list):
            for x in v[:16]:
                if isinstance(x, str) and x.strip():
                    out.append(x.strip()[:64])
                elif isinstance(x, dict):
                    n = x.get("name") or x.get("characterName") or x.get("id")
                    if isinstance(n, str) and n.strip():
                        out.append(n.strip()[:64])
    seen: set[str] = set()
    uniq: list[str] = []
    for x in out:
        if x not in seen:
            seen.add(x)
            uniq.append(x)
    return uniq[:12]


def _extract_curator_hints_llm(
    chapter_contract: dict[str, Any],
    gateway: LLMGateway,
    *,
    project_id: str | None,
    chapter_no: int | None,
) -> tuple[list[str], list[str]]:
    sys = (
        "你是小说检索助手。从章纲 JSON 中提取：(1) 3-5 个向量检索关键词（人物名、地名、组织、道具、专有名词）；"
        "(2) 本章大纲涉及的出场人物姓名列表（2-12 个，优先实名）。"
        '只输出 JSON：{"keywords":["…"],"outline_characters":["…"]}，不要其它文字。'
    )
    user = json.dumps(chapter_contract, ensure_ascii=False)[:12000]
    res = gateway.chat_completion(
        messages=[{"role": "system", "content": sys}, {"role": "user", "content": user}],
        response_format_json=True,
        temperature=0.1,
        agent_name="chapter_gen",
        node_name="context_curator_intent",
        project_id=project_id,
        chapter_no=chapter_no,
        on_delta=None,
    )
    obj = json.loads(res.text)
    if not isinstance(obj, dict):
        raise ValueError("intent response not object")
    kws = obj.get("keywords")
    if not isinstance(kws, list):
        raise ValueError("keywords missing")
    cleaned_kw: list[str] = []
    for x in kws:
        if isinstance(x, str) and x.strip():
            cleaned_kw.append(x.strip()[:64])
        if len(cleaned_kw) >= 5:
            break
    chars_raw = obj.get("outline_characters")
    cleaned_chars: list[str] = []
    if isinstance(chars_raw, list):
        for x in chars_raw:
            if isinstance(x, str) and x.strip():
                cleaned_chars.append(x.strip()[:64])
            if len(cleaned_chars) >= 12:
                break
    return cleaned_kw[:5], cleaned_chars[:12]


def context_curator_node(state: dict[str, Any], *, gateway: LLMGateway) -> dict[str, Any]:
    hist = state.get("history_summaries")
    if not hist:
        hist = state.get("recent_summaries") or []
    if len(hist) > 20:
        hist = hist[-20:]
    tail = hist[-3:] if hist else []

    chapter_contract = state.get("chapter_contract") if isinstance(state.get("chapter_contract"), dict) else {}
    pid = str(state.get("project_id") or "")
    ch_no = state.get("chapter_no")

    keywords: list[str] = []
    outline_chars: list[str] = []
    try:
        keywords, outline_chars = _extract_curator_hints_llm(
            chapter_contract,
            gateway,
            project_id=pid or None,
            chapter_no=int(ch_no) if ch_no is not None else None,
        )
    except Exception as e:
        logger.debug("intent LLM failed or sparse: %s", e)
        keywords = []
        outline_chars = []
    if len(keywords) < 3:
        for x in _fallback_keywords(chapter_contract):
            if x not in keywords:
                keywords.append(x)
            if len(keywords) >= 5:
                break
    if len(outline_chars) < 2:
        for x in _fallback_outline_characters(chapter_contract):
            if x not in outline_chars:
                outline_chars.append(x)
            if len(outline_chars) >= 12:
                break

    query_line = " ".join(keywords) if keywords else ""
    vector_chunks: list[dict[str, Any]] = []
    if pid and query_line:
        try:
            vector_chunks = query_knowledge(pid, query_line, limit=3)
        except Exception as e:
            logger.warning("[VectorSearch] query failed: %s", e)

    logger.info(
        "[VectorSearch] keywords=%s hits=%d query_preview=%s",
        keywords,
        len(vector_chunks),
        query_line[:160],
    )

    lore_bundle = {"outline_characters": outline_chars, "neo4j_recall": {}}
    if pid and outline_chars:
        try:
            lore_bundle["neo4j_recall"] = recall_for_chapter(
                project_id=pid,
                outline_character_names=outline_chars,
            )
        except Exception as e:
            logger.warning("[Neo4jRecall] failed: %s", e)

    relationship_graph = {
        "outline_characters": outline_chars,
        "nodes": lore_bundle["neo4j_recall"].get("nodes_touched") or [],
        "edges": lore_bundle["neo4j_recall"].get("relationship_edges") or [],
    }
    unresolved_events = {
        "open_foreshadowing": lore_bundle["neo4j_recall"].get("open_foreshadowing") or [],
        "recent_events": lore_bundle["neo4j_recall"].get("recent_events") or [],
    }

    raw_story = state["story_contract"] if isinstance(state.get("story_contract"), dict) else {}
    story_canon = build_story_canon(raw_story)

    pack: dict[str, Any] = {
        "project_id": state["project_id"],
        "chapter_no": state["chapter_no"],
        "story_contract": state["story_contract"],
        "story_canon": story_canon,
        "chapter_contract": state["chapter_contract"],
        "history_summaries": hist,
        "recent_summaries": tail,
        "vector_context": {
            "keywords": keywords,
            "chunks": vector_chunks,
        },
        "relationship_graph": relationship_graph,
        "unresolved_events": unresolved_events,
    }

    try:
        chapters_meta: list[Any] = []
        for x in hist:
            if isinstance(x, dict):
                cn = x.get("chapterNo", x.get("chapter_no"))
                if cn is not None:
                    chapters_meta.append(cn)
        get_chapter_sse_emit()(
            "artifact",
            {"kind": "rolling_memory_meta", "data": {"historySummaryChapters": chapters_meta}},
        )
        get_chapter_sse_emit()(
            "artifact",
            {
                "kind": "vector_memory_meta",
                "data": {"keywords": keywords, "hitCount": len(vector_chunks)},
            },
        )
        get_chapter_sse_emit()(
            "artifact",
            {
                "kind": "lore_graph_meta",
                "data": {
                    "outlineCharacters": outline_chars,
                    "edgeCount": len(relationship_graph.get("edges") or []),
                    "foreshadowCount": len(unresolved_events.get("open_foreshadowing") or []),
                },
            },
        )
    except Exception:
        pass

    notes = (state.get("user_rewrite_notes") or "").strip()
    fs_raw = state.get("fan_series_preset") or state.get("fanSeriesPreset")
    fan_series_preset = fs_raw.strip() if isinstance(fs_raw, str) else None
    items = build_pack_items(
        project_id=str(state["project_id"]),
        chapter_no=int(state["chapter_no"]),
        story_contract=pack["story_contract"] if isinstance(pack.get("story_contract"), dict) else {},
        chapter_contract=pack["chapter_contract"] if isinstance(pack.get("chapter_contract"), dict) else {},
        history_summaries=hist if isinstance(hist, list) else [],
        recent_summaries=tail if isinstance(tail, list) else [],
        vector_context=pack["vector_context"] if isinstance(pack.get("vector_context"), dict) else {},
        relationship_graph=relationship_graph,
        unresolved_events=unresolved_events,
        user_rewrite_notes=notes,
        fan_series_preset=fan_series_preset,
    )
    context_pack_items = [x.model_dump() for x in items]

    return {"context_pack": pack, "context_pack_items": context_pack_items}
