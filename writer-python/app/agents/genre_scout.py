from __future__ import annotations

import json

from app.schemas.genre import GenreRecommendRequest, ScoutOutput
from app.services.genre_data import load_genre_context_bundle, load_platform_snippet
from app.services.llm_gateway import LLMGateway
from app.services.prompt_registry import load_prompt


def run(req: GenreRecommendRequest, gateway: LLMGateway) -> ScoutOutput:
    system = load_prompt("genre_scout_v1.md")
    pref = json.dumps(req.model_dump(by_alias=True, exclude_none=True), ensure_ascii=False)
    plat = load_platform_snippet(req.target_platform)
    bundle = load_genre_context_bundle()
    user_msg = (
        f"用户偏好 JSON:\n{pref}\n\n"
        f"=== 当前平台摘要 YAML ===\n{plat}\n\n"
        f"=== 静态数据包 ===\n{bundle}"
    )
    gr = gateway.chat_completion(
        messages=[
            {"role": "system", "content": system},
            {"role": "user", "content": user_msg},
        ],
        response_format_json=True,
        temperature=0.35,
        agent_name="genre_scout",
        node_name="main",
        project_id=req.project_id,
    )
    return ScoutOutput.model_validate_json(gr.text)
