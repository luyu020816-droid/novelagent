"""POST /api/writer/chapters/generate 请求体（与 Java 侧 Jackson camelCase 对齐）。"""

from __future__ import annotations

from typing import Any

from typing import Literal

from pydantic import BaseModel, Field, model_validator


class ChapterGenerateRequest(BaseModel):
    model_config = {"populate_by_name": True}

    project_id: str = Field(alias="projectId")
    chapter_no: int = Field(alias="chapterNo", ge=1)
    story_contract: dict[str, Any] = Field(alias="storyContract")
    chapter_contract: dict[str, Any] = Field(alias="chapterContract")

    history_summaries: list[dict[str, Any]] = Field(default_factory=list, alias="historySummaries")
    recent_summaries: list[dict[str, Any]] = Field(default_factory=list, alias="recentSummaries")
    user_rewrite_notes: str = Field(default="", alias="userRewriteNotes")
    #: plot=默认剧情向修改；anti_ai=弱化 AI 腔与套话，剧情不变
    rewrite_mode: Literal["plot", "anti_ai"] = Field(default="plot", alias="rewriteMode")
    retry_count: int = Field(default=0, alias="retryCount", ge=0, le=10)
    fan_series_preset: str | None = Field(default=None, alias="fanSeriesPreset")

    @model_validator(mode="before")
    @classmethod
    def hoist_chapter_contract_if_nested(cls, data: Any) -> Any:
        """Java 在 chapterContract 为 null 时可能不写顶层键；兼容嵌套在 storyContract 下的章纲。"""
        if not isinstance(data, dict):
            return data
        out = dict(data)
        if out.get("chapterContract") is None and out.get("chapter_contract") is None:
            story = out.get("storyContract") or out.get("story_contract")
            if isinstance(story, dict):
                nested = story.get("chapterContract") or story.get("chapter_contract")
                if nested is not None:
                    out["chapterContract"] = nested
        return out
