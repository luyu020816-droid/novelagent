"""Planner：生成 Scene Plan（JSON）。"""

from __future__ import annotations

import json
from typing import Any

from app.nodes._sse import sse_llm_delta
from app.services.llm_gateway import LLMGateway


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
        "须与 story_canon.must_retain_facts、protagonist_contract、supporting_contracts 一致，不得编造矛盾人设或关系；"
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
    return {"scene_plan": scene_plan}
