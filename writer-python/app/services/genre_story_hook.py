"""Inject optional author story hook into genre pipeline prompts."""

from __future__ import annotations

from app.schemas.genre import GenreRecommendRequest


def format_story_hook_block(req: GenreRecommendRequest) -> str:
    h = (req.story_hook or "").strip()
    if not h:
        return ""
    return (
        "\n\n=== 作者提供的故事线 / 一句话创意（题材方案必须可与该创意对齐，不得无视）===\n"
        f"{h}\n"
    )
