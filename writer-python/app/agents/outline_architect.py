from __future__ import annotations

import json
from collections.abc import Callable
from typing import Any

from app.schemas.chapter import OutlineArchitectOutput
from app.schemas.story import StoryContract
from app.services.json_repair import validate_or_repair
from app.services.llm_gateway import LLMGateway
from app.services.prompt_registry import load_prompt


def run(
    genre_decision: dict[str, Any],
    story_contract: StoryContract,
    gateway: LLMGateway,
    project_id: str | None,
    on_llm_delta: Callable[[str], None] | None = None,
) -> OutlineArchitectOutput:
    system = load_prompt("outline_architect_v1.md")
    gd = json.dumps(genre_decision, ensure_ascii=False)
    sc = story_contract.model_dump_json(by_alias=True)
    user_msg = (
        f"genre_decision JSON:\n{gd}\n\nstory_contract JSON:\n{sc}\n\n"
        f"请严格按系统指令只输出 JSON（含 first_volume_outline 与恰好 20 章 chapters）。"
    )
    gr = gateway.chat_completion(
        messages=[
            {"role": "system", "content": system},
            {"role": "user", "content": user_msg},
        ],
        response_format_json=True,
        temperature=0.25,
        agent_name="outline_architect",
        node_name="main",
        project_id=project_id,
        on_delta=on_llm_delta,
    )
    hint = json.dumps(OutlineArchitectOutput.model_json_schema(), ensure_ascii=False)[:14000]
    out, _ = validate_or_repair(
        gr.text,
        OutlineArchitectOutput,
        gateway,
        agent_name="outline_architect",
        repair_context=hint,
        project_id=project_id,
        on_llm_delta=on_llm_delta,
    )
    return out
