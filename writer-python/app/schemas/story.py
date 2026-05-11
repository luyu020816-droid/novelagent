from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator

from app.schemas.chapter import ChapterContract


def _to_camel(snake: str) -> str:
    parts = snake.split("_")
    return parts[0] + "".join(p.title() for p in parts[1:])


_STORY_REQUEST_CONFIG = ConfigDict(
    alias_generator=_to_camel,
    populate_by_name=True,
    extra="ignore",
)

_STORY_RESPONSE_CONFIG = ConfigDict(
    alias_generator=_to_camel,
    populate_by_name=True,
    serialize_by_alias=True,
    extra="ignore",
)


# --- HTTP：init-novel 入站 ---


class InitNovelRequest(BaseModel):
    """Java 传入 projectId + 题材决策原始 JSON（通常为 genre_decision_contracts.raw_json）。"""

    model_config = _STORY_REQUEST_CONFIG

    project_id: str = Field(min_length=1)
    genre_decision: dict = Field(description="Genre Decision Contract JSON 对象")
    fan_series_preset: str | None = Field(
        default=None,
        description="丛书预设 ID（如 hp_fan），合并进 Story Contract 真源",
    )
    wizard_notes: str | None = Field(
        default=None,
        description="开机向导/作者备注：并入 genre_decision.authorWizardBrief 供整条初始化流水线使用",
    )


# --- Novel Seed（Showrunner 产出，对齐 Day 5「Novel Seed 必须包含」） ---


class NovelSeed(BaseModel):
    model_config = _STORY_RESPONSE_CONFIG

    title_candidates: list[str] = Field(min_length=1, description="标题候选")
    target_reader: str = Field(description="目标读者画像")
    core_selling_point: str = Field(description="核心卖点 / 一句话钩子")
    protagonist_archetype: str = Field(description="主角原型（如底层逆袭技工）")
    golden_finger: str = Field(description="金手指设定（来源、边界、代价倾向）")
    commercial_payoffs: list[str] = Field(
        default_factory=list,
        description="商业爽点标签（打脸、升级、资源反差等）",
    )
    opening_conflict: str = Field(description="开篇冲突：第一场矛盾的诱因与张力")
    tone: str = Field(description="叙事基调，如冷幽默热血、压抑写实等")


# --- Character Designer ---


class ProtagonistProfile(BaseModel):
    """对齐 15plan 6.2 protagonist，并显式保留金手指字段以便前端展示。"""

    model_config = _STORY_RESPONSE_CONFIG

    name: str = Field(description="主角称呼（可用暂定名）")
    desire: str = Field(description="表层欲望")
    weakness: str = Field(description="人性弱点或羁绊")
    secret: str = Field(description="秘密或隐患")
    growth_arc: str = Field(description="成长弧线摘要")
    golden_finger: str = Field(description="金手指：边界、代价、使用禁忌")


class SupportingCharacter(BaseModel):
    model_config = _STORY_RESPONSE_CONFIG

    name: str
    role: str = Field(description="叙事功能：导师 / 对照组 / 反派雏形 / 情感锚点等")
    relationship_to_protagonist: str = Field(description="与主角关系")
    one_line_hook: str = Field(description="一句话记忆点")


class CharacterDesignerOutput(BaseModel):
    model_config = _STORY_RESPONSE_CONFIG

    protagonist: ProtagonistProfile
    supporting_characters: list[SupportingCharacter] = Field(
        default_factory=list,
        description="核心配角，建议 2～5 人",
    )

    @field_validator("supporting_characters")
    @classmethod
    def supporting_bounds(cls, v: list[SupportingCharacter]) -> list[SupportingCharacter]:
        if len(v) > 8:
            raise ValueError("supporting_characters must have at most 8 items for Day 5")
        return v


# --- Worldbuilder ---


class StyleGuide(BaseModel):
    model_config = _STORY_RESPONSE_CONFIG

    narrative_voice: str = Field(default="", description="叙事人称与语气")
    pacing: str = Field(default="", description="节奏偏好")
    dialogue_ratio: str = Field(default="", description="对话密度倾向")
    taboo_topics: list[str] = Field(default_factory=list, description="叙事层面尽量避免的话题")


class WorldbuilderOutput(BaseModel):
    model_config = _STORY_RESPONSE_CONFIG

    world_rules: list[str] = Field(description="世界底层规则（物理/社会/组织）")
    ability_rules: list[str] = Field(description="能力/系统运作规则与边界")
    forbidden_moves: list[str] = Field(description="禁区：剧情或设定上禁止发生的事")
    style_guide: StyleGuide = Field(default_factory=StyleGuide)
    first_volume_direction: str = Field(description="第一卷主线走向与阶段性目标")


# --- Story Contract（最终契约，对齐 15plan 6.2 + Day 5 first_volume_direction） ---


class Positioning(BaseModel):
    model_config = _STORY_RESPONSE_CONFIG

    title_candidates: list[str] = Field(default_factory=list)
    genre: str = ""
    target_reader: str = ""
    core_hook: str = ""
    tone: str = ""


class StoryContract(BaseModel):
    """对齐 15plan 6.2 JSON；volume_outline Day 5 可为空列表。"""

    model_config = _STORY_RESPONSE_CONFIG

    positioning: Positioning
    protagonist: ProtagonistProfile
    characters: list[SupportingCharacter] = Field(default_factory=list)
    world_rules: list[str] = Field(default_factory=list)
    ability_rules: list[str] = Field(default_factory=list)
    forbidden_moves: list[str] = Field(default_factory=list)
    style_guide: StyleGuide = Field(default_factory=StyleGuide)
    first_volume_direction: str = ""
    volume_outline: list[str] = Field(default_factory=list)
    must_retain_facts: list[str] = Field(
        default_factory=list,
        alias="mustRetainFacts",
        description="作者或编辑标注的不可丢事实（并入章节生成的 story_canon）",
    )


class InitNovelResponse(BaseModel):
    model_config = _STORY_RESPONSE_CONFIG

    novel_seed: NovelSeed
    story_contract: StoryContract
    first_volume_outline: str = Field(description="Day 6：第一卷大纲文字（Outline Architect）")
    chapter_contracts: list[ChapterContract] = Field(
        description="Day 6：前 20 章 Chapter Contract（经 Initial Critic）",
    )

    @model_validator(mode="after")
    def day6_chapter_count(self) -> InitNovelResponse:
        if len(self.chapter_contracts) != 20:
            raise ValueError("chapter_contracts must contain exactly 20 items")
        return self
