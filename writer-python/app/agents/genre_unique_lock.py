"""Skill / 故事线定稿后的唯一题材锁定（单轮 JSON，无多备选 Scout 路径）。"""

from __future__ import annotations

import json
from collections.abc import Callable

from app.schemas.genre import GenreRecommendRequest
from app.services.genre_data import load_genre_context_bundle
from app.services.genre_story_hook import format_story_hook_block
from app.services.llm_gateway import LLMGateway
from app.services.prompt_registry import load_prompt


def run(
    req: GenreRecommendRequest,
    gateway: LLMGateway,
    on_llm_delta: Callable[[str], None] | None = None,
) -> str:
    """返回模型原始 JSON 文本，供 validate_or_repair → GenreDecisionContract。"""
    system = load_prompt("genre_unique_lock_v1.md")
    pref = json.dumps(req.model_dump(by_alias=True, exclude_none=True), ensure_ascii=False)
    bundle = load_genre_context_bundle()
    user_msg = (
        "【用户与平台偏好 JSON】\n"
        f"{pref}\n\n"
        f"=== genre_rules 与题材上下文（择要遵守）===\n{bundle}"
        f"{format_story_hook_block(req)}"
    )
    gr = gateway.chat_completion(
        messages=[
            {"role": "system", "content": system},
            {"role": "user", "content": user_msg},
        ],
        response_format_json=True,
        temperature=0.22,
        agent_name="genre_unique_lock",
        node_name="main",
        project_id=req.project_id,
        on_delta=on_llm_delta,
    )
    return gr.text
