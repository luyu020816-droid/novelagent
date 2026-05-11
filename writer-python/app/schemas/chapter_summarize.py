"""POST /api/writer/chapters/summarize 请求体。"""

from __future__ import annotations

from pydantic import BaseModel, Field


class ChapterSummarizeRequest(BaseModel):
    model_config = {"populate_by_name": True}

    project_id: str = Field(alias="projectId")
    chapter_no: int = Field(alias="chapterNo", ge=1)
    chapter_text: str = Field(alias="chapterText", min_length=1)
