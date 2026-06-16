"""领域一致性检查（PlotPilot consistency_checker 简化版）。"""

from __future__ import annotations

from typing import Any

from app.services.story_canon_consistency import check_must_retain_facts_present


def run_consistency_check(state: dict[str, Any]) -> dict[str, Any]:
    """对照 story_contract / story_canon 与正文做确定性校验。"""
    text = str(state.get("chapter_text") or "")
    story = state.get("story_contract") if isinstance(state.get("story_contract"), dict) else {}
    pack = state.get("context_pack") if isinstance(state.get("context_pack"), dict) else {}
    canon = pack.get("story_canon") if isinstance(pack.get("story_canon"), dict) else {}

    violations: list[str] = []
    violations.extend(check_must_retain_facts_present(story, context_text=text))

    mrf = canon.get("must_retain_facts") or canon.get("mustRetainFacts")
    if isinstance(mrf, list):
        for raw in mrf[:20]:
            if not isinstance(raw, str):
                continue
            fact = raw.strip()
            if len(fact) < 4:
                continue
            needle = fact[: min(24, len(fact))]
            if needle and needle not in text:
                violations.append(f"canon 事实未体现: {needle[:40]}")

    nn = canon.get("author_governance") or {}
    if isinstance(nn, dict):
        for raw in (nn.get("non_negotiables") or nn.get("nonNegotiables") or [])[:10]:
            if isinstance(raw, str) and len(raw.strip()) >= 4:
                frag = raw.strip()[:12]
                if frag in ("不写系统面板", "无系统") and frag not in text:
                    pass  # 仅检查禁止项出现即违规较复杂，略
                if "禁止" in raw and raw.strip()[:8] in text:
                    violations.append(f"可能违背 non_negotiable: {raw[:30]}")

    rep = state.get("critic_report") if isinstance(state.get("critic_report"), dict) else {}
    checks = rep.get("consistency_checks") if isinstance(rep.get("consistency_checks"), dict) else {}
    if checks.get("protagonist_aligned") is False:
        violations.append("Critic: protagonist_aligned=false")
    if checks.get("relationships_plausible") is False:
        violations.append("Critic: relationships_plausible=false")

    return {"ok": len(violations) == 0, "violations": violations[:12]}
