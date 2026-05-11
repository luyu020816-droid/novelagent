"""Neo4j 世界观图谱只读 API（前端表格）。"""

from __future__ import annotations

from fastapi import APIRouter

from app.services.neo4j_lore_store import export_snapshot

router = APIRouter(tags=["writer"])


@router.get("/api/writer/lore/{project_id}/snapshot")
def lore_snapshot(project_id: str) -> dict:
    return export_snapshot(project_id=project_id)
