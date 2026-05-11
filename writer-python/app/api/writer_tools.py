"""辅助接口：风格指纹、外部 Agent 意图预演。"""

from __future__ import annotations

import json
import re

from fastapi import APIRouter, HTTPException

from app.config import get_settings
from app.schemas.agent_intent import AgentIntentRequest, AgentIntentResponse, SuggestedAction
from app.schemas.style_analyze import StyleAnalyzeRequest, StyleAnalyzeResponse
from app.services.llm_gateway import LLMGateway

router = APIRouter(tags=["writer"])


@router.post("/api/writer/style/analyze", response_model=StyleAnalyzeResponse)
def style_analyze(body: StyleAnalyzeRequest) -> StyleAnalyzeResponse:
    settings = get_settings()
    if not settings.openai_api_key:
        raise HTTPException(status_code=503, detail="OPENAI_API_KEY is not set.")
    text = body.sample_text.strip()
    parts = re.split(r"[。！？.!?]+", text)
    nonempty = [p.strip() for p in parts if p.strip()]
    avg_len = (sum(len(p) for p in nonempty) / len(nonempty)) if nonempty else 0.0
    gw = LLMGateway(settings)
    sys = (
        "你是写作教练。根据作者给的正文片段，写出一段给 AI 代笔用的「风格约束」Markdown（不超过400字），"
        "包含：叙事人称与距离、节奏偏好、对话密度、忌讳修辞类型；不要复述剧情。"
    )
    res = gw.chat_completion(
        messages=[
            {"role": "system", "content": sys},
            {"role": "user", "content": text[:12000]},
        ],
        temperature=0.25,
        agent_name="style_tools",
        node_name="style_analyze",
        project_id=None,
        chapter_no=None,
        on_delta=None,
    )
    guide = (res.text or "").strip() or "（未能生成风格指导，请加长样本后再试）"
    return StyleAnalyzeResponse(
        avgSentenceLen=round(avg_len, 2),
        sampleChars=len(text),
        styleGuideMd=guide,
    )


@router.post("/api/writer/agent/intent-preview", response_model=AgentIntentResponse)
def agent_intent_preview(body: AgentIntentRequest) -> AgentIntentResponse:
    settings = get_settings()
    if not settings.openai_api_key:
        raise HTTPException(status_code=503, detail="OPENAI_API_KEY is not set.")
    gw = LLMGateway(settings)
    sys = (
        "你是 MythosForge 写作 IDE 的指令解析器。用户会用自然语言描述想做的事。"
        '只输出 JSON：{"suggestedActions":[{"action":"string","detail":"string"},...]}，'
        "action 取值示例：open_story_init / edit_outline / regenerate_chapter / rename_entity / run_consistency / export_md / other；"
        "至少 1 条，至多 5 条；detail 用简短中文说明。"
    )
    user = json.dumps({"projectId": body.project_id, "message": body.message}, ensure_ascii=False)
    res = gw.chat_completion(
        messages=[{"role": "system", "content": sys}, {"role": "user", "content": user}],
        response_format_json=True,
        temperature=0.2,
        agent_name="agent_intent",
        node_name="intent_preview",
        project_id=body.project_id,
        chapter_no=None,
        on_delta=None,
    )
    try:
        obj = json.loads(res.text)
        raw = obj.get("suggestedActions") if isinstance(obj, dict) else None
        actions: list[SuggestedAction] = []
        if isinstance(raw, list):
            for x in raw[:5]:
                if isinstance(x, dict) and x.get("action"):
                    actions.append(
                        SuggestedAction(action=str(x["action"]), detail=str(x.get("detail") or ""))
                    )
        if not actions:
            actions = [SuggestedAction(action="other", detail=body.message[:200])]
    except Exception as e:
        raise HTTPException(status_code=502, detail=f"intent parse failed: {e}") from e
    return AgentIntentResponse(suggestedActions=actions)
