"""POST /api/writer/knowledge/sync — Java accept 后异步同步正文向量。"""

from __future__ import annotations

from pydantic import BaseModel, Field


class KnowledgeSyncRequest(BaseModel):
    model_config = {"populate_by_name": True}

    project_id: str = Field(alias="projectId")
    chapter_no: int = Field(alias="chapterNo", ge=1)
    chapter_text: str = Field(alias="chapterText", min_length=1)
