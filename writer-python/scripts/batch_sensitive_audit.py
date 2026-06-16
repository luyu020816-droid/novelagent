#!/usr/bin/env python3
"""
大批量语料敏感词批审：Pandas 分块读取，控制内存；每块调用 AC 扫描。

示例:
  python scripts/batch_sensitive_audit.py --input chapters.jsonl --text-column body --chunksize 2000
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

# 允许从 writer-python 根目录运行
_ROOT = Path(__file__).resolve().parents[1]
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

from app.services.sensitive_scanner import scan_sensitive  # noqa: E402


def _audit_jsonl(path: Path, text_column: str, chunksize: int, profile: str) -> None:
    import pandas as pd

    total = 0
    blocked = 0
    review = 0
    for chunk in pd.read_json(path, lines=True, chunksize=chunksize):
        if text_column not in chunk.columns:
            raise SystemExit(f"列不存在: {text_column}")
        for text in chunk[text_column].fillna("").astype(str):
            total += 1
            rep = scan_sensitive(text, profile)
            if rep.get("disposition") == "block":
                blocked += 1
            elif rep.get("disposition") == "review":
                review += 1
    print(json.dumps({"rows": total, "blocked": blocked, "review": review}, ensure_ascii=False))


def main() -> None:
    p = argparse.ArgumentParser(description="Pandas 分块敏感词批审")
    p.add_argument("--input", required=True, type=Path)
    p.add_argument("--text-column", default="body")
    p.add_argument("--chunksize", type=int, default=5000)
    p.add_argument("--profile", default="default")
    args = p.parse_args()
    _audit_jsonl(args.input, args.text_column, args.chunksize, args.profile)


if __name__ == "__main__":
    main()
