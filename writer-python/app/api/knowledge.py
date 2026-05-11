"""Day 10：知识库向量同步（Qdrant）。"""

from __future__ import annotations

import logging

from fastapi import APIRouter, HTTPException

from app.config import get_settings
from app.schemas.knowledge_sync import KnowledgeSyncRequest
from app.services.vector_service import upsert_chapter_chunks

logger = logging.getLogger(__name__)

router = APIRouter(tags=["writer"])


@router.post("/api/writer/knowledge/sync")
def knowledge_sync(body: KnowledgeSyncRequest) -> dict:
    settings = get_settings()
    if not settings.embedding_auth_configured():
        raise HTTPException(
            status_code=503,
            detail="Embeddings need EMBEDDING_API_KEY (recommended) or OPENAI_API_KEY in writer-python/.env.",
        )
    try:
        n = upsert_chapter_chunks(body.project_id, body.chapter_no, body.chapter_text)
    except Exception as e:
        logger.exception("knowledge sync failed")
        raise HTTPException(status_code=502, detail=str(e)) from e
    return {"ok": True, "chunksUpserted": n}
