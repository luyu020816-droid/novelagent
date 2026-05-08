from __future__ import annotations

from pathlib import Path

_PROMPTS_ROOT = Path(__file__).resolve().parents[2] / "prompts"


def load_prompt(relative_name: str) -> str:
    path = _PROMPTS_ROOT / relative_name
    if not path.is_file():
        raise FileNotFoundError(f"Prompt not found: {path}")
    return path.read_text(encoding="utf-8")
