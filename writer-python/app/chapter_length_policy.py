"""单章正文篇幅（字符计）：含汉字、标点、数字、空格等，与常见平台「字数」统计一致。"""

from __future__ import annotations

# 产品约定：每章成文目标区间（用户可调时改此模块即可）
CHAPTER_BODY_MIN_CHARS = 2500
CHAPTER_BODY_MAX_CHARS = 4000


def chapter_char_count(text: str) -> int:
    return len((text or "").strip())
