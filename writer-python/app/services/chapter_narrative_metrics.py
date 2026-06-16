"""章后叙事指标：张力与文风相似度（供 Java accept 后落库）。"""

from __future__ import annotations

import json
import logging
from typing import Any

from app.services.llm_gateway import LLMGateway
from app.services.narrative_metrics_heuristic import (
    _heuristic_style_similarity,
    _heuristic_tension,
)

_log = logging.getLogger(__name__)


def compute_narrative_metrics(
    gateway: LLMGateway,
    *,
    project_id: str,
    chapter_no: int,
    chapter_text: str,
) -> dict[str, Any]:
    """返回 tensionScore、styleSimilarity、raw（可审计）。"""
    sys = (
        "你是网文编辑。通读本章正文，输出严格 JSON（不要 markdown）："
        '{"tensionScore":1-10的数,"styleSimilarity":0-1的数,"clicheFlags":["可选短标签"],'
        '"notes":"一句评语"}\n'
        "tensionScore：情节推进与悬念强度；styleSimilarity：相对成熟网文的自然度（非与历史章向量比对时的近似）。"
    )
    user = json.dumps(
        {"projectId": project_id, "chapterNo": chapter_no, "text": chapter_text[:50000]},
        ensure_ascii=False,
    )
    try:
        res = gateway.chat_completion(
            messages=[{"role": "system", "content": sys}, {"role": "user", "content": user}],
            response_format_json=True,
            temperature=0.1,
            agent_name="chapter_gen",
            node_name="narrative_metrics",
            project_id=project_id,
            chapter_no=chapter_no,
            on_delta=None,
        )
        data = json.loads(res.text)
        if not isinstance(data, dict):
            raise ValueError("metrics not object")
        t = float(data.get("tensionScore", 5))
        s = float(data.get("styleSimilarity", 0.75))
        t = max(1.0, min(10.0, t))
        s = max(0.0, min(1.0, s))
        return {
            "tensionScore": round(t, 2),
            "styleSimilarity": round(s, 3),
            "raw": data,
        }
    except Exception as e:
        _log.warning("[narrative_metrics] LLM failed project=%s ch=%s: %s", project_id, chapter_no, e)
        return {
            "tensionScore": _heuristic_tension(chapter_text),
            "styleSimilarity": _heuristic_style_similarity(chapter_text),
            "raw": {"fallback": "heuristic", "error": str(e)[:500]},
        }
