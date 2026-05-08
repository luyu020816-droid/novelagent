from __future__ import annotations

import json
from pathlib import Path

_WRITER_ROOT = Path(__file__).resolve().parents[2]
_DATA = _WRITER_ROOT / "data"


def load_genre_context_bundle() -> str:
    """拼接 trope_cards + platform_profiles + genre_rules 供 prompt 使用。"""
    chunks: list[str] = []

    trope_dir = _DATA / "trope_cards"
    for name in ("urban_tech_system.json", "fantasy_leveling.json", "romance_rebirth.json"):
        p = trope_dir / name
        chunks.append(f"=== trope_cards/{name} ===\n{p.read_text(encoding='utf-8')}")

    plat_dir = _DATA / "platform_profiles"
    for name in ("fanqie.yaml", "qidian.yaml"):
        p = plat_dir / name
        chunks.append(f"=== platform_profiles/{name} ===\n{p.read_text(encoding='utf-8')}")

    rules = _DATA / "genre_rules" / "default.yaml"
    chunks.append(f"=== genre_rules/default.yaml ===\n{rules.read_text(encoding='utf-8')}")

    return "\n\n".join(chunks)


def load_platform_snippet(platform_name: str) -> str:
    """按用户 target_platform 粗略匹配平台 YAML。"""
    key = platform_name.strip().lower()
    if "番茄" in platform_name or "fanqie" in key:
        return (_DATA / "platform_profiles" / "fanqie.yaml").read_text(encoding="utf-8")
    if "起点" in platform_name or "qidian" in key:
        return (_DATA / "platform_profiles" / "qidian.yaml").read_text(encoding="utf-8")
    return json.dumps(
        {"note": "未精确匹配平台文件，默认参考番茄与起点两份 profile。", "fanqie": "see bundle"},
        ensure_ascii=False,
    )
