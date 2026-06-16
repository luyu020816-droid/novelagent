"""内置节点实现 — 包装现有 LangGraph 节点并补齐 PlotPilot 类型库（30 种）。"""

from __future__ import annotations

import json
from typing import Any

from app.dag.models import NodeCategory, NodeMeta, NodePort, PromptMode
from app.dag.registry import NodeRegistry
from app.dag.gateway_helpers import apply_rule_review_gates
from app.dag.review_helpers import (
    ext_summary_from_history,
    review_foreshadow as _review_foreshadow,
    review_storyline as _review_storyline,
    review_timeline as _review_timeline,
)
from app.services.consistency_checker import run_consistency_check
from app.nodes.budget_node import budget_node
from app.nodes.context_curator_node import context_curator_node
from app.nodes.critic_node import critic_node
from app.nodes.decision_gate_node import decision_gate_node
from app.nodes.ghostwriter_node import ghostwriter_node
from app.nodes.planner_node import planner_node
from app.nodes.scene_director_node import scene_director_node
from app.nodes.stylist_node import stylist_node
from app.services.narrative_metrics_heuristic import _heuristic_tension
from app.services.fatigue_scanner import scan_fatigue
from app.services.llm_gateway import LLMGateway
from app.services.memory_engine_lite import build_memory_engine_blocks, format_memory_engine_for_pack
from app.services.narrative_feed_forward import build_previously_on, merge_plotpilot_slots
from app.services.prompt_cpms import load_node_prompt
from app.services.scene_plan_beats import enforce_scene_plan_beats
from app.services.sensitive_scanner import scan_sensitive, should_fail_critic
from app.services.story_canon_service import build_story_canon
from app.chapter_length_policy import CHAPTER_BODY_MAX_CHARS, CHAPTER_BODY_MIN_CHARS, chapter_char_count


def _gw(state: dict[str, Any]) -> LLMGateway:
    g = state.get("_dag_gateway")
    if isinstance(g, LLMGateway):
        return g
    return LLMGateway()


def _patch_context(state: dict[str, Any], **kwargs: Any) -> dict[str, Any]:
    pack = state.get("context_pack")
    if not isinstance(pack, dict):
        pack = {}
    pack.update(kwargs)
    return {"context_pack": pack}


def _stage_marker(state: dict[str, Any], stage: str) -> dict[str, Any]:
    done = list(state.get("_ctx_stages_done") or [])
    done.append(stage)
    return {"_ctx_stages_done": done}


# ─── 上下文（6）───


@NodeRegistry.register(
    "ctx_blueprint",
    NodeMeta(
        node_type="ctx_blueprint",
        display_name="剧本基建",
        category=NodeCategory.CONTEXT,
        cpms_node_key="ctx_blueprint",
        description="注入 story_canon 与章纲契约",
        output_ports=[NodePort(name="story_canon", description="设定契约")],
    ),
)
def ctx_blueprint(state: dict[str, Any]) -> dict[str, Any]:
    raw = state.get("story_contract") if isinstance(state.get("story_contract"), dict) else {}
    canon = build_story_canon(raw)
    out = _patch_context(
        state,
        story_canon=canon,
        story_contract=state.get("story_contract"),
        chapter_contract=state.get("chapter_contract"),
    )
    out.update(_stage_marker(state, "ctx_blueprint"))
    return out


@NodeRegistry.register(
    "ctx_memory",
    NodeMeta(
        node_type="ctx_memory",
        display_name="记忆引擎",
        category=NodeCategory.CONTEXT,
        cpms_node_key="ctx_memory",
        description="FACT_LOCK / 已完成节拍 / 已揭示线索",
    ),
)
def ctx_memory(state: dict[str, Any]) -> dict[str, Any]:
    hist = state.get("history_summaries") or state.get("recent_summaries") or []
    ch_no = int(state.get("chapter_no") or 0)
    raw = state.get("story_contract") if isinstance(state.get("story_contract"), dict) else {}
    blocks = build_memory_engine_blocks(hist if isinstance(hist, list) else [], chapter_no=ch_no, story_contract=raw)
    text = format_memory_engine_for_pack(blocks)
    out = _patch_context(
        state,
        memory_engine=text,
        fact_lock=blocks.get("fact_lock", ""),
        completed_beats_lock=blocks.get("completed_beats", ""),
        revealed_clues=blocks.get("revealed_clues", ""),
    )
    out.update(_stage_marker(state, "ctx_memory"))
    return out


@NodeRegistry.register(
    "ctx_foreshadow",
    NodeMeta(
        node_type="ctx_foreshadow",
        display_name="伏笔注入",
        category=NodeCategory.CONTEXT,
        description="前情提要与未回收伏笔",
    ),
)
def ctx_foreshadow(state: dict[str, Any]) -> dict[str, Any]:
    hist = state.get("history_summaries") or state.get("recent_summaries") or []
    ch_no = int(state.get("chapter_no") or 0)
    prev = build_previously_on(hist if isinstance(hist, list) else [], chapter_no=ch_no)
    pack = state.get("context_pack") if isinstance(state.get("context_pack"), dict) else {}
    unresolved = pack.get("unresolved_events") if isinstance(pack.get("unresolved_events"), dict) else {}
    out = _patch_context(state, previously_on=prev, unresolved_events=unresolved)
    out.update(_stage_marker(state, "ctx_foreshadow"))
    return out


@NodeRegistry.register(
    "ctx_voice",
    NodeMeta(
        node_type="ctx_voice",
        display_name="角色声线",
        category=NodeCategory.CONTEXT,
        description="主角/配角口吻与人设块",
    ),
)
def ctx_voice(state: dict[str, Any]) -> dict[str, Any]:
    pack = state.get("context_pack") if isinstance(state.get("context_pack"), dict) else {}
    canon = pack.get("story_canon") if isinstance(pack.get("story_canon"), dict) else {}
    voice = {
        "protagonist_contract": canon.get("protagonist_contract") or canon.get("protagonistContract"),
        "supporting_contracts": canon.get("supporting_contracts") or canon.get("supportingContracts"),
    }
    out = _patch_context(state, voice_block=voice)
    out.update(_stage_marker(state, "ctx_voice"))
    return out


@NodeRegistry.register(
    "ctx_debt",
    NodeMeta(
        node_type="ctx_debt",
        display_name="叙事债务",
        category=NodeCategory.CONTEXT,
        description="汇合/子文本/任务单",
    ),
)
def ctx_debt(state: dict[str, Any]) -> dict[str, Any]:
    co = state.get("chapter_obligations") or state.get("chapterObligations")
    if not isinstance(co, dict):
        return _stage_marker(state, "ctx_debt")
    pack = dict(state.get("context_pack") or {})
    pack["narrative_obligations"] = co
    merge_plotpilot_slots(pack, co)
    return {"context_pack": pack, **_stage_marker(state, "ctx_debt")}


@NodeRegistry.register(
    "ctx_vector",
    NodeMeta(
        node_type="ctx_vector",
        display_name="向量召回",
        category=NodeCategory.CONTEXT,
        description="Qdrant + Neo4j 图谱召回（在 ctx_assemble 中完整执行）",
    ),
)
def ctx_vector(state: dict[str, Any]) -> dict[str, Any]:
    return _stage_marker(state, "ctx_vector")


@NodeRegistry.register(
    "ctx_assemble",
    NodeMeta(
        node_type="ctx_assemble",
        display_name="上下文组装",
        category=NodeCategory.CONTEXT,
        description="Curator 完整组装 context_pack 与 pack_items",
    ),
)
def ctx_assemble(state: dict[str, Any]) -> dict[str, Any]:
    return context_curator_node(state, gateway=_gw(state))


# ─── 规划（4）───


@NodeRegistry.register(
    "planning_beat_sheet",
    NodeMeta(
        node_type="planning_beat_sheet",
        display_name="节拍表规划",
        category=NodeCategory.PLANNING,
        cpms_node_key="planner",
        description="生成 scene_plan.beats（4～8 条）",
    ),
)
def planning_beat_sheet(state: dict[str, Any]) -> dict[str, Any]:
    return planner_node(state, gateway=_gw(state))


@NodeRegistry.register(
    "planning_act",
    NodeMeta(
        node_type="planning_act",
        display_name="场景规划",
        category=NodeCategory.PLANNING,
        cpms_node_key="scene_director",
        description="场景导演细化 tension / pov",
    ),
)
def planning_act(state: dict[str, Any]) -> dict[str, Any]:
    return scene_director_node(state, gateway=_gw(state))


@NodeRegistry.register(
    "planning_outline_partition",
    NodeMeta(
        node_type="planning_outline_partition",
        display_name="章纲分区",
        category=NodeCategory.PLANNING,
        description="将 must_cover 映射到 beats（规则层）",
    ),
)
def planning_outline_partition(state: dict[str, Any]) -> dict[str, Any]:
    plan = state.get("scene_plan") if isinstance(state.get("scene_plan"), dict) else {}
    cc = state.get("chapter_contract") if isinstance(state.get("chapter_contract"), dict) else {}
    enforced = enforce_scene_plan_beats(plan, cc)
    return {"scene_plan": enforced}


@NodeRegistry.register(
    "planning_quick_macro",
    NodeMeta(
        node_type="planning_quick_macro",
        display_name="快宏规划",
        category=NodeCategory.PLANNING,
        description="可选：快速宏观走向（默认透传）",
    ),
)
def planning_quick_macro(state: dict[str, Any]) -> dict[str, Any]:
    return {}


# ─── 执行（4）───


@NodeRegistry.register(
    "budget_token",
    NodeMeta(
        node_type="budget_token",
        display_name="Token 预算",
        category=NodeCategory.EXECUTION,
        description="T0–T3 裁剪 context_pack",
    ),
)
def budget_token(state: dict[str, Any]) -> dict[str, Any]:
    return budget_node(state)


@NodeRegistry.register(
    "exec_beat",
    NodeMeta(
        node_type="exec_beat",
        display_name="节拍放大",
        category=NodeCategory.EXECUTION,
        description="规范化 beats 并校验 ≥4",
    ),
)
def exec_beat(state: dict[str, Any]) -> dict[str, Any]:
    cc = state.get("chapter_contract") if isinstance(state.get("chapter_contract"), dict) else {}
    plan = state.get("scene_plan") if isinstance(state.get("scene_plan"), dict) else {}
    return {"scene_plan": enforce_scene_plan_beats(plan, cc)}


@NodeRegistry.register(
    "exec_writer",
    NodeMeta(
        node_type="exec_writer",
        display_name="剧情引擎",
        category=NodeCategory.EXECUTION,
        cpms_node_key="ghostwriter",
        description="按 beats 分段成稿",
    ),
)
def exec_writer(state: dict[str, Any]) -> dict[str, Any]:
    return ghostwriter_node(state, gateway=_gw(state))


@NodeRegistry.register(
    "exec_polish",
    NodeMeta(
        node_type="exec_polish",
        display_name="润色成稿",
        category=NodeCategory.EXECUTION,
        cpms_node_key="stylist",
        description="通过后文风润色",
    ),
)
def exec_polish(state: dict[str, Any]) -> dict[str, Any]:
    return stylist_node(state, gateway=_gw(state))


# ─── 校验（8）───


@NodeRegistry.register(
    "val_compliance",
    NodeMeta(
        node_type="val_compliance",
        display_name="合规扫描",
        category=NodeCategory.VALIDATION,
        description="AC 敏感词",
    ),
)
def val_compliance(state: dict[str, Any]) -> dict[str, Any]:
    text = state.get("chapter_text") or ""
    rep = scan_sensitive(text, "default")
    return {"_val_compliance": rep}


@NodeRegistry.register(
    "val_length",
    NodeMeta(
        node_type="val_length",
        display_name="篇幅校验",
        category=NodeCategory.VALIDATION,
        description=f"正文 {CHAPTER_BODY_MIN_CHARS}～{CHAPTER_BODY_MAX_CHARS} 字",
    ),
)
def val_length(state: dict[str, Any]) -> dict[str, Any]:
    text = state.get("chapter_text") or ""
    n = chapter_char_count(text)
    ok = CHAPTER_BODY_MIN_CHARS <= n <= CHAPTER_BODY_MAX_CHARS
    return {"_val_length": {"ok": ok, "chars": n}}


@NodeRegistry.register(
    "val_anti_ai",
    NodeMeta(
        node_type="val_anti_ai",
        display_name="反 AI 审计",
        category=NodeCategory.VALIDATION,
        description="疲劳词/套话扫描",
    ),
)
def val_anti_ai(state: dict[str, Any]) -> dict[str, Any]:
    text = state.get("chapter_text") or ""
    rep = scan_fatigue(text, "default")
    return {"_val_anti_ai": rep}


@NodeRegistry.register(
    "val_tension",
    NodeMeta(
        node_type="val_tension",
        display_name="张力评估",
        category=NodeCategory.VALIDATION,
        description="启发式张力分",
    ),
)
def val_tension(state: dict[str, Any]) -> dict[str, Any]:
    text = state.get("chapter_text") or ""
    try:
        t = _heuristic_tension(text)
        return {"_val_tension": {"tensionScore": t}}
    except Exception as e:
        return {"_val_tension": {"error": str(e)}}


@NodeRegistry.register(
    "val_narrative",
    NodeMeta(
        node_type="val_narrative",
        display_name="叙事审查",
        category=NodeCategory.VALIDATION,
        cpms_node_key="critic",
        description="Critic 多维度 JSON 审查",
    ),
)
def val_narrative(state: dict[str, Any]) -> dict[str, Any]:
    return critic_node(state, gateway=_gw(state))


@NodeRegistry.register(
    "val_style",
    NodeMeta(
        node_type="val_style",
        display_name="文风警报",
        category=NodeCategory.VALIDATION,
        description="可选文风漂移（默认记录 styled 差异）",
    ),
)
def val_style(state: dict[str, Any]) -> dict[str, Any]:
    raw = state.get("chapter_text") or ""
    styled = state.get("styled_text") or raw
    return {"_val_style": {"changed": styled != raw, "len_delta": len(styled) - len(raw)}}


@NodeRegistry.register(
    "val_foreshadow",
    NodeMeta(
        node_type="val_foreshadow",
        display_name="伏笔雷达",
        category=NodeCategory.VALIDATION,
        description="标记是否含 open_foreshadowing 推进",
    ),
)
def val_foreshadow(state: dict[str, Any]) -> dict[str, Any]:
    rep = _review_foreshadow(state)
    return {"_val_foreshadow": rep}


@NodeRegistry.register(
    "review_character",
    NodeMeta(
        node_type="review_character",
        display_name="人物一致性",
        category=NodeCategory.REVIEW,
        description="读取 critic consistency_checks",
    ),
)
def review_character(state: dict[str, Any]) -> dict[str, Any]:
    rep = run_consistency_check(state)
    out: dict[str, Any] = {"_review_consistency": rep}
    critic = state.get("critic_report") if isinstance(state.get("critic_report"), dict) else {}
    checks = critic.get("consistency_checks") if isinstance(critic.get("consistency_checks"), dict) else {}
    out["_review_character"] = checks
    return out


# ─── 网关（3）───


@NodeRegistry.register(
    "gw_circuit",
    NodeMeta(
        node_type="gw_circuit",
        display_name="熔断网关",
        category=NodeCategory.GATEWAY,
        description="根据 critic_report.pass 设置 accepted",
    ),
)
def gw_circuit(state: dict[str, Any]) -> dict[str, Any]:
    merged = apply_rule_review_gates(state)
    run_state = {**state, **merged}
    return decision_gate_node(run_state)


@NodeRegistry.register(
    "gw_retry",
    NodeMeta(
        node_type="gw_retry",
        display_name="重写网关",
        category=NodeCategory.GATEWAY,
        description="retry_count +1",
    ),
)
def gw_retry(state: dict[str, Any]) -> dict[str, Any]:
    n = int(state.get("retry_count") or 0)
    return {"retry_count": n + 1}


@NodeRegistry.register(
    "gw_review",
    NodeMeta(
        node_type="gw_review",
        display_name="审阅暂停",
        category=NodeCategory.GATEWAY,
        description="可选人工审阅标记（默认透传）",
    ),
)
def gw_review(state: dict[str, Any]) -> dict[str, Any]:
    rep = state.get("critic_report") if isinstance(state.get("critic_report"), dict) else {}
    accepted = bool(state.get("accepted")) or bool(rep.get("pass"))
    mode = "auto_pass" if accepted else "suggest_hold"
    notes = []
    if not accepted:
        notes.append("审查未通过，建议人工扫一眼再重试")
    return {"_gw_review": mode, "_gw_review_notes": notes}


# ─── Anti-AI（4）───


@NodeRegistry.register(
    "anti_ai_behavior",
    NodeMeta(
        node_type="anti_ai_behavior",
        display_name="行为协议",
        category=NodeCategory.ANTI_AI,
        cpms_node_key="anti_ai_protocol",
        description="注入 Anti-AI 协议到 context",
    ),
)
def anti_ai_behavior(state: dict[str, Any]) -> dict[str, Any]:
    try:
        proto = load_node_prompt("anti_ai_protocol", fallback_file="anti_ai_protocol_v1.md")[:4000]
    except FileNotFoundError:
        proto = ""
    return _patch_context(state, anti_ai_protocol=proto)


@NodeRegistry.register(
    "anti_ai_audit",
    NodeMeta(
        node_type="anti_ai_audit",
        display_name="Anti-AI 审计",
        category=NodeCategory.ANTI_AI,
        description="成稿后套话审计",
    ),
)
def anti_ai_audit(state: dict[str, Any]) -> dict[str, Any]:
    return val_anti_ai(state)


@NodeRegistry.register(
    "anti_ai_char_lock",
    NodeMeta(
        node_type="anti_ai_char_lock",
        display_name="角色锁",
        category=NodeCategory.ANTI_AI,
        description="锁定 must_retain 人设",
    ),
)
def anti_ai_char_lock(state: dict[str, Any]) -> dict[str, Any]:
    pack = state.get("context_pack") if isinstance(state.get("context_pack"), dict) else {}
    canon = pack.get("story_canon") if isinstance(pack.get("story_canon"), dict) else {}
    facts = canon.get("must_retain_facts") or canon.get("mustRetainFacts") or []
    return {"_anti_ai_char_lock": facts}


@NodeRegistry.register(
    "anti_ai_finale",
    NodeMeta(
        node_type="anti_ai_finale",
        display_name="终稿 Anti-AI",
        category=NodeCategory.ANTI_AI,
        description="润色后再扫一遍疲劳词",
    ),
)
def anti_ai_finale(state: dict[str, Any]) -> dict[str, Any]:
    text = state.get("styled_text") or state.get("chapter_text") or ""
    rep = scan_fatigue(text, "default")
    return {"_anti_ai_finale": rep}


# ─── 世界（2）───


@NodeRegistry.register(
    "world_bible_all",
    NodeMeta(
        node_type="world_bible_all",
        display_name="世界观圣经",
        category=NodeCategory.WORLD,
        description="story_contract 世界观块",
    ),
)
def world_bible_all(state: dict[str, Any]) -> dict[str, Any]:
    sc = state.get("story_contract") if isinstance(state.get("story_contract"), dict) else {}
    world = sc.get("worldSetting") or sc.get("world_setting") or {}
    return _patch_context(state, world_bible=world)


@NodeRegistry.register(
    "world_characters",
    NodeMeta(
        node_type="world_characters",
        display_name="人物圣经",
        category=NodeCategory.WORLD,
        description="角色表摘要",
    ),
)
def world_characters(state: dict[str, Any]) -> dict[str, Any]:
    sc = state.get("story_contract") if isinstance(state.get("story_contract"), dict) else {}
    chars = sc.get("characters") or sc.get("characterList") or []
    return _patch_context(state, world_characters=chars)


# ─── 扩展 / 通用（3）───


@NodeRegistry.register(
    "ext_summary",
    NodeMeta(
        node_type="ext_summary",
        display_name="摘要抽取",
        category=NodeCategory.VALIDATION,
        description="从已定稿摘要拼一行预览（完整摘要仍走 aftermath）",
    ),
)
def ext_summary(state: dict[str, Any]) -> dict[str, Any]:
    return {"_ext_summary": ext_summary_from_history(state)}


@NodeRegistry.register(
    "review_timeline",
    NodeMeta(
        node_type="review_timeline",
        display_name="时间线审查",
        category=NodeCategory.REVIEW,
        description="章节号与历史摘要/近期事件时间线一致性",
    ),
)
def review_timeline(state: dict[str, Any]) -> dict[str, Any]:
    rep = _review_timeline(state)
    out = {"_review_timeline": rep}
    if not rep.get("ok"):
        notes = state.get("_review_notes")
        if not isinstance(notes, list):
            notes = []
        notes.append("[时间线] " + "; ".join(rep.get("issues") or [])[:400])
        out["_review_notes"] = notes
    return out


@NodeRegistry.register(
    "review_storyline",
    NodeMeta(
        node_type="review_storyline",
        display_name="故事线审查",
        category=NodeCategory.REVIEW,
        description="活跃故事线与任务单关键词是否体现",
    ),
)
def review_storyline(state: dict[str, Any]) -> dict[str, Any]:
    rep = _review_storyline(state)
    return {"_review_storyline": rep}


@NodeRegistry.register(
    "generic_llm",
    NodeMeta(
        node_type="generic_llm",
        display_name="通用 LLM 节点",
        category=NodeCategory.EXECUTION,
        prompt_mode=PromptMode.GENERIC,
        description="由简短描述生成的可配置 LLM 节点",
        is_configurable=True,
    ),
)
def generic_llm(state: dict[str, Any]) -> dict[str, Any]:
    cfg = state.get("_dag_node_config") or {}
    if not isinstance(cfg, dict):
        cfg = {}
    sys_p = str(cfg.get("system_prompt") or "你是小说流水线助手。")
    user_t = str(cfg.get("prompt_template") or "处理以下状态 JSON 并输出 JSON。")
    read_keys = cfg.get("state_read_keys") or ["chapter_text", "scene_plan"]
    if not isinstance(read_keys, list):
        read_keys = ["chapter_text"]
    payload = {k: state.get(k) for k in read_keys if isinstance(k, str)}
    user = user_t + "\n\n" + json.dumps(payload, ensure_ascii=False)[:24000]
    res = _gw(state).chat_completion(
        messages=[{"role": "system", "content": sys_p}, {"role": "user", "content": user}],
        response_format_json=True,
        temperature=0.2,
        agent_name="chapter_gen",
        node_name=str(state.get("_dag_node_id") or "generic_llm"),
        project_id=state.get("project_id"),
        chapter_no=state.get("chapter_no"),
    )
    try:
        obj = json.loads(res.text)
    except json.JSONDecodeError:
        obj = {"raw": res.text}
    write_keys = cfg.get("state_write_keys") or []
    out: dict[str, Any] = {"_generic_llm_result": obj}
    if isinstance(write_keys, list) and write_keys:
        key = write_keys[0] if isinstance(write_keys[0], str) else "_generic_llm_result"
        out[key] = obj
    return out
