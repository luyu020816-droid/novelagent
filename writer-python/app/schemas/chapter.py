from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field, field_validator


def _to_camel(snake: str) -> str:
    parts = snake.split("_")
    return parts[0] + "".join(p.title() for p in parts[1:])


_CHAPTER_CONFIG = ConfigDict(
    alias_generator=_to_camel,
    populate_by_name=True,
    serialize_by_alias=True,
    extra="ignore",
)


class ChapterContract(BaseModel):
    """对齐 15plan §6.3；camelCase 出站供 Java / 前端。"""

    model_config = _CHAPTER_CONFIG

    chapter_no: int = Field(ge=1, le=200, description="章节序号，Day 6 固定 1～20")
    title_hint: str = Field(description="章标题提示，可为暂定")
    chapter_goal: str = Field(description="本章叙事目标")
    must_cover: list[str] = Field(default_factory=list, description="本章必须覆盖的情节点")
    forbidden_moves: list[str] = Field(
        default_factory=list,
        description="本章不得发生的剧情禁令（可承接 Story Contract）",
    )
    payoff: str = Field(description="本章爽点 / 情绪兑现")
    cliffhanger: str = Field(description="章末钩子")


class OutlineArchitectOutput(BaseModel):
    """初始化阶段：一段式剧情走向；`chapters` 由 outline_chapter_draft 填充后供 initial_critic 修订。"""

    model_config = _CHAPTER_CONFIG

    first_volume_outline: str = Field(
        description="约 500～1000 字一段式剧情走向：起承转合、主线赌注、对立升级与卷末落点；不分章、不写对白",
    )
    chapters: list[ChapterContract] = Field(
        default_factory=list,
        description="逐章草案（outline_chapter_draft → initial_critic）；outline_architect 单独调用时为空",
    )

    @field_validator("first_volume_outline")
    @classmethod
    def plot_direction_length_band(cls, v: str) -> str:
        s = (v or "").strip()
        n = len(s)
        if n < 250:
            raise ValueError("first_volume_outline 过短：目标约 500～1000 字的一段式走向")
        if n > 6000:
            raise ValueError("first_volume_outline 过长：请保持一段式剧情走向，勿写成逐章纲")
        return s


class InitialCriticOutput(BaseModel):
    """Initial Critic 修订后的章契约列表。"""

    model_config = _CHAPTER_CONFIG

    chapters: list[ChapterContract] = Field(description="修订后仍须恰好 20 章")

    @field_validator("chapters")
    @classmethod
    def exactly_twenty_sequential(cls, v: list[ChapterContract]) -> list[ChapterContract]:
        if len(v) != 20:
            raise ValueError("chapters must contain exactly 20 items")
        nos = [c.chapter_no for c in v]
        expected = list(range(1, 21))
        if sorted(nos) != expected:
            raise ValueError(f"chapter_no must be 1..20 exactly once each, got {nos}")
        return v
