"""Critic：审查正文，输出含 pass 与多维度清单的 JSON；并附疲劳词扫描。"""

from __future__ import annotations

import json
from typing import Any

from app.nodes._sse import sse_llm_delta
from app.services.fatigue_scanner import scan_fatigue
from app.services.llm_gateway import LLMGateway


def critic_node(state: dict[str, Any], *, gateway: LLMGateway) -> dict[str, Any]:
    ctx = state.get("context_pack") or {}
    plan = state.get("scene_plan") or {}
    text = state.get("chapter_text") or ""
    story = state.get("story_contract") or {}
    fan = (state.get("fan_series_preset") or story.get("fanSeriesPreset") or "").strip() or None
    fatigue_profile = "default"
    if fan:
        fatigue_profile = "default"

    sys = (
        "你是章节质量审查。对照 chapter_contract、scene_plan、context_pack.story_canon（若有），"
        "检查正文是否合格，并专项检查：人设是否与 protagonist_contract/supporting_contracts 一致，"
        "是否违背 must_retain_facts 中的事实或关系，节奏与文笔可为次要。"
        "若 story_canon 含 author_governance，必须将其 intent 与 non_negotiables 视为硬约束。"
        "若 story_canon 缺失则跳过一致性块，仅评结构与章纲贴合度。"
        "若 context_for_review 含 fan_series_digest，将其视为丛书级每章硬提示，违背则 violations 写明并倾向 pass=false。"
        "你必须输出 **dimensions** 数组，每项含 id / ok / note；id 必须从下列枚举择一："
        "outline_adherence, character_voice, canon_facts, pacing_hooks, prose_quality, fan_series_rules, author_governance。"
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
        "forbidden_moves": ctx.get("forbidden_moves"),
        "human_instruction": ctx.get("human_instruction"),
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

    return {"critic_report": report}
