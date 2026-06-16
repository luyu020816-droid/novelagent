"""Story Canon 规则校验（参考 PlotPilot consistency_checker，仅确定性规则）。"""

from __future__ import annotations

from typing import Any


def check_must_retain_facts_present(
    story_contract: dict[str, Any] | None,
    *,
    context_text: str,
) -> list[str]:
    """mustRetainFacts 中较短的事实串应出现在上下文拼接文本中（Curator 注入后）。"""
    violations: list[str] = []
    sc = story_contract if isinstance(story_contract, dict) else {}
    mrf = sc.get("mustRetainFacts") or sc.get("must_retain_facts")
    if not isinstance(mrf, list) or not context_text:
        return violations
    blob = context_text
    for raw in mrf[:20]:
        if not isinstance(raw, str):
            continue
        fact = raw.strip()
        if len(fact) < 4:
            continue
        needle = fact[: min(24, len(fact))]
        if needle and needle not in blob:
            violations.append(f"must_retain_fact missing in context: {needle[:40]}…")
    return violations


def check_story_canon_shrink_safe(canon: dict[str, Any]) -> list[str]:
    """压缩后仍应保留 author_governance 键。"""
    if not isinstance(canon, dict):
        return ["canon not object"]
    ag = canon.get("author_governance")
    if canon and ag is None:
        return ["author_governance missing after canon build"]
    return []
