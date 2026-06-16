"""Day 11：Critic 通过后对正文做纯文笔润色（不改剧情事实）。"""

from __future__ import annotations

import logging
from pathlib import Path
from typing import Any

from app.chapter_length_policy import CHAPTER_BODY_MAX_CHARS, CHAPTER_BODY_MIN_CHARS
from app.nodes._sse import sse_llm_delta
from app.services.llm_gateway import LLMGateway

logger = logging.getLogger(__name__)

_PROMPT_PATH = Path(__file__).resolve().parent.parent / "prompts" / "stylist_v1.md"


def _load_stylist_system() -> str:
    try:
        return _PROMPT_PATH.read_text(encoding="utf-8").strip()
    except OSError as e:
        logger.warning("stylist prompt missing: %s", e)
        return (
            "你是文笔编辑。只润色中文小说正文，禁止改变剧情事实、人物决定与设定。"
            "不要输出 JSON。若无法保证不改剧情，则原样返回用户正文。"
        )


def stylist_node(state: dict[str, Any], *, gateway: LLMGateway) -> dict[str, Any]:
    raw = (state.get("chapter_text") or "").strip()
    if not raw:
        return {"styled_text": ""}

    sys = (
        _load_stylist_system()
        + f"\n\n【篇幅】润色后正文字符数应仍大致落在 {CHAPTER_BODY_MIN_CHARS}～{CHAPTER_BODY_MAX_CHARS} 区间内"
        "（与润色前同量级）；禁止为洗稿而大量删节导致明显低于下界。"
    )
    user = "以下为须润色的章节正文（勿改剧情事实）：\n\n" + raw[:100000]

    try:
        res = gateway.chat_completion(
            messages=[{"role": "system", "content": sys}, {"role": "user", "content": user}],
            response_format_json=False,
            temperature=0.25,
            agent_name="chapter_gen",
            node_name="stylist",
            project_id=state.get("project_id"),
            chapter_no=state.get("chapter_no"),
            on_delta=lambda t: sse_llm_delta("stylist", t),
        )
        out = (res.text or "").strip()
        if not out:
            out = raw
        return {"styled_text": out}
    except Exception as e:
        logger.warning("stylist failed, fallback to raw chapter_text: %s", e)
        return {"styled_text": raw}
