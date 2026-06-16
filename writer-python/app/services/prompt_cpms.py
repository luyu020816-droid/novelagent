"""CPMS：PG 活跃版本优先，fallback prompts/cpms_manifest.json + 文件。"""

from __future__ import annotations

import json
import logging
import uuid
from contextvars import ContextVar
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path

from app.services.prompt_registry import load_prompt

logger = logging.getLogger(__name__)

_MANIFEST = Path(__file__).resolve().parents[2] / "prompts" / "cpms_manifest.json"

_prompt_version_ctx: ContextVar[str | None] = ContextVar("cpms_prompt_version", default=None)


@dataclass(frozen=True)
class PromptBundle:
    text: str
    version: str
    source: str  # pg | manifest


@lru_cache
def _manifest() -> dict:
    if not _MANIFEST.is_file():
        return {}
    return json.loads(_MANIFEST.read_text(encoding="utf-8"))


def peek_prompt_version() -> str | None:
    return _prompt_version_ctx.get()


def _load_from_pg(node_key: str) -> PromptBundle | None:
    try:
        from app.db import get_connection
    except Exception:
        return None
    try:
        with get_connection() as conn:
            with conn.cursor() as cur:
                cur.execute(
                    """
                    SELECT version_label, content FROM cpms_prompt_versions
                    WHERE node_key = %s AND is_active = TRUE
                    ORDER BY created_at DESC LIMIT 1
                    """,
                    (node_key,),
                )
                row = cur.fetchone()
        if row and row.get("content"):
            return PromptBundle(
                text=str(row["content"]),
                version=str(row.get("version_label") or "pg_active"),
                source="pg",
            )
    except Exception as e:
        logger.debug("CPMS PG miss for %s: %s", node_key, e)
    return None


def _load_from_manifest(node_key: str, *, fallback_file: str | None) -> PromptBundle:
    nodes = _manifest().get("nodes") or {}
    rel = nodes.get(node_key)
    ver = "manifest_v1"
    if isinstance(rel, str) and rel.strip():
        return PromptBundle(text=load_prompt(rel.strip()), version=ver, source="manifest")
    if fallback_file:
        return PromptBundle(text=load_prompt(fallback_file), version=ver, source="manifest")
    raise FileNotFoundError(f"CPMS node not found: {node_key}")


def resolve_node_prompt(node_key: str, *, fallback_file: str | None = None) -> PromptBundle:
    pg = _load_from_pg(node_key)
    if pg is not None:
        return pg
    return _load_from_manifest(node_key, fallback_file=fallback_file)


def load_node_prompt(node_key: str, *, fallback_file: str | None = None) -> str:
    bundle = resolve_node_prompt(node_key, fallback_file=fallback_file)
    _prompt_version_ctx.set(bundle.version)
    return bundle.text


def seed_manifest_to_pg(*, activate: bool = True) -> int:
    """将 manifest 中节点写入 cpms_prompt_versions（开发/CI 一次性）。"""
    from app.db import get_connection

    nodes = _manifest().get("nodes") or {}
    n = 0
    with get_connection() as conn:
        with conn.cursor() as cur:
            for node_key, rel in nodes.items():
                if not isinstance(rel, str) or not rel.strip():
                    continue
                content = load_prompt(rel.strip())
                vid = uuid.uuid4().hex
                label = "v1_file"
                if activate:
                    cur.execute(
                        "UPDATE cpms_prompt_versions SET is_active = FALSE WHERE node_key = %s",
                        (node_key,),
                    )
                cur.execute(
                    """
                    INSERT INTO cpms_prompt_versions (id, node_key, version_label, content, is_active)
                    VALUES (%s, %s, %s, %s, %s)
                    ON CONFLICT (node_key, version_label) DO UPDATE SET
                      content = EXCLUDED.content,
                      is_active = EXCLUDED.is_active
                    """,
                    (vid, node_key, label, content, activate),
                )
                n += 1
        conn.commit()
    return n
