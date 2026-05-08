from pydantic import BaseModel, ConfigDict, Field


class TestAgentOutput(BaseModel):
    model_config = ConfigDict(extra="ignore")

    ok: bool = Field(description="Whether the agent completed successfully")
    message: str = Field(description="Short human-readable summary")
    items: list[str] = Field(default_factory=list, description="Sample string items")


class TestAgentRequest(BaseModel):
    user_hint: str | None = Field(
        default=None,
        description="Optional user text merged into the prompt as context.",
    )
