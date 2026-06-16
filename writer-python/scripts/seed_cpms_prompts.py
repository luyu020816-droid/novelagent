#!/usr/bin/env python3
"""将 cpms_manifest 提示词写入 PG cpms_prompt_versions（需已执行 Flyway V21）。"""

from __future__ import annotations

import sys
from pathlib import Path

_ROOT = Path(__file__).resolve().parents[1]
if str(_ROOT) not in sys.path:
    sys.path.insert(0, str(_ROOT))

from app.services.prompt_cpms import seed_manifest_to_pg  # noqa: E402


def main() -> int:
    n = seed_manifest_to_pg(activate=True)
    print(f"seeded {n} node prompts (active)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
