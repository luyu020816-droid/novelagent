from __future__ import annotations

import json

from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import ValidationError

from app.agents import genre_scout, genre_unique_lock, market_fit_scorer, trope_strategist
from app.agents import storyline_interviewer as storyline_interviewer_agent
from app.config import get_settings
from app.schemas.genre import GenreDecisionContract, GenreRecommendRequest
from app.schemas.storyline_interview import ChatTurn, GenreInterviewRequest, InterviewerResponse
from app.services.json_repair import validate_or_repair
from app.services.llm_gateway import LLMGateway
from app.services.sse_queue_runner import sse_threaded_generator

router = APIRouter(tags=["writer"])

# 须与 frontend/src/pages/ProjectDetailPage.tsx SKILL_INTERVIEW_OPENER 一致
_SKILL_INTERVIEW_AUTO_OPENER = (
    "我已在本项目中选择了丛书 Skill。"
    "请先结合 Skill 核对规则与禁忌，用 1～2 个问题请作者说明希望落笔的题材或子类型（若 Skill 已限定则只做复述确认），再问一个关键场景或开篇基调假设。"
)


@router.post("/api/writer/genre/interview", response_model=InterviewerResponse)
def genre_storyline_interview(body: GenreInterviewRequest) -> InterviewerResponse:
    """路径 B：多轮互动采访（非流式）。路径 A 与其它 genre 接口不受影响。"""
    settings = get_settings()
    if not settings.openai_api_key:
        raise HTTPException(
            status_code=503,
            detail="OPENAI_API_KEY is not set (environment or .env).",
        )
    gateway = LLMGateway(settings)
    effective = body
    skill_id = (body.writer_skill_id or "").strip()
    if skill_id and not body.chat_history:
        effective = body.model_copy(
            update={
                "chat_history": [ChatTurn(role="user", content=_SKILL_INTERVIEW_AUTO_OPENER)]
            }
        )
    try:
        return storyline_interviewer_agent.run(effective, gateway)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e)) from e
    except RuntimeError as e:
        raise HTTPException(status_code=502, detail=str(e)) from e


def _repair_schema_hint() -> str:
    return json.dumps(GenreDecisionContract.model_json_schema(), ensure_ascii=False)[:12000]


@router.post("/api/writer/genre/recommend", response_model=GenreDecisionContract)
def genre_recommend(body: GenreRecommendRequest) -> GenreDecisionContract:
    settings = get_settings()
    if not settings.openai_api_key:
        raise HTTPException(
            status_code=503,
            detail="OPENAI_API_KEY is not set (environment or .env).",
        )

    gateway = LLMGateway(settings)
    try:
        if body.unique_direction:
            raw_text = genre_unique_lock.run(body, gateway)
            contract, _ = validate_or_repair(
                raw_text,
                GenreDecisionContract,
                gateway,
                agent_name="genre_decision",
                repair_context=_repair_schema_hint(),
                project_id=body.project_id,
            )
            return contract
        scout_out = genre_scout.run(body, gateway)
        strat_out = trope_strategist.run(body, scout_out, gateway)
        raw_text = market_fit_scorer.run(body, scout_out, strat_out, gateway)
        contract, _ = validate_or_repair(
            raw_text,
            GenreDecisionContract,
            gateway,
            agent_name="genre_decision",
            repair_context=_repair_schema_hint(),
            project_id=body.project_id,
        )
    except ValidationError as e:
        raise HTTPException(status_code=502, detail=f"Genre pipeline validation failed: {e}") from e
    except RuntimeError as e:
        raise HTTPException(status_code=502, detail=str(e)) from e
    except ValueError as e:
        raise HTTPException(status_code=502, detail=str(e)) from e
    except FileNotFoundError as e:
        raise HTTPException(
            status_code=500,
            detail=f"Genre static data file not found (check writer-python/data/): {e}",
        ) from e

    return contract


@router.post("/api/writer/genre/recommend/stream")
def genre_recommend_stream(body: GenreRecommendRequest) -> StreamingResponse:
    settings = get_settings()
    if not settings.openai_api_key:
        raise HTTPException(
            status_code=503,
            detail="OPENAI_API_KEY is not set (environment or .env).",
        )

    def worker(emit) -> None:
        gateway = LLMGateway(settings)
        pid = body.project_id
        emit(
            "pipeline_start",
            {
                "pipeline": "genre_recommend_unique" if body.unique_direction else "genre_recommend",
                "projectId": pid,
            },
        )
        try:
            if body.unique_direction:
                emit("node_start", {"node": "genre_unique_lock"})
                raw_text = genre_unique_lock.run(
                    body,
                    gateway,
                    on_llm_delta=lambda d: emit("llm_delta", {"node": "genre_unique_lock", "text": d}),
                )
                emit("node_end", {"node": "genre_unique_lock", "ok": True})
            else:
                emit("node_start", {"node": "genre_scout"})
                scout_out = genre_scout.run(
                    body,
                    gateway,
                    on_llm_delta=lambda d: emit("llm_delta", {"node": "genre_scout", "text": d}),
                )
                emit("node_end", {"node": "genre_scout", "ok": True})

                emit("node_start", {"node": "trope_strategist"})
                strat_out = trope_strategist.run(
                    body,
                    scout_out,
                    gateway,
                    on_llm_delta=lambda d: emit("llm_delta", {"node": "trope_strategist", "text": d}),
                )
                emit("node_end", {"node": "trope_strategist", "ok": True})

                emit("node_start", {"node": "market_fit_scorer"})
                raw_text = market_fit_scorer.run(
                    body,
                    scout_out,
                    strat_out,
                    gateway,
                    on_llm_delta=lambda d: emit("llm_delta", {"node": "market_fit_scorer", "text": d}),
                )
                emit("node_end", {"node": "market_fit_scorer", "ok": True})

            emit("node_start", {"node": "genre_decision"})
            contract, repaired = validate_or_repair(
                raw_text,
                GenreDecisionContract,
                gateway,
                agent_name="genre_decision",
                repair_context=_repair_schema_hint(),
                project_id=body.project_id,
                on_llm_delta=lambda d: emit("llm_delta", {"node": "genre_decision_json_repair", "text": d}),
            )
            emit("node_end", {"node": "genre_decision", "ok": True, "repaired": repaired})

            emit(
                "artifact",
                {"kind": "GenreDecisionContract", "data": contract.model_dump(by_alias=True)},
            )
            emit("done", {"ok": True})
        except ValidationError as e:
            emit("error", {"message": f"Genre pipeline validation failed: {e}"})
            emit("done", {"ok": False})
        except Exception as e:
            emit("error", {"message": str(e)})
            emit("done", {"ok": False})

    return StreamingResponse(
        sse_threaded_generator(worker),
        media_type="text/event-stream",
        headers={
            "Cache-Control": "no-cache",
            "Connection": "keep-alive",
            "X-Accel-Buffering": "no",
        },
    )
