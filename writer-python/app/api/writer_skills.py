"""列举 app/skills/library 下已加载的 YAML Skill。"""

from __future__ import annotations

from fastapi import APIRouter

from app.skills.loader import SKILL_LIBRARY_DIR, list_skill_summaries

router = APIRouter(tags=["writer"])


@router.get("/api/writer/skills")
def list_writer_skills() -> dict:
    """返回 skills: [{id, label}]；目录为空则为 []。"""
    return {
        "libraryDir": str(SKILL_LIBRARY_DIR),
        "skills": list_skill_summaries(),
    }
