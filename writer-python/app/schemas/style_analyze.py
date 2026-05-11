"""POST /api/writer/style/analyze — 参考文本风格指纹（轻量）。"""

from __future__ import annotations

from pydantic import BaseModel, ConfigDict, Field


class StyleAnalyzeRequest(BaseModel):
    model_config = {"populate_by_name": True}

    sample_text: str = Field(alias="sampleText", min_length=20, max_length=120000)


class StyleAnalyzeResponse(BaseModel):
    model_config = ConfigDict(populate_by_name=True, serialize_by_alias=True)

    avg_sentence_len: float = Field(alias="avgSentenceLen")
    sample_chars: int = Field(alias="sampleChars")
    style_guide_md: str = Field(alias="styleGuideMd")
