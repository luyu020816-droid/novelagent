from __future__ import annotations

import json
from typing import Any

from pydantic import BaseModel, ConfigDict, Field

from app.schemas.setup_narrative import NarrativeProposeRequest, NarrativeProposeResponse
from app.services.json_repair import validate_or_repair
from app.services.llm_gateway import LLMGateway
from app.services.prompt_registry import load_prompt
from app.skills.loader import get_series_preset


class NarrativeDomainOut(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    storylines: list[dict[str, Any]] = Field(default_factory=list)
    confluences: list[dict[str, Any]] = Field(default_factory=list)
    subtext_seeds: list[dict[str, Any]] = Field(default_factory=list, alias="subtextSeeds")


def _skill_block(skill_id: str) -> str:
    preset = get_series_preset(skill_id)
    if preset is None:
        return ""
    raw = preset.model_dump() if hasattr(preset, "model_dump") else dict(preset)
    return (
        "\n\n=== 丛书 Skill（须遵守）===\n"
        + json.dumps(raw, ensure_ascii=False, indent=2)[:12000]
        + "\n=== Skill 结束 ===\n"
    )


def run_propose(req: NarrativeProposeRequest, gateway: LLMGateway) -> NarrativeProposeResponse:
    system = load_prompt("narrative_planner_v1.md")
    skill_id = (req.writer_skill_id or "").strip()
    if skill_id:
        system = system + _skill_block(skill_id)

    payload: dict[str, Any] = {
        "genreDecision": req.genre_decision,
        "storyContract": req.story_contract,
        "firstVolumeOutline": req.first_volume_outline,
        "targetChapters": req.target_chapters,
    }
    if req.user_feedback:
        payload["userFeedback"] = req.user_feedback
    if req.previous_proposal:
        payload["previousProposal"] = req.previous_proposal

    user_msg = (
        "请根据以下输入生成叙事结构 JSON（storylines + confluences + subtextSeeds）：\n"
        + json.dumps(payload, ensure_ascii=False, indent=2)
    )
    gr = gateway.chat_completion(
        messages=[
            {"role": "system", "content": system},
            {"role": "user", "content": user_msg},
        ],
        response_format_json=True,
        temperature=0.35,
        agent_name="narrative_planner",
        node_name="propose",
        project_id=req.project_id,
    )
    hint = json.dumps(NarrativeDomainOut.model_json_schema(), ensure_ascii=False)[:8000]
    out, _ = validate_or_repair(
        gr.text,
        NarrativeDomainOut,
        gateway,
        agent_name="narrative_planner",
        repair_context=hint,
        project_id=req.project_id,
    )
    domain = out.model_dump(by_alias=True)
    reply = "已根据故事契约生成故事结构草案。"
    if req.user_feedback:
        reply = "已根据您的反馈修订故事结构草案。"
    return NarrativeProposeResponse(narrative_domain=domain, assistant_reply=reply)
