"""Scene Director：在 Planner 之后收紧本场人物/空间与镜头约束，合并进 scene_plan.director。"""

from __future__ import annotations

import json
from typing import Any

from app.nodes._sse import sse_llm_delta
from app.services.llm_gateway import LLMGateway


def scene_director_node(state: dict[str, Any], *, gateway: LLMGateway) -> dict[str, Any]:
    plan = state.get("scene_plan")
    if not isinstance(plan, dict):
        plan = {}
    sys = (
        "你是场记 Scene Director。根据 scene_plan（含 beats）与 chapter_contract 人物/地点，"
        "输出严格 JSON（不要 markdown）："
        '{"cast_filter":["本场应出现或重点描写的人物名或代号"],"location_anchor":"主要空间/地点",'
        '"shot_constraints":["镜头或节奏约束短句，2～5条"],"avoid":["本章应弱化或不要展开的信息"]}'
    )
    user = json.dumps(
        {
            "scene_plan": plan,
            "chapter_contract": state.get("chapter_contract") or {},
        },
        ensure_ascii=False,
    )[:18000]
    res = gateway.chat_completion(
        messages=[{"role": "system", "content": sys}, {"role": "user", "content": user}],
        response_format_json=True,
        temperature=0.2,
        agent_name="chapter_gen",
        node_name="scene_director",
        project_id=state.get("project_id"),
        chapter_no=state.get("chapter_no"),
        on_delta=lambda t: sse_llm_delta("scene_director", t),
    )
    try:
        director = json.loads(res.text)
        if not isinstance(director, dict):
            raise ValueError("director not object")
    except Exception as e:
        raise RuntimeError(f"scene_director JSON parse failed: {e}") from e
    merged = {**plan, "director": director}
    return {"scene_plan": merged}
