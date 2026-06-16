"""定稿后扩展：文风提示 + 叙事债建议（写入 summary 扩展字段）。"""

from __future__ import annotations

import json
from typing import Any

from app.services.llm_gateway import LLMGateway
from app.services.prompt_cpms import load_node_prompt


def enrich_aftermath_metadata(
    gateway: LLMGateway,
    *,
    project_id: str,
    chapter_no: int,
    chapter_text: str,
) -> dict[str, Any]:
    text = (chapter_text or "").strip()
    if not text:
        return {}
    try:
        sys = load_node_prompt("aftermath_enrich", fallback_file="aftermath_enrich_v1.md")
    except FileNotFoundError:
        return {}
    user = f"project={project_id} chapter_no={chapter_no}\n\n章节正文：\n" + text[:48000]
    res = gateway.chat_completion(
        messages=[{"role": "system", "content": sys}, {"role": "user", "content": user}],
        response_format_json=True,
        temperature=0.15,
        agent_name="chapter_gen",
        node_name="aftermath_enrich",
        project_id=project_id,
        chapter_no=chapter_no,
        on_delta=None,
    )
    obj = json.loads(res.text)
    return obj if isinstance(obj, dict) else {}
