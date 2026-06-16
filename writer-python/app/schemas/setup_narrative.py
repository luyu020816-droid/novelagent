from __future__ import annotations

from typing import Any

from pydantic import BaseModel, ConfigDict, Field


def _to_camel(snake: str) -> str:
    parts = snake.split("_")
    return parts[0] + "".join(p.title() for p in parts[1:])


class NarrativeProposeRequest(BaseModel):
    model_config = ConfigDict(alias_generator=_to_camel, populate_by_name=True, extra="ignore")

    project_id: str = Field(alias="projectId")
    genre_decision: dict[str, Any] = Field(default_factory=dict, alias="genreDecision")
    story_contract: dict[str, Any] = Field(default_factory=dict, alias="storyContract")
    first_volume_outline: str = Field(default="", alias="firstVolumeOutline")
    target_chapters: int = Field(default=100, alias="targetChapters", ge=1)
    user_feedback: str | None = Field(default=None, alias="userFeedback")
    previous_proposal: dict[str, Any] | None = Field(default=None, alias="previousProposal")
    writer_skill_id: str | None = Field(default=None, alias="writerSkillId")


class NarrativeProposeResponse(BaseModel):
    model_config = ConfigDict(
        alias_generator=_to_camel,
        populate_by_name=True,
        serialize_by_alias=True,
        extra="ignore",
    )

    narrative_domain: dict[str, Any] = Field(alias="narrativeDomain")
    assistant_reply: str = Field(default="已生成故事结构草案。", alias="assistantReply")
