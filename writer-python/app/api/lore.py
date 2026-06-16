"""Neo4j 世界观图谱只读 API（前端表格）。"""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter

from pydantic import BaseModel, ConfigDict, Field

from app.services.neo4j_lore_store import (
    export_snapshot,
    upsert_narrative_chapter_context,
    upsert_narrative_structure_sync,
)

router = APIRouter(tags=["writer"])


@router.get("/api/writer/lore/{project_id}/snapshot")
def lore_snapshot(project_id: str) -> dict:
    return export_snapshot(project_id=project_id)


class NarrativeChapterContextBody(BaseModel):
    model_config = ConfigDict(extra="ignore", populate_by_name=True)

    project_id: str = Field(alias="projectId")
    chapter_no: int = Field(alias="chapterNo", ge=1)
    chapter_obligations: dict[str, Any] = Field(alias="chapterObligations")


@router.post("/api/writer/lore/narrative-chapter-context")
def narrative_chapter_context(body: NarrativeChapterContextBody) -> dict:
    upsert_narrative_chapter_context(
        project_id=body.project_id,
        chapter_no=body.chapter_no,
        obligations=body.chapter_obligations,
    )
    return {"ok": True}


class NarrativeStructureSyncBody(BaseModel):
    model_config = ConfigDict(extra="ignore", populate_by_name=True)

    project_id: str = Field(alias="projectId")
    storylines: list[dict[str, Any]] = Field(default_factory=list)
    confluences: list[dict[str, Any]] = Field(default_factory=list)


@router.post("/api/writer/lore/narrative-structure-sync")
def narrative_structure_sync(body: NarrativeStructureSyncBody) -> dict:
    """定稿后由 Java 推送 PG 全量故事线/汇合点，写入 Neo4j LoreStoryline / LoreConfluence 节点。"""
    upsert_narrative_structure_sync(
        project_id=body.project_id,
        storylines=body.storylines,
        confluences=body.confluences,
    )
    return {"ok": True}
