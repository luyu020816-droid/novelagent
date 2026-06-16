#!/usr/bin/env python3
"""离线抽检：疲劳扫描 + AC 敏感词（不调用 LLM）。

用法（在 writer-python 目录）:
  python scripts/eval_chapter_qa.py --text path/to/chapter.txt
  python scripts/eval_chapter_qa.py --dir ../exports/chapters
"""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

_ROOT = Path(__file__).resolve().parents[1]
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

from app.services.fatigue_scanner import scan_fatigue  # noqa: E402
from app.services.sensitive_scanner import scan_sensitive  # noqa: E402


def audit_text(text: str, *, label: str) -> dict:
    fatigue = scan_fatigue(text)
    sensitive = scan_sensitive(text, profile_id="default")
    fatigue_banned = int(fatigue.get("bannedCount") or 0)
    sensitive_block = int(sensitive.get("blockCount") or 0)
    sensitive_review = int(sensitive.get("reviewCount") or 0)
    return {
        "label": label,
        "chars": len(text),
        "fatigue_banned": fatigue_banned,
        "fatigue_pass": fatigue_banned == 0,
        "sensitive_block": sensitive_block,
        "sensitive_review": sensitive_review,
        "sensitive_pass": sensitive.get("disposition") == "pass" and sensitive_block == 0,
    }


def main() -> int:
    ap = argparse.ArgumentParser(description="Chapter QA offline eval (fatigue + sensitive)")
    ap.add_argument("--text", type=Path, help="Single chapter text file")
    ap.add_argument("--dir", type=Path, help="Directory of .txt/.md chapters")
    args = ap.parse_args()

    paths: list[Path] = []
    if args.text:
        paths.append(args.text)
    if args.dir:
        paths.extend(sorted(args.dir.glob("**/*.txt")))
        paths.extend(sorted(args.dir.glob("**/*.md")))
    if not paths:
        ap.error("Provide --text or --dir")

    failed = 0
    for p in paths:
        body = p.read_text(encoding="utf-8", errors="replace")
        row = audit_text(body, label=str(p))
        ok = row["fatigue_pass"] and row["sensitive_pass"]
        status = "PASS" if ok else "FAIL"
        print(
            f"{status}\t{row['label']}\tchars={row['chars']}\t"
            f"fatigue_banned={row['fatigue_banned']}\t"
            f"sensitive_block={row['sensitive_block']} review={row['sensitive_review']}"
        )
        if not ok:
            failed += 1

    print(f"\nTotal {len(paths)}, failed {failed}")
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
