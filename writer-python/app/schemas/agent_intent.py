"""POST /api/writer/agent/intent-preview — 自然语言 → 建议动作列表。"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


class AgentIntentRequest(BaseModel):
    model_config = {"populate_by_name": True}

    project_id: str = Field(alias="projectId", min_length=1)
    message: str = Field(min_length=1, max_length=8000)


class SuggestedAction(BaseModel):
    action: str
    detail: str


class AgentIntentResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)

    suggested_actions: list[SuggestedAction] = Field(alias="suggestedActions")
