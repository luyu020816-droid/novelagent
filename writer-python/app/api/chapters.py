"""章节生成 SSE API（LangGraph）。"""

from __future__ import annotations

from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse

from app.config import get_settings
from app.graph.graph_runner import run_chapter_graph
from app.schemas.chapter_generate import ChapterGenerateRequest
from app.schemas.chapter_summarize import ChapterSummarizeRequest
from app.services.chapter_summarize import summarize_chapter_text
from app.services.lore_keeper_service import ingest_chapter_lore
from app.services.llm_gateway import LLMGateway
from app.services.sse_queue_runner import sse_threaded_generator

router = APIRouter(tags=["writer"])


@router.post("/api/writer/chapters/generate")
def chapters_generate_stream(body: ChapterGenerateRequest) -> StreamingResponse:
    """SSE：信封仍为 `event:` + `data:` JSON（node_start / llm_delta / node_end / artifact / done / error）。"""
    settings = get_settings()
    if not settings.openai_api_key:
        raise HTTPException(
            status_code=503,
            detail="OPENAI_API_KEY is not set (environment or .env).",
        )

    initial = body.model_dump(by_alias=False)
    initial.setdefault("retry_count", initial.get("retry_count", 0))
    initial.setdefault("user_rewrite_notes", initial.get("user_rewrite_notes") or "")
    initial.setdefault("rewrite_mode", initial.get("rewrite_mode") or "plot")

    def worker(emit) -> None:
        run_chapter_graph(initial, emit)

    return StreamingResponse(
        sse_threaded_generator(worker),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )


@router.post("/api/writer/chapters/summarize")
def chapters_summarize(body: ChapterSummarizeRequest) -> dict:
    """Java 人工 accept 后同步抽取滚动摘要（写入 chapter_commits.summary）。"""
    settings = get_settings()
    if not settings.openai_api_key:
        raise HTTPException(
            status_code=503,
            detail="OPENAI_API_KEY is not set (environment or .env).",
        )
    gw = LLMGateway()
    summary = summarize_chapter_text(
        gw,
        project_id=body.project_id,
        chapter_no=body.chapter_no,
        chapter_text=body.chapter_text,
    )
    ingest_chapter_lore(
        gw,
        project_id=body.project_id,
        chapter_no=body.chapter_no,
        chapter_text=body.chapter_text,
    )
    return {"summary": summary}
