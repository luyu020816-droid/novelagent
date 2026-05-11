"""人工定稿后同步抽取滚动摘要（Java `/api/writer/chapters/summarize` 调用）。"""

from __future__ import annotations

import json
from typing import Any

from app.services.llm_gateway import LLMGateway


def summarize_chapter_text(
    gateway: LLMGateway,
    *,
    project_id: str,
    chapter_no: int,
    chapter_text: str,
) -> dict[str, Any]:
    text = (chapter_text or "").strip()
    if not text:
        raise ValueError("chapter_text is empty")

    sys = (
        "你是长篇小说的滚动摘要编辑。将给定章节正文压缩为严格 JSON（不要 markdown）：\n"
        '{"key_events":["章节内关键剧情事实，短句"],"character_state":"主要人物状态与关系变化摘要",'
        '"pending_foreshadowing":["尚未回收的伏笔或悬念条目"]}\n'
        "须客观、可检索，避免空话。"
    )
    user = f"project={project_id} chapter_no={chapter_no}\n\n章节正文：\n" + text[:60000]
    res = gateway.chat_completion(
        messages=[{"role": "system", "content": sys}, {"role": "user", "content": user}],
        response_format_json=True,
        temperature=0.2,
        agent_name="chapter_gen",
        node_name="summarize",
        project_id=project_id,
        chapter_no=chapter_no,
        on_delta=None,
    )
    obj = json.loads(res.text)
    if not isinstance(obj, dict):
        raise ValueError("summary not object")
    for k in ("key_events", "character_state", "pending_foreshadowing"):
        if k not in obj:
            raise ValueError(f"missing {k}")
    return obj
