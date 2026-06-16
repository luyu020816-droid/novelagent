"""DAG 网关：规则审阅结果并入 Critic 门禁。"""

from __future__ import annotations

from typing import Any


def apply_rule_review_gates(state: dict[str, Any]) -> dict[str, Any]:
    """将 review_* / consistency 规则结果写入 critic_report，不通过则 pass=false。"""
    report = state.get("critic_report")
    if not isinstance(report, dict):
        report = {"pass": True, "dimensions": [], "notes": []}

    checks: list[tuple[str, str, str]] = [
        ("_review_timeline", "时间线审查", "timeline_review"),
        ("_review_storyline", "故事线审查", "storyline_review"),
        ("_review_consistency", "设定一致性", "canon_facts"),
        ("_val_foreshadow", "伏笔推进", "pacing_hooks"),
    ]
    failures: list[str] = []
    dims = report.get("dimensions")
    if not isinstance(dims, list):
        dims = []
        report["dimensions"] = dims

    for key, label, dim_id in checks:
        block = state.get(key)
        if not isinstance(block, dict):
            continue
        ok = block.get("ok")
        if ok is False:
            failures.append(label)
            found = False
            for d in dims:
                if isinstance(d, dict) and d.get("id") == dim_id:
                    d["ok"] = False
                    prev = str(d.get("note") or "")
                    d["note"] = (label + "未通过；" + prev)[:500]
                    found = True
                    break
            if not found:
                dims.append({"id": dim_id, "ok": False, "note": f"{label}规则层未通过"})

    if failures:
        report["pass"] = False
        notes = report.setdefault("notes", [])
        if isinstance(notes, list):
            notes.append("[规则审阅] " + "、".join(failures) + " 未通过")

    return {"critic_report": report}
