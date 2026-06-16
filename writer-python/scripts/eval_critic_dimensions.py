#!/usr/bin/env python3
"""离线 golden 评测集：批量统计 Critic dimensions 通过率（参考 PlotPilot eval）。

用法（writer-python 目录）:
  python scripts/eval_critic_dimensions.py --dir fixtures/eval/golden
  python scripts/eval_critic_dimensions.py --artifact path/to/chapter_artifact.json
"""

from __future__ import annotations

import argparse
import json
import sys
from collections import defaultdict
from pathlib import Path

_ROOT = Path(__file__).resolve().parents[1]
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

from app.services.critic_schema import CRITIC_DIMENSION_IDS, validate_critic_report  # noqa: E402


def _load_critic(data: dict) -> dict | None:
    c = data.get("criticReport") or data.get("critic_report")
    return c if isinstance(c, dict) else None


def _scan_artifact(path: Path) -> dict:
    data = json.loads(path.read_text(encoding="utf-8"))
    critic = _load_critic(data)
    row: dict = {"label": str(path), "has_critic": critic is not None}
    if not critic:
        row["schema_ok"] = False
        return row
    errs = validate_critic_report(critic)
    row["schema_ok"] = len(errs) == 0
    row["schema_errors"] = errs
    row["pass"] = bool(critic.get("pass"))
    dims = critic.get("dimensions") or []
    dim_map: dict[str, bool] = {}
    if isinstance(dims, list):
        for d in dims:
            if isinstance(d, dict) and isinstance(d.get("id"), str):
                dim_map[d["id"]] = bool(d.get("ok"))
    row["dimensions"] = dim_map
    return row


def aggregate(rows: list[dict]) -> dict[str, dict]:
    stats: dict[str, dict] = {}
    for did in sorted(CRITIC_DIMENSION_IDS):
        stats[did] = {"seen": 0, "ok": 0, "fail": 0, "missing": 0}
    for r in rows:
        if not r.get("has_critic"):
            continue
        dm = r.get("dimensions") or {}
        for did in CRITIC_DIMENSION_IDS:
            stats[did]["seen"] += 1
            if did not in dm:
                stats[did]["missing"] += 1
            elif dm[did]:
                stats[did]["ok"] += 1
            else:
                stats[did]["fail"] += 1
    return stats


def main() -> int:
    ap = argparse.ArgumentParser(description="Critic dimension pass-rate over golden artifacts")
    ap.add_argument("--dir", type=Path, help="Directory of *.json artifacts")
    ap.add_argument("--artifact", type=Path, help="Single artifact JSON")
    ap.add_argument(
        "--schema-only",
        action="store_true",
        help="CI 模式：仅要求 JSON 契约合法，不要求 critic pass=true",
    )
    ap.add_argument(
        "--min-dimension",
        action="append",
        default=[],
        metavar="ID:RATE",
        help="维度最低通过率，如 beat_coverage:0.5",
    )
    args = ap.parse_args()

    paths: list[Path] = []
    if args.artifact:
        paths.append(args.artifact)
    if args.dir:
        paths.extend(sorted(args.dir.glob("**/*.json")))
    if not paths:
        ap.error("Provide --dir or --artifact")

    rows = [_scan_artifact(p) for p in paths]
    n = len(rows)
    pass_n = sum(1 for r in rows if r.get("pass"))
    schema_n = sum(1 for r in rows if r.get("schema_ok"))

    print(f"artifacts={n} critic_pass={pass_n}/{n} schema_ok={schema_n}/{n}")
    print("")
    print("dimension_id          pass_rate   ok/fail/missing(seen)")
    stats = aggregate(rows)
    for did, s in stats.items():
        seen = s["seen"]
        if seen == 0:
            continue
        rate = s["ok"] / seen if seen else 0.0
        print(
            f"{did:22}  {rate:6.1%}     {s['ok']}/{s['fail']}/{s['missing']}({seen})"
        )

    failed_dims_any = []
    for r in rows:
        dm = r.get("dimensions") or {}
        for did, ok in dm.items():
            if ok is False:
                failed_dims_any.append((r["label"], did))
    if failed_dims_any:
        print("")
        print("failed_samples:")
        for label, did in failed_dims_any[:20]:
            print(f"  {did} @ {label}")

    if args.schema_only:
        code = 0 if schema_n == n else 1
    else:
        code = 0 if schema_n == n and pass_n == n else 1

    for spec in args.min_dimension:
        if ":" not in spec:
            print(f"invalid --min-dimension: {spec}", file=sys.stderr)
            return 1
        did, rate_s = spec.split(":", 1)
        try:
            min_rate = float(rate_s)
        except ValueError:
            print(f"invalid rate in --min-dimension: {spec}", file=sys.stderr)
            return 1
        seen = stats.get(did, {}).get("seen", 0)
        if seen == 0:
            print(f"min-dimension skip {did}: no samples")
            continue
        rate = stats[did]["ok"] / seen
        if rate < min_rate:
            print(f"FAIL min-dimension {did}: {rate:.1%} < {min_rate:.1%}", file=sys.stderr)
            code = 1
        else:
            print(f"OK min-dimension {did}: {rate:.1%} >= {min_rate:.1%}")

    return code


if __name__ == "__main__":
    raise SystemExit(main())
