"""兼容旧 import：Skill 内容由 app/skills/library/*.yaml 提供（见 loader）。"""

from __future__ import annotations

from app.skills.loader import (
    SeriesPreset,
    get_series_preset,
    list_known_preset_ids,
    list_skill_summaries,
    load_all_skills,
)

__all__ = [
    "SeriesPreset",
    "get_series_preset",
    "list_known_preset_ids",
    "list_skill_summaries",
    "load_all_skills",
]
