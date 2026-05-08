from __future__ import annotations

from fastapi import APIRouter, HTTPException

from app.config import get_settings
from app.schemas.test_agent import TestAgentOutput, TestAgentRequest
from app.services.json_repair import validate_or_repair
from app.services.llm_gateway import LLMGateway
from app.services.prompt_registry import load_prompt

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
