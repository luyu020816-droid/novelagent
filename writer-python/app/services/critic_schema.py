"""Critic 报告 JSON 契约（枚举 dimension id，供门禁与离线 eval）。"""

from __future__ import annotations

from typing import Any

CRITIC_DIMENSION_IDS: frozenset[str] = frozenset(
    {
        "outline_adherence",
        "character_voice",
        "canon_facts",
        "pacing_hooks",
        "prose_quality",
        "fan_series_rules",
        "author_governance",
        "chapter_length",
        "narrative_obligations",
        "content_compliance",
        "beat_coverage",
        "anti_ai_prose",
    }
)

HARD_GATE_DIMENSION_IDS: frozenset[str] = frozenset(
    {
        "beat_coverage",
        "content_compliance",
        "chapter_length",
    }
)


def validate_critic_report(report: dict[str, Any]) -> list[str]:
    """返回违规说明列表；空列表表示契约通过。"""
    errors: list[str] = []
    if not isinstance(report, dict):
        return ["report must be object"]
    if "pass" not in report:
        errors.append("missing pass")
    dims = report.get("dimensions")
    if dims is not None and not isinstance(dims, list):
        errors.append("dimensions must be array")
        return errors
    if isinstance(dims, list):
        for i, d in enumerate(dims):
            if not isinstance(d, dict):
                errors.append(f"dimensions[{i}] not object")
                continue
            did = d.get("id")
            if not isinstance(did, str) or did not in CRITIC_DIMENSION_IDS:
                errors.append(f"dimensions[{i}].id invalid: {did!r}")
            if "ok" in d and not isinstance(d.get("ok"), bool):
                errors.append(f"dimensions[{i}].ok must be bool")
    return errors


def dimension_ok(report: dict[str, Any], dimension_id: str) -> bool | None:
    """某维度 ok；无该维度返回 None。"""
    dims = report.get("dimensions")
    if not isinstance(dims, list):
        return None
    for d in dims:
        if isinstance(d, dict) and d.get("id") == dimension_id:
            return bool(d.get("ok"))
    return None
