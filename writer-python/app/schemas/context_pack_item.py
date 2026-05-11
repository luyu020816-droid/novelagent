"""Day 11：可裁剪上下文条目（priority 11 最高，裁剪时先删 priority 1）。"""

from __future__ import annotations

from pydantic import BaseModel, Field


class ContextPackItem(BaseModel):
    content: str = Field(description="写入模型上下文的文本或 JSON 字符串")
    priority: int = Field(ge=1, le=11, description="11 最高；低优先级先被 Budget 剔除")
    category: str = Field(description="语义类别，用于强制保留与审计")
    estimated_tokens: int = Field(ge=0, description="tiktoken cl100k_base 估算")
