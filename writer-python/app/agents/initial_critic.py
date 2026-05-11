from __future__ import annotations

import json
from collections.abc import Callable
from typing import Any

from app.schemas.chapter import ChapterContract, InitialCriticOutput, OutlineArchitectOutput
from app.schemas.story import StoryContract
from app.services.json_repair import validate_or_repair
from app.services.llm_gateway import LLMGateway
from app.services.prompt_registry import load_prompt


def run(
    genre_decision: dict[str, Any],
    story_contract: StoryContract,
    outline: OutlineArchitectOutput,
    gateway: LLMGateway,
    project_id: str | None,
    on_llm_delta: Callable[[str], None] | None = None,
) -> list[ChapterContract]:
    system = load_prompt("initial_critic_v1.md")
    gd = json.dumps(genre_decision, ensure_ascii=False)
    sc = story_contract.model_dump_json(by_alias=True)
    draft = json.dumps(
        [c.model_dump(by_alias=True) for c in outline.chapters],
        ensure_ascii=False,
    )
    user_msg = (
        f"genre_decision JSON:\n{gd}\n\nstory_contract JSON:\n{sc}\n\n"
        f"first_volume_outline:\n{outline.first_volume_outline}\n\n"
        f"chapters JSON 数组（草案）:\n{draft}\n\n"
        f"请严格按系统指令只输出 JSON（修订后的 chapters，恰好 20 章）。"
    )
    gr = gateway.chat_completion(
        messages=[
            {"role": "system", "content": system},
            {"role": "user", "content": user_msg},
        ],
        response_format_json=True,
        temperature=0.2,
        agent_name="initial_critic",
        node_name="main",
        project_id=project_id,
        on_delta=on_llm_delta,
    )
    hint = json.dumps(InitialCriticOutput.model_json_schema(), ensure_ascii=False)[:14000]
    out, _ = validate_or_repair(
        gr.text,
        InitialCriticOutput,
        gateway,
        agent_name="initial_critic",
        repair_context=hint,
        project_id=project_id,
        on_llm_delta=on_llm_delta,
    )
    return out.chapters
