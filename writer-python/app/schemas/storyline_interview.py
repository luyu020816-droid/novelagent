"""路径 B：多轮互动采访 —— 请求 / 响应 Schema。"""

from __future__ import annotations

from typing import Any, Literal

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator


def _to_camel(snake: str) -> str:
    parts = snake.split("_")
    return parts[0] + "".join(p.title() for p in parts[1:])


_REQUEST_CFG = ConfigDict(
    alias_generator=_to_camel,
    populate_by_name=True,
    extra="ignore",
)

_RESPONSE_CFG = ConfigDict(
    alias_generator=_to_camel,
    populate_by_name=True,
    serialize_by_alias=True,
    extra="ignore",
)


class ChatTurn(BaseModel):
    """单轮对话：role + content。"""

    model_config = ConfigDict(extra="ignore")

    role: Literal["user", "assistant", "system"]
    content: str = Field(min_length=1)


class GenreInterviewRequest(BaseModel):
    """POST /api/writer/genre/interview 入站。"""

    model_config = _REQUEST_CFG

    chat_history: list[ChatTurn] = Field(
        min_length=1,
        description="完整对话历史，含本轮用户消息",
    )
    project_id: str | None = Field(default=None, description="可选，写入 llm_usage_log")


class InterviewerResponse(BaseModel):
    """采访 Agent 出站（严格 JSON，经 LLM + Pydantic 校验）。"""

    model_config = _RESPONSE_CFG

    status: Literal["asking", "complete"]
    reply_to_user: str = Field(description="追问话术或完成结语")
    final_summary: str | None = Field(default=None, description="约100字故事线确认，仅 complete")
    core_settings: dict[str, Any] | None = Field(
        default=None,
        description="主角、核心冲突、世界观键值等，仅 complete",
    )

    @field_validator("reply_to_user")
    @classmethod
    def strip_reply(cls, v: str) -> str:
        return v.strip()

    @model_validator(mode="after")
    def validate_status_payload(self) -> InterviewerResponse:
        if self.status == "complete":
            if not self.final_summary or not str(self.final_summary).strip():
                raise ValueError("complete 时必须提供非空 final_summary")
            if self.core_settings is None or len(self.core_settings) == 0:
                raise ValueError("complete 时必须提供非空 core_settings 对象")
        else:
            object.__setattr__(self, "final_summary", None)
            object.__setattr__(self, "core_settings", None)
        return self
