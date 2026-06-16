"""场景节拍规范化（Planner / Ghostwriter 共用）。"""

from __future__ import annotations

from typing import Any

from app.services.memory_engine_constants import MAX_SCENE_PLAN_BEATS, MIN_SCENE_PLAN_BEATS


def normalize_beats(plan: dict[str, Any]) -> list[dict[str, str]]:
    raw = plan.get("beats")
    if not isinstance(raw, list):
        return []
    out: list[dict[str, str]] = []
    for b in raw:
        if isinstance(b, dict):
            beat = str(b.get("beat") or b.get("title") or "").strip()
            goal = str(b.get("goal") or b.get("purpose") or "").strip()
            if beat or goal:
                out.append({"beat": beat or goal, "goal": goal or beat})
        elif isinstance(b, str) and b.strip():
            out.append({"beat": b.strip(), "goal": b.strip()})
    return out


def pad_beats_from_must_cover(
    beats: list[dict[str, str]],
    chapter_contract: dict[str, Any],
    *,
    min_beats: int = MIN_SCENE_PLAN_BEATS,
    max_beats: int = MAX_SCENE_PLAN_BEATS,
) -> list[dict[str, str]]:
    """不足 min_beats 时用 must_cover 条目补齐（规则层，非 LLM）。"""
    out = list(beats)
    mc = chapter_contract.get("mustCover") or chapter_contract.get("must_cover")
    if isinstance(mc, list):
        for item in mc:
            if len(out) >= min_beats:
                break
            if not isinstance(item, str) or not item.strip():
                continue
            t = item.strip()[:200]
            if any(t in (b.get("beat") or "") for b in out):
                continue
            out.append({"beat": t, "goal": f"落实章纲：{t}"})
    while len(out) < min_beats:
        n = len(out) + 1
        out.append({"beat": f"节拍{n}", "goal": "承接前文并推进本章目标"})
    return out[:max_beats]


def enforce_scene_plan_beats(
    scene_plan: dict[str, Any],
    chapter_contract: dict[str, Any] | None,
) -> dict[str, Any]:
    cc = chapter_contract if isinstance(chapter_contract, dict) else {}
    beats = pad_beats_from_must_cover(normalize_beats(scene_plan), cc)
    scene_plan = dict(scene_plan)
    scene_plan["beats"] = beats
    return scene_plan
