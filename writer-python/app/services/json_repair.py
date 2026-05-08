from __future__ import annotations

import json
from typing import Any, TypeVar

from pydantic import BaseModel, ValidationError

from app.services.llm_gateway import LLMGateway

T = TypeVar("T", bound=BaseModel)

REPAIR_SYSTEM = (
    "You fix malformed JSON. Reply with a single JSON object only, no markdown, no commentary. "
    "The object must satisfy the caller's schema description in the user message."
)


def validate_or_repair(
    raw_text: str,
    model_type: type[T],
    gateway: LLMGateway,
    *,
    agent_name: str,
    repair_context: str,
    job_id: str | None = None,
    project_id: str | None = None,
) -> tuple[T, bool]:
    """
    Validate JSON against Pydantic model; on failure run exactly one repair via LLMGateway.
    Returns (model_instance, repaired_flag).
    """
    try:
        data: Any = json.loads(raw_text.strip())
        try:
            return model_type.model_validate(data), False
        except ValidationError:
            pass
    except json.JSONDecodeError:
        pass

    user_msg = (
        f"{repair_context}\n\n"
        f"Broken output (fix into valid JSON for the schema):\n{raw_text}"
    )
    result = gateway.chat_completion(
        messages=[
            {"role": "system", "content": REPAIR_SYSTEM},
            {"role": "user", "content": user_msg},
        ],
        temperature=0,
        response_format_json=True,
        agent_name=agent_name,
        node_name="json_repair",
        job_id=job_id,
        project_id=project_id,
    )
    try:
        data2: Any = json.loads(result.text.strip())
    except json.JSONDecodeError as e:
        raise ValueError(f"Repair returned non-JSON: {result.text[:800]}") from e
    return model_type.model_validate(data2), True
