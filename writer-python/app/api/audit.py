"""低成本全书一致性抽查（两段摘录对比）。"""

from __future__ import annotations

import json
import logging

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel, Field

from app.config import get_settings
from app.services.llm_gateway import LLMGateway

logger = logging.getLogger(__name__)

router = APIRouter(tags=["writer"])


class ConsistencySpotcheckRequest(BaseModel):
    project_id: str = Field(alias="projectId")
    chapter_a_no: int = Field(alias="chapterANo", ge=1)
    chapter_b_no: int = Field(alias="chapterBNo", ge=1)
    excerpt_a: str = Field(alias="excerptA", min_length=1, max_length=32000)
    excerpt_b: str = Field(alias="excerptB", min_length=1, max_length=32000)

    model_config = {"populate_by_name": True}


_SYS = """你是长篇小说设定一致性审查。比较两段摘录（可能来自不同章节），判断是否出现明显的人设/世界观/关键事实冲突。

只输出严格 JSON（不要 markdown）：
{"likely_conflict": true/false, "notes": "一句中文说明"}"""


@router.post("/api/writer/audit/consistency-spotcheck")
def consistency_spotcheck(body: ConsistencySpotcheckRequest) -> dict[str, bool | str]:
    settings = get_settings()
    if not settings.openai_api_key:
        raise HTTPException(status_code=503, detail="OPENAI_API_KEY is not set.")
    gw = LLMGateway()
    user = json.dumps(
        {
            "projectId": body.project_id,
            "chapterANo": body.chapter_a_no,
            "chapterBNo": body.chapter_b_no,
            "excerptA": body.excerpt_a[:24000],
            "excerptB": body.excerpt_b[:24000],
        },
        ensure_ascii=False,
    )
    try:
        res = gw.chat_completion(
            messages=[{"role": "system", "content": _SYS}, {"role": "user", "content": user}],
            response_format_json=True,
            temperature=0.1,
            agent_name="audit",
            node_name="consistency_spotcheck",
            project_id=body.project_id,
            chapter_no=body.chapter_b_no,
            on_delta=None,
        )
        obj = json.loads(res.text)
    except Exception as e:
        logger.exception("consistency_spotcheck failed")
        raise HTTPException(status_code=502, detail=str(e)) from e
    if not isinstance(obj, dict) or "likely_conflict" not in obj:
        raise HTTPException(status_code=502, detail="invalid LLM JSON")
    return {
        "likelyConflict": bool(obj.get("likely_conflict")),
        "notes": str(obj.get("notes") or ""),
    }
