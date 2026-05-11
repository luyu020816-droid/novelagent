"""Decision Gate：根据 critic_report.pass 设置 accepted / rejected（Day7 不回跳 Ghostwriter）。"""

from __future__ import annotations

from typing import Any


def decision_gate_node(state: dict[str, Any]) -> dict[str, Any]:
    report = state.get("critic_report") or {}
    ok = bool(report.get("pass"))
    return {"accepted": ok, "rejected": not ok}
