from __future__ import annotations

import json
from collections.abc import Callable

from app.schemas.genre import GenreRecommendRequest, ScoutOutput, StrategistOutput
from app.services.genre_data import load_genre_context_bundle
from app.services.genre_story_hook import format_story_hook_block
from app.services.llm_gateway import LLMGateway
from app.services.prompt_registry import load_prompt


def run(
    req: GenreRecommendRequest,
    scout: ScoutOutput,
    strategist: StrategistOutput,
    gateway: LLMGateway,
    on_llm_delta: Callable[[str], None] | None = None,
) -> str:
    """返回模型原始 JSON 文本，供上层 Pydantic + repair 校验。"""
    system = load_prompt("market_fit_scorer_v1.md")
    pref = json.dumps(req.model_dump(by_alias=True, exclude_none=True), ensure_ascii=False)
    scout_json = scout.model_dump_json(by_alias=True)
    strat_json = strategist.model_dump_json(by_alias=True)
    bundle = load_genre_context_bundle()
    user_msg = (
        f"用户偏好 JSON:\n{pref}\n\n"
        f"Scout JSON:\n{scout_json}\n\n"
        f"Strategist JSON:\n{strat_json}\n\n"
        f"=== genre_rules 与题材上下文 ===\n{bundle}"
        f"{format_story_hook_block(req)}"
    )
    gr = gateway.chat_completion(
        messages=[
            {"role": "system", "content": system},
            {"role": "user", "content": user_msg},
        ],
        response_format_json=True,
        temperature=0.25,
        agent_name="market_fit_scorer",
        node_name="main",
        project_id=req.project_id,
        on_delta=on_llm_delta,
    )
    return gr.text
