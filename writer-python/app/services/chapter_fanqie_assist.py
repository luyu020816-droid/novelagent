"""番茄编辑点评、动笔前章梗、合并意见润色（单轮 LLM）。"""

from __future__ import annotations

import json
from typing import Any

from app.services.llm_gateway import LLMGateway
from app.services.prompt_registry import load_prompt


def run_fanqie_editor_review(
    *,
    gateway: LLMGateway,
    project_id: str,
    chapter_no: int,
    story_contract: dict[str, Any],
    chapter_contract: dict[str, Any],
    chapter_text: str,
) -> str:
    system = load_prompt("fanqie_editor_v1.md")
    user = (
        f"chapter_no={chapter_no}\n\n"
        "【story_contract JSON】\n"
        + json.dumps(story_contract, ensure_ascii=False)[:14000]
        + "\n\n【chapter_contract JSON】\n"
        + json.dumps(chapter_contract, ensure_ascii=False)[:12000]
        + "\n\n【本章正文】\n"
        + (chapter_text or "").strip()[:80000]
    )
    gr = gateway.chat_completion(
        messages=[{"role": "system", "content": system}, {"role": "user", "content": user}],
        response_format_json=False,
        temperature=0.25,
        agent_name="chapter_fanqie",
        node_name="fanqie_review",
        project_id=project_id,
        chapter_no=chapter_no,
        on_delta=None,
    )
    return (gr.text or "").strip()


def run_polish_with_notes(
    *,
    gateway: LLMGateway,
    project_id: str,
    chapter_no: int,
    chapter_text: str,
    tomato_review: str,
    author_notes: str,
) -> str:
    system = load_prompt("chapter_polish_combined_v1.md")
    user = (
        "【番茄编辑意见】\n"
        + (tomato_review or "").strip()[:24000]
        + "\n\n【作者补充】\n"
        + (author_notes or "").strip()[:24000]
        + "\n\n【须修改的章节正文】\n"
        + (chapter_text or "").strip()[:100000]
    )
    gr = gateway.chat_completion(
        messages=[{"role": "system", "content": system}, {"role": "user", "content": user}],
        response_format_json=False,
        temperature=0.28,
        agent_name="chapter_fanqie",
        node_name="polish_combined",
        project_id=project_id,
        chapter_no=chapter_no,
        on_delta=None,
    )
    out = (gr.text or "").strip()
    if not out:
        raise RuntimeError("polish_combined produced empty text")
    return out


def run_propose_chapter_plan_summary(
    *,
    gateway: LLMGateway,
    project_id: str,
    chapter_no: int,
    story_contract: dict[str, Any],
    chapter_contract: dict[str, Any],
    prev_chapter_commit_summary: dict[str, Any] | None,
) -> str:
    system = load_prompt("propose_chapter_plan_v1.md")
    prev_blob = ""
    if prev_chapter_commit_summary:
        prev_blob = json.dumps(prev_chapter_commit_summary, ensure_ascii=False)[:12000]
    user = (
        f"chapter_no={chapter_no}\n\n"
        "【story_contract】\n"
        + json.dumps(story_contract, ensure_ascii=False)[:14000]
        + "\n\n【chapter_contract】\n"
        + json.dumps(chapter_contract, ensure_ascii=False)[:12000]
        + "\n\n【上一章已定稿摘要 JSON（若无则说明是首章或无前文）】\n"
        + (prev_blob if prev_blob else "（无）")
    )
    gr = gateway.chat_completion(
        messages=[{"role": "system", "content": system}, {"role": "user", "content": user}],
        response_format_json=False,
        temperature=0.3,
        agent_name="chapter_fanqie",
        node_name="propose_plan",
        project_id=project_id,
        chapter_no=chapter_no,
        on_delta=None,
    )
    return (gr.text or "").strip()
