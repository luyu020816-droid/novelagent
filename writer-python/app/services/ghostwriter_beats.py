"""按 scene_plan.beats 分段成稿（PlotPilot exec_beat 简化）。"""

from __future__ import annotations

import json
from typing import Any

from app.chapter_length_policy import CHAPTER_BODY_MAX_CHARS, CHAPTER_BODY_MIN_CHARS
from app.nodes._sse import sse_llm_delta
from app.services.llm_gateway import LLMGateway
from app.services.prompt_cpms import load_node_prompt


from app.services.scene_plan_beats import normalize_beats as _normalize_beats


def write_chapter_by_beats(
    state: dict[str, Any],
    *,
    gateway: LLMGateway,
    ctx: dict[str, Any],
    plan: dict[str, Any],
    sys_prefix: str,
    extra_user_blocks: list[str],
) -> str:
    beats = _normalize_beats(plan)
    if len(beats) < 2:
        raise ValueError("beats too few for segmented write")

    try:
        beat_sys = load_node_prompt("ghostwriter_beat", fallback_file="ghostwriter_beat_v1.md")
    except FileNotFoundError:
        beat_sys = "只写当前节拍正文片段，中文，不要 JSON。"

    target = max(CHAPTER_BODY_MIN_CHARS, min(CHAPTER_BODY_MAX_CHARS, int((CHAPTER_BODY_MIN_CHARS + CHAPTER_BODY_MAX_CHARS) / 2)))
    per_beat = max(400, target // len(beats))

    parts: list[str] = []
    prior = ""
    for idx, b in enumerate(beats):
        beat_sys_full = (
            sys_prefix
            + "\n\n"
            + beat_sys
            + f"\n\n【全章目标字数约 {CHAPTER_BODY_MIN_CHARS}～{CHAPTER_BODY_MAX_CHARS}；本节拍约 {per_beat} 字】"
        )
        user_parts = [
            f"节拍 {idx + 1}/{len(beats)}：{b['beat']}\n目标：{b['goal']}",
            "context_pack=\n" + json.dumps(ctx, ensure_ascii=False)[:10000],
            "scene_plan=\n" + json.dumps(plan, ensure_ascii=False)[:6000],
        ]
        if prior:
            user_parts.append("【已写前文（须衔接）】\n" + prior[-12000:])
        user_parts.extend(extra_user_blocks)
        user = "\n\n".join(user_parts)

        res = gateway.chat_completion(
            messages=[{"role": "system", "content": beat_sys_full}, {"role": "user", "content": user}],
            response_format_json=False,
            temperature=0.38,
            agent_name="chapter_gen",
            node_name="ghostwriter_beat",
            project_id=state.get("project_id"),
            chapter_no=state.get("chapter_no"),
            on_delta=lambda t: sse_llm_delta("ghostwriter", t),
        )
        chunk = (res.text or "").strip()
        if not chunk:
            raise RuntimeError(f"ghostwriter beat {idx + 1} empty")
        parts.append(chunk)
        prior = "\n\n".join(parts)

    return "\n\n".join(parts).strip()
