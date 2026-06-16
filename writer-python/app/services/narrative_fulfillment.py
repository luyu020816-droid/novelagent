"""本章任务单履约评估（启发式 + 可选 LLM）。

与 Java ``NarrativeFulfillmentService`` 对齐：对照 ``chapterObligations`` 检查正文是否体现
汇合摘要与子文本窗口条目。供 ``POST /api/writer/chapters/narrative-fulfillment`` 调用。
"""

from __future__ import annotations

import json
from typing import Any

from app.services.llm_gateway import LLMGateway


def heuristic_fulfillment(obligations: dict[str, Any], chapter_text: str) -> dict[str, Any]:
    """关键词级快速检查；无 LLM 时 Java 侧 fallback 亦使用同等逻辑。"""
    text = chapter_text or ""
    lower = text.lower()
    conf_ok = True
    conf_rows: list[dict[str, Any]] = []
    for c in obligations.get("dueConfluences") or []:
        if not isinstance(c, dict):
            continue
        hint = str(c.get("contextSummary") or c.get("notes") or "")
        passed = not hint or hint in text or hint.lower() in lower
        conf_rows.append({"id": c.get("id"), "pass": passed, "note": "ok" if passed else "汇合关键词未出现"})
        if not passed:
            conf_ok = False
    sub_ok = True
    sub_rows: list[dict[str, Any]] = []
    for s in obligations.get("dueSubtextInWindow") or []:
        if not isinstance(s, dict):
            continue
        q = str(s.get("question") or "")
        probe = q[:12] if len(q) > 12 else q
        passed = not probe or probe in text
        sub_rows.append({"id": s.get("id"), "pass": passed, "note": "ok" if passed else "子文本未体现"})
        if not passed:
            sub_ok = False
    overall = conf_ok and sub_ok
    return {
        "confluenceFulfilled": conf_rows,
        "subtextAddressed": sub_rows,
        "overallPass": overall,
        "summaryLine": "启发式校验通过" if overall else "启发式校验：部分任务单未体现",
        "source": "heuristic",
    }


def llm_fulfillment(
    gateway: LLMGateway,
    *,
    project_id: str,
    chapter_no: int,
    obligations: dict[str, Any],
    chapter_text: str,
) -> dict[str, Any]:
    """调用 LLM 输出结构化 JSON；解析失败时退回 ``heuristic_fulfillment``。"""
    sys = (
        "你是叙事结构审阅员。对照 chapterObligations 检查 chapterText 是否落实汇合、子文本窗口与里程碑。"
        '输出 JSON：{"overallPass":bool,"summaryLine":"…","confluenceFulfilled":[{"id":"…","pass":bool,"note":"…"}],'
        '"subtextAddressed":[{"id":"…","pass":bool,"note":"…"}]}'
    )
    user = json.dumps(
        {"chapterObligations": obligations, "chapterText": chapter_text[:40000]},
        ensure_ascii=False,
    )[:50000]
    res = gateway.chat_completion(
        messages=[{"role": "system", "content": sys}, {"role": "user", "content": user}],
        response_format_json=True,
        temperature=0.1,
        agent_name="chapter_gen",
        node_name="narrative_fulfillment",
        project_id=project_id,
        chapter_no=chapter_no,
        on_delta=None,
    )
    out = json.loads(res.text)
    if isinstance(out, dict):
        out["source"] = "llm"
        return out
    return heuristic_fulfillment(obligations, chapter_text)
