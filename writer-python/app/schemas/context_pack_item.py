"""Day 11：可裁剪上下文条目（priority 12 为「本章已确认摘要」专用最高档；一般条目 ≤11，裁剪时先删低 priority）。"""

from __future__ import annotations

from pydantic import BaseModel, Field


class ContextPackItem(BaseModel):
    content: str = Field(description="写入模型上下文的文本或 JSON 字符串")
    priority: int = Field(
        ge=1,
        le=12,
        description="12=本章已确认动笔摘要（最高）；11=人类指令等；低优先级先被 Budget 剔除",
    )
    category: str = Field(description="语义类别，用于强制保留与审计")
    estimated_tokens: int = Field(ge=0, description="tiktoken cl100k_base 估算")
