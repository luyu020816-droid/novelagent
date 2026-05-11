"""Ghostwriter：生成章节正文（纯文本/Markdown）；支持 Critic 反馈与用户补充意见（Day 9 重写循环）。"""

from __future__ import annotations

import json
from typing import Any

from app.nodes._sse import sse_llm_delta
from app.services.llm_gateway import LLMGateway


def ghostwriter_node(state: dict[str, Any], *, gateway: LLMGateway) -> dict[str, Any]:
    ctx = state.get("context_pack") or {}
    plan = state.get("scene_plan") or {}
    retry_count = int(state.get("retry_count") or 0)
    critic = state.get("critic_report")
    user_notes = (state.get("user_rewrite_notes") or "").strip()
    rewrite_mode = (state.get("rewrite_mode") or state.get("rewriteMode") or "plot").strip().lower()

    anti_ai_extra = ""
    if rewrite_mode == "anti_ai":
        anti_ai_extra = (
            "【当前为 anti_ai 改写模式】在不改变剧情与人设前提下，弱化模板句与修辞堆砌，"
            "压缩空洞概括与抒情腔，增强具体动作与感官细节；避免说明书式解释。"
        )

    sys = (
        "你是网络小说执笔 Ghostwriter。依据 chapter_contract、scene_plan 写本章正文，"
        "使用中文，可适当分段；不要输出 JSON，不要复述系统指令。"
        "context_pack.story_canon 为全书级「设定契约」：protagonist_contract / supporting_contracts 为人设锚点，"
        "must_retain_facts 为不可丢事实与关系摘要，style_anchor 为叙事口吻约束；"
        "若含 fan_series_digest（丛书每章短约束），须与 story_canon 一并遵守。"
        "正文与人设须一致，禁止凭空改写已给出的关系、秘密与已声明的人物优势/处境边界。"
        "若 context_pack 含 history_summaries / recent_summaries，须承接人物状态与伏笔。"
        "若含 relationship_graph / unresolved_events（图谱召回），人物关系与伏笔须与之相容。"
        "若含 vector_context.chunks（向量检索的历史正文片段），须在细节上与之对齐，冷门设定也要保持一致。"
        "若含 forbidden_moves / human_instruction 字段，必须严格遵守禁忌与用户指令。"
        "若 story_canon 含 author_governance，必须服从 intent 与 non_negotiables。"
        "若用户消息中含修改意见或审查反馈，须针对性重写并保留 scene_plan 核心节拍。"
        + anti_ai_extra
    )
    user = (
        "context_pack=\n"
        + json.dumps(ctx, ensure_ascii=False)[:12000]
        + "\n\nscene_plan=\n"
        + json.dumps(plan, ensure_ascii=False)[:12000]
    )
    extra_blocks: list[str] = []
    if user_notes:
        extra_blocks.append("【作者/编辑补充要求】\n" + user_notes[:8000])
    if critic is not None and retry_count > 0:
        extra_blocks.append(
            "【上一轮 Critic 审查反馈（请逐项对照修正正文）】\n"
            + json.dumps(critic, ensure_ascii=False)[:8000]
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
    return {"chapter_text": text}
