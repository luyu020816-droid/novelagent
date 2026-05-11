"""扫描正文中的疲劳词 / AI 腔短语（YAML 配置）。"""

from __future__ import annotations

from pathlib import Path
from typing import Any

import yaml

_LIBRARY_ROOT = Path(__file__).resolve().parent.parent / "skills" / "library" / "fatigue"


def load_fatigue_profile(profile_id: str | None) -> dict[str, Any]:
    pid = (profile_id or "default").strip() or "default"
    path = _LIBRARY_ROOT / f"{pid}.yaml"
    if not path.is_file():
        path = _LIBRARY_ROOT / "default.yaml"
    if not path.is_file():
        return {"banned_substrings": [], "caution_substrings": []}
    data = yaml.safe_load(path.read_text(encoding="utf-8")) or {}
    return {
        "banned_substrings": list(data.get("banned_substrings") or []),
        "caution_substrings": list(data.get("caution_substrings") or []),
        "version": data.get("version"),
        "profile_id": data.get("profile_id") or pid,
    }


def scan_fatigue(text: str, profile_id: str | None = None) -> dict[str, Any]:
    """返回命中列表与计数，供 Critic 附件与日志使用。"""
    prof = load_fatigue_profile(profile_id)
    t = text or ""
    hits_banned: list[dict[str, Any]] = []
    hits_caution: list[dict[str, Any]] = []
    lower = t.lower()

    for s in prof["banned_substrings"]:
        if not isinstance(s, str) or not s.strip():
            continue
        needle = s.strip()
        if needle.lower() in lower or needle in t:
            hits_banned.append({"phrase": needle, "severity": "banned"})

    for s in prof["caution_substrings"]:
        if not isinstance(s, str) or not s.strip():
            continue
        needle = s.strip()
        if needle.lower() in lower or needle in t:
            hits_caution.append({"phrase": needle, "severity": "caution"})

    return {
        "profileId": prof.get("profile_id"),
        "profileVersion": prof.get("version"),
        "bannedHits": hits_banned,
        "cautionHits": hits_caution,
        "bannedCount": len(hits_banned),
        "cautionCount": len(hits_caution),
    }
