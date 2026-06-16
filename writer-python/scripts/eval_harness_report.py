#!/usr/bin/env python3
"""PlotPilot 式 harness 抽检：规则 QA + scene_plan beats 数量（需 chapter JSON 工件）。

  python scripts/eval_harness_report.py --artifact path/to/chapter_artifact.json
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

_ROOT = Path(__file__).resolve().parents[1]
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

from app.services.critic_schema import validate_critic_report  # noqa: E402
from app.services.fatigue_scanner import scan_fatigue  # noqa: E402
from app.services.sensitive_scanner import scan_sensitive  # noqa: E402


def audit_text(text: str, *, label: str) -> dict:
    fatigue = scan_fatigue(text)
    sensitive = scan_sensitive(text, profile_id="default")
    fatigue_banned = int(fatigue.get("bannedCount") or 0)
    sensitive_block = int(sensitive.get("blockCount") or 0)
    return {
        "label": label,
        "fatigue_banned": fatigue_banned,
        "fatigue_pass": fatigue_banned == 0,
        "sensitive_block": sensitive_block,
        "sensitive_pass": sensitive.get("disposition") == "pass" and sensitive_block == 0,
    }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--artifact", type=Path, required=True, help="含 chapterText / scenePlan 的 JSON")
    args = ap.parse_args()
    data = json.loads(args.artifact.read_text(encoding="utf-8"))
    text = data.get("chapterText") or data.get("chapter_text") or ""
    plan = data.get("scenePlan") or data.get("scene_plan") or {}
    beats = plan.get("beats") if isinstance(plan, dict) else []
    n_beats = len(beats) if isinstance(beats, list) else 0

    row = audit_text(text, label=str(args.artifact))
    beat_ok = n_beats >= 4
    print(f"beats={n_beats} beat_sheet_ok={beat_ok}")
    print(
        f"qa={'PASS' if row['fatigue_pass'] and row['sensitive_pass'] else 'FAIL'} "
        f"fatigue_banned={row['fatigue_banned']} sensitive_block={row['sensitive_block']}"
    )
    critic = data.get("criticReport") or data.get("critic_report")
    schema_ok = True
    if isinstance(critic, dict):
        errs = validate_critic_report(critic)
        if errs:
            schema_ok = False
            print("critic_schema_errors:", "; ".join(errs[:3]))
        dims = critic.get("dimensions") or []
        failed = [d.get("id") for d in dims if isinstance(d, dict) and d.get("ok") is False]
        if failed:
            print("critic_failed_dims:", ", ".join(str(x) for x in failed))
    return (
        0
        if beat_ok and row["fatigue_pass"] and row["sensitive_pass"] and schema_ok
        else 1
    )


if __name__ == "__main__":
    raise SystemExit(main())
