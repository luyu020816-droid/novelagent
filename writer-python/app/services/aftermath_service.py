"""定稿后统一 aftermath：滚动摘要 + Neo4j Lore + 伏笔回收判定。"""

from __future__ import annotations

import logging
from typing import Any

from app.services.aftermath_enrich import enrich_aftermath_metadata
from app.services.chapter_summarize import summarize_chapter_text
from app.services.lore_keeper_service import ingest_chapter_lore, run_foreshadow_resolve_pass
from app.services.llm_gateway import LLMGateway

logger = logging.getLogger(__name__)


def run_chapter_aftermath(
    gateway: LLMGateway,
    *,
    project_id: str,
    chapter_no: int,
    chapter_text: str,
) -> dict[str, Any]:
    """Java accept 定稿后同步调用；向量 upsert 仍由 Java 异步 knowledge/sync。"""
    summary = summarize_chapter_text(
        gateway,
        project_id=project_id,
        chapter_no=chapter_no,
        chapter_text=chapter_text,
    )

    lore_ingested = False
    lore_error: str | None = None
    try:
        ingest_chapter_lore(
            gateway,
            project_id=project_id,
            chapter_no=chapter_no,
            chapter_text=chapter_text,
        )
        lore_ingested = True
    except Exception as e:
        lore_error = str(e)
        logger.warning(
            "[Aftermath] lore ingest failed project=%s chapter=%s: %s",
            project_id,
            chapter_no,
            e,
        )

    enrich: dict[str, Any] = {}
    try:
        enrich = enrich_aftermath_metadata(
            gateway,
            project_id=project_id,
            chapter_no=chapter_no,
            chapter_text=chapter_text,
        )
        if isinstance(summary, dict) and enrich:
            summary = {**summary, "_aftermathEnrich": enrich}
            hint = enrich.get("style_similarity_hint")
            if hint is not None:
                summary["style_similarity_hint"] = hint
    except Exception as e:
        logger.warning("[Aftermath] enrich failed: %s", e)

    pending = summary.get("pending_foreshadowing") if isinstance(summary, dict) else None
    resolve_meta = run_foreshadow_resolve_pass(
        gateway,
        project_id=project_id,
        chapter_no=chapter_no,
        chapter_text=chapter_text,
        pending_still_open=pending if isinstance(pending, list) else None,
    )

    return {
        "summary": summary,
        "loreIngested": lore_ingested,
        "loreError": lore_error,
        "foreshadowResolve": resolve_meta,
        "aftermathEnrich": enrich,
    }
