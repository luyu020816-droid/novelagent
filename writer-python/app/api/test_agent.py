from __future__ import annotations

from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse

from app.config import get_settings
from app.schemas.test_agent import TestAgentOutput, TestAgentRequest
from app.services.json_repair import validate_or_repair
from app.services.llm_gateway import LLMGateway
from app.services.prompt_registry import load_prompt
from app.services.sse_queue_runner import sse_threaded_generator

router = APIRouter(tags=["writer"])


@router.post("/api/writer/test-agent", response_model=TestAgentOutput)
def test_agent(body: TestAgentRequest | None = None) -> TestAgentOutput:
    body = body or TestAgentRequest()
    settings = get_settings()
    if not settings.openai_api_key:
        raise HTTPException(
            status_code=503,
            detail="OPENAI_API_KEY is not set (environment or .env).",
        )

    system_prompt = load_prompt("test_agent_v1.md")
    user_lines: list[str] = []
    if body.user_hint:
        user_lines.append(f"User hint: {body.user_hint}")
    user_lines.append("Respond with JSON only, following the system instructions.")
    user_message = "\n".join(user_lines)

    gateway = LLMGateway(settings)
    gr = gateway.chat_completion(
        messages=[
            {"role": "system", "content": system_prompt},
            {"role": "user", "content": user_message},
        ],
        temperature=0.3,
        response_format_json=True,
        agent_name="test_agent",
        node_name="main",
    )

    schema_doc = 'Required JSON shape: {"ok": boolean, "message": string, "items": array of strings}'

    try:
        out, _repaired = validate_or_repair(
            gr.text,
            TestAgentOutput,
            gateway,
            agent_name="test_agent",
            repair_context=schema_doc,
        )
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"Output validation failed: {e}") from e

    return out


@router.post("/api/writer/test-agent/stream")
def test_agent_stream(body: TestAgentRequest | None = None) -> StreamingResponse:
    body = body or TestAgentRequest()
    settings = get_settings()
    if not settings.openai_api_key:
        raise HTTPException(
            status_code=503,
            detail="OPENAI_API_KEY is not set (environment or .env).",
        )

    system_prompt = load_prompt("test_agent_v1.md")
    user_lines: list[str] = []
    if body.user_hint:
        user_lines.append(f"User hint: {body.user_hint}")
    user_lines.append("Respond with JSON only, following the system instructions.")
    user_message = "\n".join(user_lines)
    schema_doc = 'Required JSON shape: {"ok": boolean, "message": string, "items": array of strings}'

    def worker(emit) -> None:
        gateway = LLMGateway(settings)
        emit("pipeline_start", {"pipeline": "test_agent"})
        try:
            emit("node_start", {"node": "test_agent"})
            gr = gateway.chat_completion(
                messages=[
                    {"role": "system", "content": system_prompt},
                    {"role": "user", "content": user_message},
                ],
                temperature=0.3,
                response_format_json=True,
                agent_name="test_agent",
                node_name="main",
                on_delta=lambda d: emit("llm_delta", {"node": "test_agent", "text": d}),
            )
            emit("node_end", {"node": "test_agent", "ok": True})

            emit("node_start", {"node": "test_agent_json_repair"})
            out, repaired = validate_or_repair(
                gr.text,
                TestAgentOutput,
                gateway,
                agent_name="test_agent",
                repair_context=schema_doc,
                on_llm_delta=lambda d: emit("llm_delta", {"node": "test_agent_json_repair", "text": d}),
            )
            emit("node_end", {"node": "test_agent_json_repair", "ok": True, "repaired": repaired})

            emit("artifact", {"kind": "TestAgentOutput", "data": out.model_dump(mode="json", by_alias=True)})
            emit("done", {"ok": True})
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
