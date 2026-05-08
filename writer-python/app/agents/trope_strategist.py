from __future__ import annotations

import json

from app.schemas.genre import GenreRecommendRequest, ScoutOutput, StrategistOutput
from app.services.genre_data import load_genre_context_bundle
from app.services.llm_gateway import LLMGateway
from app.services.prompt_registry import load_prompt


def run(req: GenreRecommendRequest, scout: ScoutOutput, gateway: LLMGateway) -> StrategistOutput:
    system = load_prompt("trope_strategist_v1.md")
    pref = json.dumps(req.model_dump(by_alias=True, exclude_none=True), ensure_ascii=False)
    scout_json = scout.model_dump_json(by_alias=True)
    bundle = load_genre_context_bundle()
    user_msg = (
        f"用户偏好 JSON:\n{pref}\n\n"
        f"Scout 输出 JSON:\n{scout_json}\n\n"
        f"=== 静态数据包 ===\n{bundle}"
    )
    gr = gateway.chat_completion(
        messages=[
            {"role": "system", "content": system},
            {"role": "user", "content": user_msg},
        ],
        response_format_json=True,
        temperature=0.35,
        agent_name="trope_strategist",
        node_name="main",
        project_id=req.project_id,
    )
    return StrategistOutput.model_validate_json(gr.text)
