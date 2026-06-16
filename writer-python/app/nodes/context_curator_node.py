"""Context Curator：滚动摘要 + 意图关键词 + Qdrant 向量召回（Day 10）。"""

from __future__ import annotations

import json
import logging
import re
from typing import Any

from app.graph.sse_context import get_chapter_sse_emit
from app.services.llm_gateway import LLMGateway
from app.services.narrative_feed_forward import (
    build_active_entity_memory,
    build_previously_on,
    merge_plotpilot_slots,
)
from app.services.token_budget_service import build_pack_items
from app.services.memory_engine_lite import build_memory_engine_blocks, format_memory_engine_for_pack
from app.services.neo4j_lore_store import recall_for_chapter
from app.services.prompt_cpms import load_node_prompt
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
    from app.services.memory_engine_constants import (
        HISTORY_SUMMARIES_MAX_CHAPTERS,
        RECENT_SUMMARY_TAIL_CHAPTERS,
    )

    if len(hist) > HISTORY_SUMMARIES_MAX_CHAPTERS:
        hist = hist[-HISTORY_SUMMARIES_MAX_CHAPTERS:]
    tail = hist[-RECENT_SUMMARY_TAIL_CHAPTERS:] if hist else []

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

    scores = [c.get("score") for c in vector_chunks if c.get("score") is not None]
    logger.info(
        "[VectorSearch] keywords=%s hits=%d scores=%s query_preview=%s",
        keywords,
        len(vector_chunks),
        [round(float(s), 3) for s in scores[:5]],
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

    ch_no_int = int(ch_no) if ch_no is not None else 0
    mem_blocks = build_memory_engine_blocks(
        hist if isinstance(hist, list) else [],
        chapter_no=ch_no_int,
        story_contract=raw_story,
    )
    memory_engine_text = format_memory_engine_for_pack(mem_blocks)

    partial = state.get("context_pack")
    partial_dict = partial if isinstance(partial, dict) else {}

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
    if memory_engine_text:
        pack["memory_engine"] = memory_engine_text
        pack["fact_lock"] = mem_blocks.get("fact_lock", "")
        pack["completed_beats_lock"] = mem_blocks.get("completed_beats", "")
        pack["revealed_clues"] = mem_blocks.get("revealed_clues", "")

    try:
        pack["anti_ai_protocol"] = load_node_prompt("anti_ai_protocol", fallback_file="anti_ai_protocol_v1.md")[:4000]
    except FileNotFoundError:
        pass

    cc = state.get("chapter_contract") if isinstance(state.get("chapter_contract"), dict) else {}
    must_cover = cc.get("mustCover") or cc.get("must_cover")
    if isinstance(must_cover, list) and must_cover:
        pack["beat_sheet_hints"] = [str(x).strip() for x in must_cover if x and str(x).strip()][:12]

    co = state.get("chapter_obligations")
    if co is None:
        co = state.get("chapterObligations")
    narrative_prompt_lines: list[str] | None = None
    story_phase_rules: dict[str, Any] | None = None
    if isinstance(co, dict) and co:
        pack["narrative_obligations"] = co
        prompt_lines = co.get("narrativePromptLines") or co.get("narrative_prompt_lines")
        if isinstance(prompt_lines, list) and prompt_lines:
            narrative_prompt_lines = [str(x) for x in prompt_lines if x]
            pack["narrative_prompt_lines"] = narrative_prompt_lines
        phase_rules = co.get("phaseRules") or co.get("phase_rules")
        if isinstance(phase_rules, dict):
            story_phase_rules = phase_rules
            pack["story_phase_rules"] = phase_rules
        merge_plotpilot_slots(pack, co)
        active_mem = build_active_entity_memory(lore_bundle.get("neo4j_recall"))
        if active_mem:
            pack["active_entity_memory"] = active_mem
        pack["narrative_graph_recall"] = {
            "activeStorylines": co.get("activeStorylines") or [],
            "dueConfluences": co.get("dueConfluences") or [],
            "overdueSubtext": co.get("overdueSubtext") or [],
            "storyPhase": co.get("storyPhase"),
        }

    previously_on = build_previously_on(
        hist if isinstance(hist, list) else [],
        chapter_no=int(ch_no) if ch_no is not None else 0,
    )
    if previously_on:
        pack["previously_on"] = previously_on

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
    plan = (state.get("confirmed_chapter_plan_summary") or state.get("confirmedChapterPlanSummary") or "").strip()
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
        confirmed_chapter_plan=plan or None,
        narrative_obligations=co if isinstance(co, dict) else None,
        narrative_prompt_lines=narrative_prompt_lines,
        story_phase_rules=story_phase_rules,
        previously_on=previously_on or None,
        continuity_brief=pack.get("continuity_brief") if isinstance(pack.get("continuity_brief"), str) else None,
        story_anchor=pack.get("story_anchor") if isinstance(pack.get("story_anchor"), str) else None,
        scars_and_motivations=pack.get("scars_and_motivations")
        if isinstance(pack.get("scars_and_motivations"), str)
        else None,
        debt_due=pack.get("debt_due") if isinstance(pack.get("debt_due"), str) else None,
        causal_chains=pack.get("causal_chains") if isinstance(pack.get("causal_chains"), str) else None,
        active_entity_memory=pack.get("active_entity_memory")
        if isinstance(pack.get("active_entity_memory"), str)
        else None,
        memory_engine=memory_engine_text or None,
        fact_lock=mem_blocks.get("fact_lock") or None,
        anti_ai_protocol=pack.get("anti_ai_protocol") if isinstance(pack.get("anti_ai_protocol"), str) else None,
        beat_sheet_hints=pack.get("beat_sheet_hints") if isinstance(pack.get("beat_sheet_hints"), list) else None,
    )
    context_pack_items = [x.model_dump() for x in items]

    for k, v in partial_dict.items():
        if v is None:
            continue
        if k not in pack or pack.get(k) in (None, "", {}, []):
            pack[k] = v

    return {"context_pack": pack, "context_pack_items": context_pack_items}
