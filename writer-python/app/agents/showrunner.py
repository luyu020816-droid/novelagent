from __future__ import annotations

import json
from collections.abc import Callable
from typing import Any

from app.schemas.story import NovelSeed
from app.services.json_repair import validate_or_repair
from app.services.llm_gateway import LLMGateway
from app.services.prompt_registry import load_prompt


def run(
    genre_decision: dict[str, Any],
    gateway: LLMGateway,
    project_id: str | None,
    on_llm_delta: Callable[[str], None] | None = None,
) -> NovelSeed:
    system = load_prompt("showrunner_v1.md")
    gd = json.dumps(genre_decision, ensure_ascii=False)
    brief = genre_decision.get("authorWizardBrief") or genre_decision.get("author_wizard_brief")
    brief_block = ""
    if isinstance(brief, str) and brief.strip():
        brief_block = (
            "\n\n【作者开机向导备注 — 必须融入书名方向、开局冲突与整体基调，不得忽略】\n"
            + brief.strip()[:8000]
            + "\n"
        )
    user_msg = f"genre_decision JSON:\n{gd}{brief_block}\n请严格按系统指令只输出 JSON。"
    gr = gateway.chat_completion(
        messages=[
            {"role": "system", "content": system},
            {"role": "user", "content": user_msg},
        ],
        response_format_json=True,
        temperature=0.35,
        agent_name="showrunner",
        node_name="main",
        project_id=project_id,
        on_delta=on_llm_delta,
    )
    hint = json.dumps(NovelSeed.model_json_schema(), ensure_ascii=False)[:12000]
    out, _ = validate_or_repair(
        gr.text,
        NovelSeed,
        gateway,
        agent_name="showrunner",
        repair_context=hint,
        project_id=project_id,
        on_llm_delta=on_llm_delta,
    )
    return out
