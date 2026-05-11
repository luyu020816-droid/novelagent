"""路径 B：多轮互动采访 Agent。"""

from __future__ import annotations

import json

from pydantic import ValidationError

from app.schemas.storyline_interview import GenreInterviewRequest, InterviewerResponse
from app.services.llm_gateway import LLMGateway
from app.services.prompt_registry import load_prompt


def _parse_llm_json(text: str) -> dict:
    t = text.strip()
    if t.startswith("```"):
        lines = t.split("\n")
        if lines and lines[0].lstrip().startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        t = "\n".join(lines)
    data = json.loads(t)
    if not isinstance(data, dict):
        raise ValueError("Interviewer output must be a JSON object")
    return data


def run(req: GenreInterviewRequest, gateway: LLMGateway) -> InterviewerResponse:
    system = load_prompt("storyline_interviewer_v1.md")
    schema_hint = json.dumps(InterviewerResponse.model_json_schema(), ensure_ascii=False)[:8000]
    system_full = (
        f"{system}\n\n"
        f"=== InterviewerResponse JSON Schema（节选）===\n{schema_hint}\n"
        "你必须只输出一个 JSON 对象，键名使用 camelCase：replyToUser、finalSummary、coreSettings。"
    )
    messages: list[dict[str, str]] = [{"role": "system", "content": system_full}]
    for turn in req.chat_history:
        messages.append({"role": turn.role, "content": turn.content})

    gr = gateway.chat_completion(
        messages=messages,
        response_format_json=True,
        temperature=0.4,
        agent_name="storyline_interviewer",
        node_name="main",
        project_id=req.project_id,
    )
    try:
        raw = _parse_llm_json(gr.text)
    except json.JSONDecodeError as e:
        raise RuntimeError(f"Interviewer returned non-JSON: {e}") from e

    # 允许模型偶尔输出 snake_case，统一转一层
    normalized: dict = {}
    for k, v in raw.items():
        if k == "reply_to_user":
            normalized["reply_to_user"] = v
        elif k == "replyToUser":
            normalized["reply_to_user"] = v
        elif k == "final_summary":
            normalized["final_summary"] = v
        elif k == "finalSummary":
            normalized["final_summary"] = v
        elif k == "core_settings":
            normalized["core_settings"] = v
        elif k == "coreSettings":
            normalized["core_settings"] = v
        elif k == "status":
            normalized["status"] = v
    try:
        return InterviewerResponse.model_validate(normalized)
    except ValidationError as e:
        raise ValueError(f"Interviewer JSON failed schema: {e}") from e
