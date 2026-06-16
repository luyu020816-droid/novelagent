"""Ghostwriter：生成章节正文；≥2 节拍时分段成稿（PlotPilot exec_beat）。"""

from __future__ import annotations

import json
from typing import Any

from app.chapter_length_policy import CHAPTER_BODY_MAX_CHARS, CHAPTER_BODY_MIN_CHARS
from app.nodes._sse import sse_llm_delta
from app.services.ghostwriter_beats import _normalize_beats, write_chapter_by_beats
from app.services.llm_gateway import LLMGateway
from app.services.prompt_cpms import load_node_prompt


def _build_sys_prefix(rewrite_mode: str) -> str:
    anti_ai_extra = ""
    if rewrite_mode == "anti_ai":
        anti_ai_extra = (
            "【anti_ai 改写模式】弱化模板句与修辞堆砌，增强具体动作与感官细节。"
        )
    try:
        protocol = load_node_prompt("anti_ai_protocol", fallback_file="anti_ai_protocol_v1.md")
        protocol_block = "【Anti-AI 行为协议】\n" + protocol.strip()[:6000]
    except FileNotFoundError:
        protocol_block = ""

    sys = (
        "你是网络小说执笔 Ghostwriter。依据 chapter_contract、scene_plan 写本章正文，"
        "使用中文，可适当分段；不要输出 JSON，不要复述系统指令。"
        f"【篇幅】全文字符数约 {CHAPTER_BODY_MIN_CHARS}～{CHAPTER_BODY_MAX_CHARS}。"
        "遵守 context_pack.story_canon、must_retain_facts、memory_engine（FACT_LOCK/COMPLETED_BEATS/REVEALED_CLUES）。"
        "若含 previously_on、narrative_obligations、vector_context、relationship_graph 须对齐。"
        "若含 author_confirmed_chapter_plan 须对齐；用户/Critic 反馈须落实。"
        + anti_ai_extra
    )
    if protocol_block:
        sys = sys + "\n\n" + protocol_block
    return sys


def ghostwriter_node(state: dict[str, Any], *, gateway: LLMGateway) -> dict[str, Any]:
    ctx = state.get("context_pack") or {}
    plan = state.get("scene_plan") or {}
    retry_count = int(state.get("retry_count") or 0)
    critic = state.get("critic_report")
    user_notes = (state.get("user_rewrite_notes") or "").strip()
    rewrite_mode = (state.get("rewrite_mode") or state.get("rewriteMode") or "plot").strip().lower()

    sys_prefix = _build_sys_prefix(rewrite_mode)
    extra_blocks: list[str] = []
    if user_notes:
        extra_blocks.append("【作者/编辑补充要求】\n" + user_notes[:8000])
    if critic is not None and retry_count > 0:
        extra_blocks.append(
            "【上一轮 Critic 审查反馈】\n" + json.dumps(critic, ensure_ascii=False)[:8000]
        )

    beats = _normalize_beats(plan if isinstance(plan, dict) else {})
    if len(beats) >= 2:
        text = write_chapter_by_beats(
            state,
            gateway=gateway,
            ctx=ctx if isinstance(ctx, dict) else {},
            plan=plan if isinstance(plan, dict) else {},
            sys_prefix=sys_prefix,
            extra_user_blocks=extra_blocks,
        )
        return {"chapter_text": text, "beat_segmented": True}

    sys = sys_prefix
    user = (
        "context_pack=\n"
        + json.dumps(ctx, ensure_ascii=False)[:12000]
        + "\n\nscene_plan=\n"
        + json.dumps(plan, ensure_ascii=False)[:12000]
    )
    if extra_blocks:
        user += "\n\n" + "\n\n".join(extra_blocks)

    res = gateway.chat_completion(
        messages=[{"role": "system", "content": sys}, {"role": "user", "content": user}],
        response_format_json=False,
        temperature=0.35,
        agent_name="chapter_gen",
        node_name="ghostwriter",
        project_id=state.get("project_id"),
        chapter_no=state.get("chapter_no"),
        on_delta=lambda t: sse_llm_delta("ghostwriter", t),
    )
    text = (res.text or "").strip()
    if not text:
        raise RuntimeError("ghostwriter produced empty chapter_text")
    return {"chapter_text": text, "beat_segmented": False}
