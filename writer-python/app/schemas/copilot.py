"""通用写作 Copilot 对话（场景化系统提示）。"""

from __future__ import annotations

from typing import Literal

from pydantic import BaseModel, Field


class CopilotChatMessage(BaseModel):
    role: Literal["user", "assistant"]
    content: str = Field(max_length=32000)


class CopilotChatRequest(BaseModel):
    model_config = {"populate_by_name": True}

    project_id: str = Field(alias="projectId", min_length=1)
    scene: Literal["init_wizard", "outline_edit", "chapter_coach"]
    messages: list[CopilotChatMessage] = Field(min_length=1)
    context_blob: str | None = Field(default=None, alias="contextBlob", max_length=48000)


class CopilotChatResponse(BaseModel):
    reply: str
