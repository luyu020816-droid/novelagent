from __future__ import annotations

import json

from fastapi import APIRouter, HTTPException
from pydantic import ValidationError

from app.agents import genre_scout, market_fit_scorer, trope_strategist
from app.config import get_settings
from app.schemas.genre import GenreDecisionContract, GenreRecommendRequest
from app.services.json_repair import validate_or_repair
from app.services.llm_gateway import LLMGateway

router = APIRouter(tags=["writer"])


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
