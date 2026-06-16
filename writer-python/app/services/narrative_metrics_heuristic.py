"""章后叙事指标启发式（无 OpenAI 依赖，供 API 无 Key 路径与单测）。"""

from __future__ import annotations

import re
from typing import Any


def _heuristic_tension(text: str) -> float:
    n = len(text)
    if n < 800:
        return 2.0
    q = len(re.findall(r"[「『\"]", text))
    ex = text.count("！") + text.count("!")
    base = 4.0 + min(2.0, n / 8000.0) + min(1.5, q * 0.08) + min(1.0, ex * 0.12)
    return max(1.0, min(10.0, round(base, 2)))


def _heuristic_style_similarity(text: str) -> float:
    if len(text) < 200:
        return 0.4
    sents = re.split(r"[。！？\n]", text)
    sents = [s.strip() for s in sents if len(s.strip()) > 8]
    if len(sents) < 3:
        return 0.75
    uniq = len(set(sents))
    ratio = uniq / len(sents)
    return max(0.35, min(0.98, round(0.55 + ratio * 0.35, 3)))


def heuristic_metrics_no_llm(chapter_text: str) -> dict[str, Any]:
    return {
        "tensionScore": _heuristic_tension(chapter_text),
        "styleSimilarity": _heuristic_style_similarity(chapter_text),
        "raw": {"stub": True, "reason": "no_openai_api_key"},
    }
