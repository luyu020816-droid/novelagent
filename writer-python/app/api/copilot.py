"""写作 Copilot：初始化向导 / 卷纲参谋 / 章节教练。"""

from __future__ import annotations

from fastapi import APIRouter, HTTPException

from app.config import get_settings
from app.schemas.copilot import CopilotChatRequest, CopilotChatResponse
from app.services.llm_gateway import LLMGateway
from app.services.prompt_registry import load_prompt

router = APIRouter(tags=["writer"])

_SCENE_FILES = {
    "init_wizard": "copilot_init_wizard.md",
    "outline_edit": "copilot_outline_edit.md",
    "chapter_coach": "copilot_chapter_coach.md",
}


@router.post("/api/writer/copilot/chat", response_model=CopilotChatResponse)
def copilot_chat(body: CopilotChatRequest) -> CopilotChatResponse:
    settings = get_settings()
    if not settings.openai_api_key:
        raise HTTPException(status_code=503, detail="OPENAI_API_KEY is not set.")

    fname = _SCENE_FILES.get(body.scene)
    if not fname:
        raise HTTPException(status_code=400, detail="unknown scene")

    system = load_prompt(fname)
    gw = LLMGateway(settings)

    llm_messages: list[dict[str, str]] = [{"role": "system", "content": system}]
    ctx = (body.context_blob or "").strip()
    if ctx:
        llm_messages.append(
            {
                "role": "user",
                "content": "【用户提供的正文/大纲上下文】\n" + ctx[:24000],
            }
        )
        llm_messages.append(
            {
                "role": "assistant",
                "content": "已阅读上下文。请直接提出你的问题或修改想法；我会基于上文给出可执行建议。",
            },
        )

    for m in body.messages:
        llm_messages.append({"role": m.role, "content": m.content[:24000]})

    res = gw.chat_completion(
        messages=llm_messages,
        response_format_json=False,
        temperature=0.35,
        agent_name="copilot",
        node_name=body.scene,
        project_id=body.project_id,
        chapter_no=None,
        on_delta=None,
    )
    return CopilotChatResponse(reply=(res.text or "").strip())
