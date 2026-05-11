from __future__ import annotations

import json
from collections.abc import Callable
from typing import Any

from app.schemas.story import CharacterDesignerOutput, NovelSeed
from app.services.json_repair import validate_or_repair
from app.services.llm_gateway import LLMGateway
from app.services.prompt_registry import load_prompt


def run(
    genre_decision: dict[str, Any],
    novel_seed: NovelSeed,
    gateway: LLMGateway,
    project_id: str | None,
    on_llm_delta: Callable[[str], None] | None = None,
) -> CharacterDesignerOutput:
    system = load_prompt("character_designer_v1.md")
    gd = json.dumps(genre_decision, ensure_ascii=False)
    ns = novel_seed.model_dump_json(by_alias=True)
    user_msg = f"genre_decision JSON:\n{gd}\n\nnovel_seed JSON:\n{ns}\n\n请严格按系统指令只输出 JSON。"
    gr = gateway.chat_completion(
        messages=[
            {"role": "system", "content": system},
            {"role": "user", "content": user_msg},
        ],
        response_format_json=True,
        temperature=0.35,
        agent_name="character_designer",
        node_name="main",
        project_id=project_id,
        on_delta=on_llm_delta,
    )
    hint = json.dumps(CharacterDesignerOutput.model_json_schema(), ensure_ascii=False)[:12000]
    out, _ = validate_or_repair(
        gr.text,
        CharacterDesignerOutput,
        gateway,
        agent_name="character_designer",
        repair_context=hint,
        project_id=project_id,
        on_llm_delta=on_llm_delta,
    )
    return out
