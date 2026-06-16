from __future__ import annotations

from typing import Any

from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import ValidationError

from app.agents import (
    character_designer,
    initial_critic,
    outline_architect,
    outline_chapter_draft,
    showrunner,
    worldbuilder,
)
from app.schemas.chapter import OutlineArchitectOutput
from app.config import get_settings
from app.schemas.chapter import ChapterContract
from app.schemas.story import (
    CharacterDesignerOutput,
    InitNovelRequest,
    InitNovelResponse,
    NovelSeed,
    Positioning,
    StoryContract,
    WorldbuilderOutput,
)
from app.skills.preset_merge import merge_story_contract_with_preset
from app.services.llm_gateway import LLMGateway
from app.services.sse_queue_runner import sse_threaded_generator

router = APIRouter(tags=["writer"])


def _genre_with_wizard(genre_decision: dict[str, Any], wizard_notes: str | None) -> dict[str, Any]:
    gd = dict(genre_decision)
    w = (wizard_notes or "").strip()
    if w:
        gd["authorWizardBrief"] = w[:12000]
    return gd


def _genre_name(genre_decision: dict[str, Any]) -> str:
    sel = genre_decision.get("selectedDirection") or genre_decision.get("selected_direction") or {}
    if isinstance(sel, dict):
        return str(sel.get("genre") or "")
    return ""


def _genre_core_hook(genre_decision: dict[str, Any]) -> str:
    return str(
        genre_decision.get("recommendedCoreHook")
        or genre_decision.get("recommended_core_hook")
        or ""
    )


def _merge_story_contract(
    genre_decision: dict[str, Any],
    novel_seed: NovelSeed,
    char_out: CharacterDesignerOutput,
    world_out: WorldbuilderOutput,
) -> StoryContract:
    gname = _genre_name(genre_decision)
    ghook = _genre_core_hook(genre_decision)
    core_hook = novel_seed.core_selling_point.strip() or ghook
    positioning = Positioning(
        title_candidates=list(novel_seed.title_candidates),
        genre=gname,
        target_reader=novel_seed.target_reader,
        core_hook=core_hook,
        tone=novel_seed.tone,
    )
    return StoryContract(
        positioning=positioning,
        protagonist=char_out.protagonist,
        characters=list(char_out.supporting_characters),
        world_rules=list(world_out.world_rules),
        ability_rules=list(world_out.ability_rules),
        forbidden_moves=list(world_out.forbidden_moves),
        style_guide=world_out.style_guide,
        first_volume_direction=world_out.first_volume_direction,
        volume_outline=[],
    )


@router.post("/api/writer/init-novel", response_model=InitNovelResponse)
def init_novel(body: InitNovelRequest) -> InitNovelResponse:
    settings = get_settings()
    if not settings.openai_api_key:
        raise HTTPException(
            status_code=503,
            detail="OPENAI_API_KEY is not set (environment or .env).",
        )

    raw_gd = body.genre_decision
    if not isinstance(raw_gd, dict) or len(raw_gd) == 0:
        raise HTTPException(status_code=400, detail="genre_decision must be a non-empty object.")
    genre_decision = _genre_with_wizard(raw_gd, body.wizard_notes)

    gateway = LLMGateway(settings)
    pid = body.project_id

    try:
        seed = showrunner.run(genre_decision, gateway, pid)
        chars = character_designer.run(genre_decision, seed, gateway, pid)
        world = worldbuilder.run(genre_decision, seed, chars, gateway, pid)
        contract = _merge_story_contract(genre_decision, seed, chars, world)
        if body.fan_series_preset:
            try:
                contract = merge_story_contract_with_preset(contract, body.fan_series_preset)
            except ValueError as e:
                raise HTTPException(status_code=400, detail=str(e)) from e
        outline_out = outline_architect.run(genre_decision, contract, gateway, pid)
        draft_chapters = outline_chapter_draft.run(
            genre_decision,
            contract,
            outline_out.first_volume_outline,
            gateway,
            pid,
        )
        outline_for_critic = OutlineArchitectOutput(
            first_volume_outline=outline_out.first_volume_outline,
            chapters=draft_chapters,
        )
        chapter_list = initial_critic.run(
            genre_decision,
            contract,
            outline_for_critic,
            gateway,
            pid,
        )
    except ValidationError as e:
        raise HTTPException(status_code=502, detail=f"Init novel validation failed: {e}") from e
    except RuntimeError as e:
        raise HTTPException(status_code=502, detail=str(e)) from e
    except ValueError as e:
        raise HTTPException(status_code=502, detail=str(e)) from e
    except FileNotFoundError as e:
        raise HTTPException(status_code=500, detail=str(e)) from e

    return InitNovelResponse(
        novel_seed=seed,
        story_contract=contract,
        first_volume_outline=outline_out.first_volume_outline,
        chapter_contracts=chapter_list,
    )


@router.post("/api/writer/init-novel/stream")
def init_novel_stream(body: InitNovelRequest) -> StreamingResponse:
    settings = get_settings()
    if not settings.openai_api_key:
        raise HTTPException(
            status_code=503,
            detail="OPENAI_API_KEY is not set (environment or .env).",
        )

    raw_gd = body.genre_decision
    if not isinstance(raw_gd, dict) or len(raw_gd) == 0:
        raise HTTPException(status_code=400, detail="genre_decision must be a non-empty object.")
    genre_decision = _genre_with_wizard(raw_gd, body.wizard_notes)

    def worker(emit) -> None:
        gateway = LLMGateway(settings)
        pid = body.project_id
        emit("pipeline_start", {"pipeline": "init_novel", "projectId": pid})
        try:
            emit("node_start", {"node": "showrunner"})
            seed = showrunner.run(
                genre_decision,
                gateway,
                pid,
                on_llm_delta=lambda d: emit("llm_delta", {"node": "showrunner", "text": d}),
            )
            emit("node_end", {"node": "showrunner", "ok": True})

            emit("node_start", {"node": "character_designer"})
            chars = character_designer.run(
                genre_decision,
                seed,
                gateway,
                pid,
                on_llm_delta=lambda d: emit("llm_delta", {"node": "character_designer", "text": d}),
            )
            emit("node_end", {"node": "character_designer", "ok": True})

            emit("node_start", {"node": "worldbuilder"})
            world = worldbuilder.run(
                genre_decision,
                seed,
                chars,
                gateway,
                pid,
                on_llm_delta=lambda d: emit("llm_delta", {"node": "worldbuilder", "text": d}),
            )
            emit("node_end", {"node": "worldbuilder", "ok": True})

            contract = _merge_story_contract(genre_decision, seed, chars, world)
            if body.fan_series_preset:
                try:
                    contract = merge_story_contract_with_preset(contract, body.fan_series_preset)
                except ValueError as e:
                    emit("error", {"message": str(e)})
                    emit("done", {"ok": False})
                    return

            emit("node_start", {"node": "outline_architect"})
            outline_out = outline_architect.run(
                genre_decision,
                contract,
                gateway,
                pid,
                on_llm_delta=lambda d: emit("llm_delta", {"node": "outline_architect", "text": d}),
            )
            emit("node_end", {"node": "outline_architect", "ok": True})

            emit("node_start", {"node": "outline_chapter_draft"})
            draft_chapters = outline_chapter_draft.run(
                genre_decision,
                contract,
                outline_out.first_volume_outline,
                gateway,
                pid,
                on_llm_delta=lambda d: emit("llm_delta", {"node": "outline_chapter_draft", "text": d}),
            )
            emit("node_end", {"node": "outline_chapter_draft", "ok": True})

            outline_for_critic = OutlineArchitectOutput(
                first_volume_outline=outline_out.first_volume_outline,
                chapters=draft_chapters,
            )
            emit("node_start", {"node": "initial_critic"})
            chapter_list = initial_critic.run(
                genre_decision,
                contract,
                outline_for_critic,
                gateway,
                pid,
                on_llm_delta=lambda d: emit("llm_delta", {"node": "initial_critic", "text": d}),
            )
            emit("node_end", {"node": "initial_critic", "ok": True})

            bundle = InitNovelResponse(
                novel_seed=seed,
                story_contract=contract,
                first_volume_outline=outline_out.first_volume_outline,
                chapter_contracts=chapter_list,
            )
            emit(
                "artifact",
                {"kind": "InitNovelBundle", "data": bundle.model_dump(mode="json", by_alias=True)},
            )
            emit("done", {"ok": True})
        except ValidationError as e:
            emit("error", {"message": f"Init novel validation failed: {e}"})
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
