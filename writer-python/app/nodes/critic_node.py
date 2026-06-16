"""Critic：审查正文，输出含 pass 与多维度清单的 JSON；并附疲劳词与敏感词（AC）扫描。"""

from __future__ import annotations

import json
from typing import Any

from app.chapter_length_policy import (
    CHAPTER_BODY_MAX_CHARS,
    CHAPTER_BODY_MIN_CHARS,
    chapter_char_count,
)
from app.nodes._sse import sse_llm_delta
from app.services.fatigue_scanner import scan_fatigue
from app.services.llm_gateway import LLMGateway
from app.services.critic_schema import dimension_ok, validate_critic_report
from app.services.scene_plan_beats import normalize_beats
from app.services.sensitive_scanner import scan_sensitive, should_fail_critic


def critic_node(state: dict[str, Any], *, gateway: LLMGateway) -> dict[str, Any]:
    ctx = state.get("context_pack") or {}
    plan = state.get("scene_plan") or {}
    text = state.get("chapter_text") or ""
    story = state.get("story_contract") or {}
    fatigue_profile = "default"

    sys = (
        "你是章节质量审查。对照 chapter_contract、scene_plan、context_pack.story_canon（若有），"
        "检查正文是否合格，并专项检查：人设是否与 protagonist_contract/supporting_contracts 一致，"
        "是否违背 must_retain_facts 中的事实或关系，节奏与文笔可为次要。"
        "若 context_for_review 含 unresolved_events.open_foreshadowing（可含 fs_key），"
        "评估本章是否对既有伏笔有合理推进或兑现；若明显应兑现却完全回避，violations 写明。"
        "若 story_canon 含 author_governance，必须将其 intent 与 non_negotiables 视为硬约束。"
        "若 story_canon 缺失则跳过一致性块，仅评结构与章纲贴合度。"
        "若 context_for_review 含 fan_series_digest，将其视为丛书级每章硬提示，违背则 violations 写明并倾向 pass=false。"
        "你必须输出 **dimensions** 数组，每项含 id / ok / note；id 必须从下列枚举择一："
        "outline_adherence, character_voice, canon_facts, pacing_hooks, prose_quality, fan_series_rules, "
        "author_governance, chapter_length, narrative_obligations, content_compliance, "
        "beat_coverage, anti_ai_prose。"
        "beat_coverage：对照 scene_plan.beats，主要节拍是否在正文中有体现。"
        "anti_ai_prose：模板句、空洞抒情、说明书式设定堆砌是否过多。"
        "若 context_for_review 含 narrative_obligations 或 narrative_prompt_lines（PG 任务单），"
        "须评估汇合/子文本/里程碑是否落实；收敛期若正文仍大量埋设新悬念则 narrative_obligations.ok=false。"
        "若 story_phase_rules.allowNewSubtext=false 且正文明显新增子文本式新坑，倾向 pass=false。"
        f"其中 chapter_length：正文（chapter_text）全文字符数应在约 {CHAPTER_BODY_MIN_CHARS}～{CHAPTER_BODY_MAX_CHARS} 之间"
        "（与 Ghostwriter 目标一致；明显越界则 ok=false 并在 note 写明当前约多少字）。"
        "只输出 JSON："
        '{"pass":true/false,"notes":["..."],'
        '"dimensions":[{"id":"outline_adherence","ok":true,"note":"..."}],'
        '"scores":{"coherence":0-10,"pacing":0-10,"consistency":0-10},'
        '"consistency_checks":{"protagonist_aligned":true/false,"supporting_roles_aligned":true/false,'
        '"relationships_plausible":true/false,"violations":["具体问题…"]}}'
        "violations 若无则 []；consistency 任一关键项为 false 时通常应 pass=false。"
    )
    slim_ctx = {
        "chapter_contract": ctx.get("chapter_contract"),
        "story_canon": ctx.get("story_canon"),
        "fan_series_digest": ctx.get("fan_series_digest"),
        "relationship_graph": ctx.get("relationship_graph"),
        "unresolved_events": ctx.get("unresolved_events"),
        "forbidden_moves": ctx.get("forbidden_moves"),
        "human_instruction": ctx.get("human_instruction"),
        "narrative_obligations": ctx.get("narrative_obligations"),
        "narrative_prompt_lines": ctx.get("narrative_prompt_lines"),
        "story_phase_rules": ctx.get("story_phase_rules"),
        "previously_on": ctx.get("previously_on"),
        "continuity_brief": ctx.get("continuity_brief"),
        "story_anchor": ctx.get("story_anchor"),
        "scars_and_motivations": ctx.get("scars_and_motivations"),
        "debt_due": ctx.get("debt_due"),
        "causal_chains": ctx.get("causal_chains"),
    }
    user = (
        json.dumps(
            {"context_for_review": slim_ctx, "scene_plan": plan, "chapter_text": text[:50000]},
            ensure_ascii=False,
        )[:26000]
    )
    res = gateway.chat_completion(
        messages=[{"role": "system", "content": sys}, {"role": "user", "content": user}],
        response_format_json=True,
        temperature=0.1,
        agent_name="chapter_gen",
        node_name="critic",
        project_id=state.get("project_id"),
        chapter_no=state.get("chapter_no"),
        on_delta=lambda t: sse_llm_delta("critic", t),
    )
    try:
        report = json.loads(res.text)
        if not isinstance(report, dict) or "pass" not in report:
            raise ValueError("critic_report missing pass")
    except Exception as e:
        raise RuntimeError(f"critic JSON parse failed: {e}") from e

    fatigue = scan_fatigue(text, fatigue_profile)
    report["fatigue_scan"] = fatigue
    if fatigue.get("bannedCount", 0) >= 4:
        report["pass"] = False
        notes = report.setdefault("notes", [])
        if isinstance(notes, list):
            notes.append(f"[疲劳词] 命中禁用短语 {fatigue['bannedCount']} 处，请删减套话或改写。")

    sensitive = scan_sensitive(text)
    report["sensitive_scan"] = sensitive
    compliance_ok = not should_fail_critic(sensitive)
    if should_fail_critic(sensitive):
        report["pass"] = False
        notes = report.setdefault("notes", [])
        if isinstance(notes, list):
            notes.append(
                f"[合规] AC 敏感词命中 block 级 {sensitive.get('blockCount', 0)} 处，"
                f"分类示例: {', '.join(sorted({h.get('category', '') for h in sensitive.get('hits', [])[:5]}))}。"
            )

    n_chars = chapter_char_count(text)
    ok_len = CHAPTER_BODY_MIN_CHARS <= n_chars <= CHAPTER_BODY_MAX_CHARS
    if not ok_len:
        report["pass"] = False
        notes = report.setdefault("notes", [])
        if isinstance(notes, list):
            notes.append(
                f"[篇幅硬性] 当前约 {n_chars} 字，要求约 {CHAPTER_BODY_MIN_CHARS}～{CHAPTER_BODY_MAX_CHARS} 字。"
            )
    dims = report.get("dimensions")
    if not isinstance(dims, list):
        dims = []
        report["dimensions"] = dims
    length_note = f"约{n_chars}字，目标{CHAPTER_BODY_MIN_CHARS}-{CHAPTER_BODY_MAX_CHARS}字"
    found = False
    for d in dims:
        if isinstance(d, dict) and d.get("id") == "chapter_length":
            found = True
            d["ok"] = bool(d.get("ok", True)) and ok_len
            prev = str(d.get("note") or "").strip()
            d["note"] = (length_note + ("；" + prev if prev else ""))[:500]
            break
    if not found:
        dims.append({"id": "chapter_length", "ok": ok_len, "note": length_note})

    comp_note = (
        f"敏感词扫描 disposition={sensitive.get('disposition')}, "
        f"block={sensitive.get('blockCount', 0)}, review={sensitive.get('reviewCount', 0)}"
    )
    comp_found = False
    for d in dims:
        if isinstance(d, dict) and d.get("id") == "content_compliance":
            comp_found = True
            d["ok"] = bool(d.get("ok", True)) and compliance_ok
            prev = str(d.get("note") or "").strip()
            d["note"] = (comp_note + ("；" + prev if prev else ""))[:500]
            break
    if not comp_found:
        dims.append({"id": "content_compliance", "ok": compliance_ok, "note": comp_note})

    plan_beats = normalize_beats(plan if isinstance(plan, dict) else {})
    beat_ok = True
    beat_note = ""
    if len(plan_beats) >= 4:
        llm_beat_ok = dimension_ok(report, "beat_coverage")
        if llm_beat_ok is False:
            beat_ok = False
            beat_note = "Critic beat_coverage=false，主要节拍未在正文体现"
        elif llm_beat_ok is None:
            beat_note = "未返回 beat_coverage，按章纲节拍数默认通过"
    else:
        beat_ok = False
        beat_note = f"scene_plan 节拍不足 4（当前 {len(plan_beats)}）"
    if not beat_ok:
        report["pass"] = False
        notes = report.setdefault("notes", [])
        if isinstance(notes, list):
            notes.append(f"[节拍硬性] {beat_note}")
    beat_found = False
    for d in dims:
        if isinstance(d, dict) and d.get("id") == "beat_coverage":
            beat_found = True
            d["ok"] = bool(d.get("ok", True)) and beat_ok
            prev = str(d.get("note") or "").strip()
            d["note"] = (beat_note + ("；" + prev if prev else ""))[:500]
            break
    if not beat_found:
        dims.append({"id": "beat_coverage", "ok": beat_ok, "note": beat_note or "beat gate"})

    schema_errs = validate_critic_report(report)
    if schema_errs:
        report["pass"] = False
        notes = report.setdefault("notes", [])
        if isinstance(notes, list):
            notes.append(f"[契约] {schema_errs[0]}")

    return {"critic_report": report}
