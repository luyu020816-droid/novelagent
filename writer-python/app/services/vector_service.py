"""Day 10：Qdrant 向量存储与 project_id 隔离检索。"""

from __future__ import annotations

import logging
import uuid
from typing import Any

from langchain_text_splitters import RecursiveCharacterTextSplitter
from openai import OpenAI
from qdrant_client import QdrantClient
from qdrant_client.models import Distance, FieldCondition, Filter, MatchValue, PointStruct, VectorParams

from app.config import Settings, get_settings

logger = logging.getLogger(__name__)


def _embedding_openai_client(settings: Settings) -> OpenAI:
    """Embeddings 可与对话分流：专用 Key + 可选 Base URL；对话走 DeepSeek 时请配 EMBEDDING_*。"""
    kwargs: dict[str, Any] = {}
    key = (settings.embedding_api_key or settings.openai_api_key or "").strip()
    if not key:
        raise RuntimeError("缺少向量 Key：请在 .env 设置 EMBEDDING_API_KEY 或 OPENAI_API_KEY")

    kwargs["api_key"] = key
    base = (settings.embedding_openai_base_url or "").strip()
    if base:
        kwargs["base_url"] = base.rstrip("/")
    elif settings.embedding_api_key:
        kwargs["base_url"] = "https://api.openai.com/v1"
    elif settings.openai_base_url:
        kwargs["base_url"] = settings.openai_base_url.strip().rstrip("/")

    return OpenAI(**kwargs)


def _qdrant(settings: Settings) -> QdrantClient:
    kwargs: dict[str, Any] = {"url": settings.qdrant_url}
    if settings.qdrant_api_key:
        kwargs["api_key"] = settings.qdrant_api_key
    return QdrantClient(**kwargs)


def _ensure_collection(client: QdrantClient, settings: Settings) -> None:
    name = settings.qdrant_collection
    if client.collection_exists(collection_name=name):
        return
    client.create_collection(
        collection_name=name,
        vectors_config=VectorParams(size=settings.embedding_dimensions, distance=Distance.COSINE),
    )
    logger.info("Qdrant: created collection %s dim=%s", name, settings.embedding_dimensions)


def _embed_batch(settings: Settings, texts: list[str]) -> list[list[float]]:
    if not texts:
        return []
    eo = _embedding_openai_client(settings)
    model = settings.embedding_model
    emb_kwargs: dict[str, Any] = {"model": model, "input": texts}
    # 千问 text-embedding-v4 / OpenAI text-embedding-3-* 等均支持 dimensions，须与 Qdrant 集合维度一致
    if settings.embedding_dimensions > 0:
        emb_kwargs["dimensions"] = settings.embedding_dimensions
    resp = eo.embeddings.create(**emb_kwargs)
    return [item.embedding for item in resp.data]


def delete_chapter_vectors(project_id: str, chapter_no: int, settings: Settings | None = None) -> None:
    settings = settings or get_settings()
    if not settings.vector_sync_enabled:
        return
    client = _qdrant(settings)
    if not client.collection_exists(collection_name=settings.qdrant_collection):
        return
    client.delete(
        collection_name=settings.qdrant_collection,
        points_selector=Filter(
            must=[
                FieldCondition(key="project_id", match=MatchValue(value=project_id)),
                FieldCondition(key="chapter_no", match=MatchValue(value=chapter_no)),
            ]
        ),
    )


def upsert_chapter_chunks(project_id: str, chapter_no: int, text: str, settings: Settings | None = None) -> int:
    """切分章节正文并写入 Qdrant；同一 project_id+chapter_no 会先删再插。"""
    settings = settings or get_settings()
    if not settings.vector_sync_enabled:
        logger.info("[VectorSync] skipped (vector_sync_enabled=false)")
        return 0
    raw = (text or "").strip()
    if not raw:
        return 0
    if not settings.embedding_auth_configured():
        logger.warning("[VectorSync] skipped: set EMBEDDING_API_KEY or OPENAI_API_KEY for embeddings")
        return 0

    splitter = RecursiveCharacterTextSplitter(chunk_size=600, chunk_overlap=120)
    chunks = splitter.split_text(raw)
    if not chunks:
        return 0

    client = _qdrant(settings)
    _ensure_collection(client, settings)
    delete_chapter_vectors(project_id, chapter_no, settings)

    vectors = _embed_batch(settings, chunks)
    coll = settings.qdrant_collection
    points: list[PointStruct] = []
    for i, (chunk, vec) in enumerate(zip(chunks, vectors, strict=True)):
        pid = str(uuid.uuid5(uuid.NAMESPACE_URL, f"mythosforge:{project_id}:{chapter_no}:{i}"))
        points.append(
            PointStruct(
                id=pid,
                vector=vec,
                payload={
                    "project_id": project_id,
                    "chapter_no": chapter_no,
                    "chunk_idx": i,
                    "text": chunk,
                },
            )
        )
    client.upsert(collection_name=coll, points=points)
    logger.info("[VectorSync] upserted project=%s chapter=%s chunks=%s", project_id, chapter_no, len(points))
    return len(points)


def query_knowledge(project_id: str, query_text: str, limit: int = 3, settings: Settings | None = None) -> list[dict[str, Any]]:
    """向量检索；强制 payload 过滤 project_id。"""
    settings = settings or get_settings()
    out: list[dict[str, Any]] = []
    if not settings.vector_sync_enabled:
        return out
    q = (query_text or "").strip()
    if not q or not settings.embedding_auth_configured():
        return out
    client = _qdrant(settings)
    coll = settings.qdrant_collection
    if not client.collection_exists(collection_name=coll):
        return out

    try:
        vec = _embed_batch(settings, [q])[0]
    except Exception as e:
        logger.warning("[VectorSearch] embed query failed: %s", e)
        return out

    flt = Filter(must=[FieldCondition(key="project_id", match=MatchValue(value=project_id))])
    resp = client.query_points(collection_name=coll, query=vec, query_filter=flt, limit=limit)
    for h in resp.points or []:
        payload = h.payload or {}
        out.append(
            {
                "text": str(payload.get("text") or ""),
                "chapter_no": payload.get("chapter_no"),
                "chunk_idx": payload.get("chunk_idx"),
                "score": float(h.score) if h.score is not None else None,
            }
        )
    return out
