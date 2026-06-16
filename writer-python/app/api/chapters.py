"""章节生成 SSE API（LangGraph）。"""

from __future__ import annotations

import logging
from typing import Annotated, Any

from fastapi import APIRouter, Header, HTTPException
from fastapi.responses import StreamingResponse

from pydantic import BaseModel, ConfigDict, Field

from app.config import get_settings
from app.graph.graph_runner import run_chapter_graph, run_chapter_graph_async
from app.schemas.chapter_generate import ChapterGenerateRequest
from app.schemas.chapter_summarize import ChapterSummarizeRequest
from app.services.java_job_callbacks import notify_node_end
from app.services.chapter_fanqie_assist import (
    run_fanqie_editor_review,
    run_polish_with_notes,
    run_propose_chapter_plan_summary,
)
from app.services.chapter_narrative_metrics import compute_narrative_metrics
from app.services.aftermath_service import run_chapter_aftermath
from app.services.narrative_metrics_heuristic import heuristic_metrics_no_llm
from app.services.narrative_fulfillment import heuristic_fulfillment, llm_fulfillment
from app.services.llm_gateway import LLMGateway
from app.services.sse_queue_runner import sse_threaded_generator

router = APIRouter(tags=["writer"])
_log = logging.getLogger(__name__)


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


@router.post("/api/writer/chapters/generate-complete")
async def chapters_generate_complete(
    body: ChapterGenerateRequest,
    x_generation_job_id: Annotated[str | None, Header(alias="X-Generation-Job-Id")] = None,
) -> dict:
    """同步整章生成：供 Java 后台线程 HTTP 调用。使用 async/await，避免在 ASGI 栈内 asyncio.run 与事件循环冲突。"""
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

    def emit(event: str, payload: dict) -> None:
        if event == "node_end" and isinstance(payload.get("node"), str):
            notify_node_end(x_generation_job_id, payload.get("node"))

    try:
        return await run_chapter_graph_async(initial, emit)
    except HTTPException:
        raise
    except Exception as e:
        logging.getLogger(__name__).exception("generate-complete failed")
        raise HTTPException(status_code=500, detail=str(e)) from e


@router.post("/api/writer/chapters/aftermath")
def chapters_aftermath(body: ChapterSummarizeRequest) -> dict:
    """定稿后统一 aftermath：摘要 + Lore 入库 + 伏笔回收判定（Java accept 主路径）。"""
    settings = get_settings()
    if not settings.openai_api_key:
        raise HTTPException(
            status_code=503,
            detail="OPENAI_API_KEY is not set (environment or .env).",
        )
    gw = LLMGateway(settings)
    result = run_chapter_aftermath(
        gw,
        project_id=body.project_id,
        chapter_no=body.chapter_no,
        chapter_text=body.chapter_text,
    )
    resolve_meta = result.get("foreshadowResolve") or {}
    if int(resolve_meta.get("resolved_count") or 0) > 0:
        _log.info(
            "foreshadow_resolve project=%s chapter=%s resolved=%s keys=%s",
            body.project_id,
            body.chapter_no,
            resolve_meta.get("resolved_count"),
            resolve_meta.get("resolved_fs_keys"),
        )
    return result


@router.post("/api/writer/chapters/summarize")
def chapters_summarize(body: ChapterSummarizeRequest) -> dict:
    """兼容旧调用：等价于 aftermath，仅返回 summary 字段。"""
    return {"summary": chapters_aftermath(body)["summary"]}


class NarrativeMetricsRequest(BaseModel):
    model_config = ConfigDict(extra="ignore", populate_by_name=True)

    project_id: str = Field(alias="projectId")
    chapter_no: int = Field(alias="chapterNo", ge=1)
    chapter_text: str = Field(alias="chapterText", min_length=1)


@router.post("/api/writer/chapters/narrative-metrics")
def chapters_narrative_metrics(body: NarrativeMetricsRequest) -> dict:
    """Java 定稿后异步估算张力/文风；无 API Key 时返回启发式占位，避免阻断流水线。"""
    settings = get_settings()
    if not settings.openai_api_key:
        h = heuristic_metrics_no_llm(body.chapter_text)
        return {
            "tensionScore": h["tensionScore"],
            "styleSimilarity": h["styleSimilarity"],
            "raw": h["raw"],
        }
    gw = LLMGateway()
    out = compute_narrative_metrics(
        gw,
        project_id=body.project_id,
        chapter_no=body.chapter_no,
        chapter_text=body.chapter_text,
    )
    return {
        "tensionScore": out["tensionScore"],
        "styleSimilarity": out["styleSimilarity"],
        "raw": out.get("raw"),
    }


class NarrativeFulfillmentRequest(BaseModel):
    model_config = ConfigDict(extra="ignore", populate_by_name=True)

    project_id: str = Field(alias="projectId")
    chapter_no: int = Field(alias="chapterNo", ge=1)
    chapter_text: str = Field(alias="chapterText", min_length=1)
    chapter_obligations: dict[str, Any] = Field(alias="chapterObligations")


@router.post("/api/writer/chapters/narrative-fulfillment")
def chapters_narrative_fulfillment(body: NarrativeFulfillmentRequest) -> dict:
    """对照 chapterObligations 评估 chapterText；无 API Key 时仅返回启发式结果。"""
    settings = get_settings()
    base = heuristic_fulfillment(body.chapter_obligations, body.chapter_text)
    if not settings.openai_api_key:
        return base
    try:
        gw = LLMGateway()
        return llm_fulfillment(
            gw,
            project_id=body.project_id,
            chapter_no=body.chapter_no,
            obligations=body.chapter_obligations,
            chapter_text=body.chapter_text,
        )
    except Exception:
        return base


class FanqieEditorReviewRequest(BaseModel):
    model_config = ConfigDict(extra="ignore", populate_by_name=True)

    project_id: str = Field(alias="projectId")
    chapter_no: int = Field(alias="chapterNo", ge=1)
    story_contract: dict = Field(alias="storyContract")
    chapter_contract: dict = Field(alias="chapterContract")
    chapter_text: str = Field(alias="chapterText", min_length=1)


class PolishCombinedRequest(BaseModel):
    model_config = ConfigDict(populate_by_name=True)

    project_id: str = Field(alias="projectId")
    chapter_no: int = Field(alias="chapterNo", ge=1)
    chapter_text: str = Field(alias="chapterText", min_length=1)
    tomato_review: str = Field(default="", alias="tomatoReview")
    author_notes: str = Field(default="", alias="authorNotes")


class ProposePlanSummaryRequest(BaseModel):
    model_config = ConfigDict(extra="ignore", populate_by_name=True)

    project_id: str = Field(alias="projectId")
    chapter_no: int = Field(alias="chapterNo", ge=1)
    story_contract: dict = Field(alias="storyContract")
    chapter_contract: dict = Field(alias="chapterContract")
    prev_chapter_commit_summary: dict | list | None = Field(default=None, alias="prevChapterCommitSummary")


@router.post("/api/writer/chapters/fanqie-editor-review")
def fanqie_editor_review(body: FanqieEditorReviewRequest) -> dict:
    settings = get_settings()
    if not settings.openai_api_key:
        raise HTTPException(status_code=503, detail="OPENAI_API_KEY is not set (environment or .env).")
    gw = LLMGateway()
    text = run_fanqie_editor_review(
        gateway=gw,
        project_id=body.project_id,
        chapter_no=body.chapter_no,
        story_contract=body.story_contract,
        chapter_contract=body.chapter_contract,
        chapter_text=body.chapter_text,
    )
    return {"review": text}


@router.post("/api/writer/chapters/polish-with-notes")
def polish_with_notes(body: PolishCombinedRequest) -> dict:
    settings = get_settings()
    if not settings.openai_api_key:
        raise HTTPException(status_code=503, detail="OPENAI_API_KEY is not set (environment or .env).")
    gw = LLMGateway()
    out = run_polish_with_notes(
        gateway=gw,
        project_id=body.project_id,
        chapter_no=body.chapter_no,
        chapter_text=body.chapter_text,
        tomato_review=body.tomato_review,
        author_notes=body.author_notes,
    )
    return {"polishedText": out}


@router.post("/api/writer/chapters/propose-plan-summary")
def propose_plan_summary(body: ProposePlanSummaryRequest) -> dict:
    settings = get_settings()
    if not settings.openai_api_key:
        raise HTTPException(status_code=503, detail="OPENAI_API_KEY is not set (environment or .env).")
    gw = LLMGateway()
    prev = body.prev_chapter_commit_summary
    if isinstance(prev, list):
        prev = None
    plan = run_propose_chapter_plan_summary(
        gateway=gw,
        project_id=body.project_id,
        chapter_no=body.chapter_no,
        story_contract=body.story_contract,
        chapter_contract=body.chapter_contract,
        prev_chapter_commit_summary=prev if isinstance(prev, dict) else None,
    )
    return {"planSummary": plan}
