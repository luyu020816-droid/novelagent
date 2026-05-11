from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field, field_validator


def _to_camel(snake: str) -> str:
    parts = snake.split("_")
    return parts[0] + "".join(p.title() for p in parts[1:])


# 请求体：不要用 serialize_by_alias，否则部分 pydantic/FastAPI 版本解析入参异常 → 422
_GENRE_REQUEST_CONFIG = ConfigDict(
    alias_generator=_to_camel,
    populate_by_name=True,
    extra="ignore",
)

# 响应体：序列化为 camelCase，供 Java / 前端读取
_GENRE_RESPONSE_CONFIG = ConfigDict(
    alias_generator=_to_camel,
    populate_by_name=True,
    serialize_by_alias=True,
    extra="ignore",
)


class GenreRecommendRequest(BaseModel):
    """与 Java / 前端一致的 camelCase JSON。"""

    model_config = _GENRE_REQUEST_CONFIG

    target_platform: str = Field(description="目标平台展示名，如 番茄、起点")
    gender_channel: str = Field(description="男频 / 女频 等")
    preferred_genres: list[str] = Field(default_factory=list)
    avoid: list[str] = Field(default_factory=list)
    writing_strength: list[str] = Field(default_factory=list)
    risk_preference: str = Field(default="medium", description="low | medium | high")
    story_hook: str | None = Field(
        default=None,
        description="可选：作者一两句故事线/创意；若填写则各 Agent 须与之对齐",
    )
    project_id: str | None = Field(default=None, description="可选，用于 llm_usage_log")


class SelectedDirection(BaseModel):
    model_config = _GENRE_RESPONSE_CONFIG

    channel: str
    genre: str
    sub_tags: list[str] = Field(default_factory=list)
    reason: str


class CandidateRanking(BaseModel):
    model_config = _GENRE_RESPONSE_CONFIG

    genre: str
    heat_score: float = Field(ge=0, le=10)
    competition_score: float = Field(ge=0, le=10)
    payoff_density: float = Field(ge=0, le=10)
    serialization_score: float = Field(ge=0, le=10)
    originality_space: float = Field(ge=0, le=10)
    token_cost_level: str
    final_score: float = Field(ge=0, le=10)
    recommend_reason: str = Field(description="推荐理由")
    risk_note: str = Field(description="该题材风险说明")


class GenreDecisionContract(BaseModel):
    model_config = _GENRE_RESPONSE_CONFIG

    selected_direction: SelectedDirection
    candidate_rankings: list[CandidateRanking]
    recommended_core_hook: str
    risk_notes: list[str] = Field(default_factory=list)

    @field_validator("candidate_rankings")
    @classmethod
    def three_candidates(cls, v: list[CandidateRanking]) -> list[CandidateRanking]:
        if len(v) != 3:
            raise ValueError("candidateRankings must contain exactly 3 items")
        return v


class ScoutCandidate(BaseModel):
    model_config = _GENRE_RESPONSE_CONFIG

    genre: str
    pitch: str


class ScoutOutput(BaseModel):
    model_config = _GENRE_RESPONSE_CONFIG

    candidates: list[ScoutCandidate]

    @field_validator("candidates")
    @classmethod
    def three(cls, v: list[ScoutCandidate]) -> list[ScoutCandidate]:
        if len(v) != 3:
            raise ValueError("candidates must have length 3")
        return v


class StrategistCandidate(BaseModel):
    model_config = _GENRE_RESPONSE_CONFIG

    genre: str
    sub_tags: list[str]
    hook_line: str
    pitch: str


class StrategistOutput(BaseModel):
    model_config = _GENRE_RESPONSE_CONFIG

    candidates: list[StrategistCandidate]
    recommended_core_hook: str

    @field_validator("candidates")
    @classmethod
    def three(cls, v: list[StrategistCandidate]) -> list[StrategistCandidate]:
        if len(v) != 3:
            raise ValueError("candidates must have length 3")
        return v
