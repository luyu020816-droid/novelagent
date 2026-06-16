"""创作向导：叙事结构提案与修订。"""

from __future__ import annotations

from fastapi import APIRouter, HTTPException

from app.agents import narrative_planner
from app.config import get_settings
from app.schemas.setup_narrative import NarrativeProposeRequest, NarrativeProposeResponse
from app.services.llm_gateway import LLMGateway

router = APIRouter(tags=["writer"])


@router.post("/api/writer/setup/narrative-propose", response_model=NarrativeProposeResponse)
def narrative_propose(body: NarrativeProposeRequest) -> NarrativeProposeResponse:
    settings = get_settings()
    if not settings.openai_api_key:
        raise HTTPException(status_code=503, detail="OPENAI_API_KEY is not set.")
    gateway = LLMGateway(settings)
    try:
        return narrative_planner.run_propose(body, gateway)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e)) from e
    except RuntimeError as e:
        raise HTTPException(status_code=502, detail=str(e)) from e


@router.post("/api/writer/setup/narrative-revise", response_model=NarrativeProposeResponse)
def narrative_revise(body: NarrativeProposeRequest) -> NarrativeProposeResponse:
    if not (body.user_feedback or "").strip():
        raise HTTPException(status_code=400, detail="userFeedback 必填")
    settings = get_settings()
    if not settings.openai_api_key:
        raise HTTPException(status_code=503, detail="OPENAI_API_KEY is not set.")
    gateway = LLMGateway(settings)
    try:
        return narrative_planner.run_propose(body, gateway)
    except ValueError as e:
        raise HTTPException(status_code=422, detail=str(e)) from e
    except RuntimeError as e:
        raise HTTPException(status_code=502, detail=str(e)) from e
