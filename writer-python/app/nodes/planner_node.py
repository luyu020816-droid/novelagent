"""Planner：生成 Scene Plan（JSON）。"""

from __future__ import annotations

import json
from typing import Any

from app.chapter_length_policy import CHAPTER_BODY_MAX_CHARS, CHAPTER_BODY_MIN_CHARS
from app.nodes._sse import sse_llm_delta
from app.services.llm_gateway import LLMGateway
from app.services.memory_engine_constants import MIN_SCENE_PLAN_BEATS
from app.services.scene_plan_beats import enforce_scene_plan_beats


def planner_node(state: dict[str, Any], *, gateway: LLMGateway) -> dict[str, Any]:
    ctx = state.get("context_pack") or {}
    sys = (
        "你是网文章节场景规划师。根据 story_contract、chapter_contract、context_pack.story_canon（设定契约）、"
        "context_pack.fan_series_digest（若有：丛书级每章提醒）、"
        "recent_summaries、context_pack.vector_context（向量召回正文片段）、"
        "context_pack.relationship_graph（人物关系网）、"
        "context_pack.unresolved_events（未回收伏笔 open_foreshadowing + recent_events），"
        "输出严格 JSON（不要 markdown）："
        '{"beats":[{"beat":"string","goal":"string"}],"scene_goal":"string","tension":"string","pov":"string"}'
        "beats 必须 **4～8 条**，每条 beat+goal 可指导一段成文；禁止少于 4 条。"
        f"节拍体量应对齐约 {CHAPTER_BODY_MIN_CHARS}～{CHAPTER_BODY_MAX_CHARS} 字成文（勿写成几百字的极简梗概）。"
        "若 chapter_contract.must_cover 有条目，应分配到各 beat 的 goal 中。"
        "若 context_pack 含 previously_on（前情提要）须承接已定稿章节因果；"
        "若含 continuity_brief 或 narrative_obligations，须落实 summaryLine、phaseRules.guidanceLine 与其中条目；"
        "若含 narrative_prompt_lines（格式化故事线/汇合提示），汇合章/逾期子文本须在 beats 中显式安排；"
        "收敛期/终局期不得规划新子文本或新故事线。"
        "若含 story_phase_rules，须服从 allowNewSubtext / allowNewForeshadowing。"
        "须与 story_canon.must_retain_facts、protagonist_contract、supporting_contracts 一致，不得编造矛盾人设或关系；"
        "若 context_pack 含 author_confirmed_chapter_plan，scene_plan 必须与之对齐。"
        "须与向量召回及图谱召回一致，人物关系不得与 relationship_graph.edges 冲突；"
        "优先呼应 unresolved_events 中的悬念。"
    )
    user = json.dumps(ctx, ensure_ascii=False)[:24000]
    res = gateway.chat_completion(
        messages=[{"role": "system", "content": sys}, {"role": "user", "content": user}],
        response_format_json=True,
        temperature=0.25,
        agent_name="chapter_gen",
        node_name="planner",
        project_id=state.get("project_id"),
        chapter_no=state.get("chapter_no"),
        on_delta=lambda t: sse_llm_delta("planner", t),
    )
    try:
        scene_plan = json.loads(res.text)
        if not isinstance(scene_plan, dict):
            raise ValueError("scene_plan not object")
    except Exception as e:
        raise RuntimeError(f"planner JSON parse failed: {e}") from e
    cc = ctx.get("chapter_contract") if isinstance(ctx.get("chapter_contract"), dict) else {}
    scene_plan = enforce_scene_plan_beats(scene_plan, cc)
    beats = scene_plan.get("beats") or []
    if not isinstance(beats, list) or len(beats) < MIN_SCENE_PLAN_BEATS:
        raise RuntimeError(f"planner beats<{MIN_SCENE_PLAN_BEATS} after enforce")
    return {"scene_plan": scene_plan}
