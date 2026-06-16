"""敏感词 / 违规表达扫描：Aho-Corasick 自动机（pyahocorasick），O(N) 多模式匹配。"""

from __future__ import annotations

import logging
import os
from dataclasses import dataclass
from functools import lru_cache
from pathlib import Path
from typing import Any, Literal

import yaml

logger = logging.getLogger(__name__)

Severity = Literal["block", "review", "log"]

_BUILTIN_ROOT = Path(__file__).resolve().parent.parent / "skills" / "library" / "sensitive_lexicon"

_SEVERITY_RANK = {"log": 0, "review": 1, "block": 2}


@dataclass(frozen=True)
class _LexiconEntry:
    phrase: str
    category: str
    severity: Severity


def _lexicon_root() -> Path:
    env = (os.environ.get("SENSITIVE_LEXICON_ROOT") or "").strip()
    if env:
        return Path(env)
    return _BUILTIN_ROOT


def _load_manifest(profile_dir: Path) -> dict[str, Any]:
    path = profile_dir / "manifest.yaml"
    if not path.is_file():
        return {}
    return yaml.safe_load(path.read_text(encoding="utf-8")) or {}


def _read_word_lines(path: Path) -> list[str]:
    if not path.is_file():
        return []
    out: list[str] = []
    for line in path.read_text(encoding="utf-8").splitlines():
        s = line.strip()
        if not s or s.startswith("#"):
            continue
        out.append(s)
    return out


def _load_entries(profile_id: str) -> list[_LexiconEntry]:
    root = _lexicon_root()
    profile_dir = root / profile_id
    if not profile_dir.is_dir():
        profile_dir = root / "default"
    manifest = _load_manifest(profile_dir)
    entries: list[_LexiconEntry] = []
    categories = manifest.get("categories") or {}
    if isinstance(categories, dict):
        for cat, spec in categories.items():
            if not isinstance(spec, dict):
                continue
            sev = str(spec.get("severity") or "block").strip().lower()
            if sev not in ("block", "review", "log"):
                sev = "block"
            fname = spec.get("file")
            if not isinstance(fname, str) or not fname.strip():
                continue
            for word in _read_word_lines(profile_dir / fname.strip()):
                entries.append(_LexiconEntry(phrase=word, category=str(cat), severity=sev))  # type: ignore[arg-type]
    return entries


@lru_cache(maxsize=8)
def _build_automaton(profile_id: str) -> tuple[Any, dict[str, Any]]:
    """编译 AC 自动机；pyahocorasick 使用 C 扩展紧凑 Trie + 失败链接。"""
    try:
        import ahocorasick  # pyahocorasick
    except ImportError as e:
        raise ImportError(
            "pyahocorasick is required for sensitive_scanner; pip install pyahocorasick"
        ) from e

    entries = _load_entries(profile_id)
    merged: dict[str, _LexiconEntry] = {}
    for ent in entries:
        cur = merged.get(ent.phrase)
        if cur is None or _SEVERITY_RANK[ent.severity] > _SEVERITY_RANK[cur.severity]:
            merged[ent.phrase] = ent
    A = ahocorasick.Automaton()
    for ent in merged.values():
        A.add_word(ent.phrase, (ent.category, ent.severity, ent.phrase))
    if len(A) == 0:
        A.add_word("__empty_lexicon_placeholder__", ("_", "log", "__empty__"))
    A.make_automaton()

    manifest = _load_manifest(_lexicon_root() / profile_id if (_lexicon_root() / profile_id).is_dir() else _lexicon_root() / "default")
    meta = {
        "profileId": manifest.get("profile_id") or profile_id,
        "profileVersion": manifest.get("version"),
        "entryCount": len(entries),
        "engine": "pyahocorasick",
        "algorithm": "aho_corasick",
    }
    return A, meta


def scan_sensitive(text: str, profile_id: str | None = None) -> dict[str, Any]:
    """
    对 text 做 AC 扫描，返回结构化报告供 Critic / 批审脚本使用。
    """
    pid = (profile_id or os.environ.get("SENSITIVE_LEXICON_PROFILE") or "default").strip() or "default"
    t = text or ""
    if not t.strip():
        return {
            **{"profileId": pid, "engine": "pyahocorasick", "algorithm": "aho_corasick"},
            "hitCount": 0,
            "blockCount": 0,
            "reviewCount": 0,
            "logCount": 0,
            "hits": [],
            "maxSeverity": "none",
            "disposition": "pass",
        }

    try:
        automaton, meta = _build_automaton(pid)
    except ImportError:
        logger.warning("pyahocorasick not installed; sensitive_scan skipped")
        return {
            "profileId": pid,
            "engine": "disabled",
            "algorithm": "none",
            "hitCount": 0,
            "blockCount": 0,
            "reviewCount": 0,
            "logCount": 0,
            "hits": [],
            "maxSeverity": "none",
            "disposition": "pass",
            "warning": "pyahocorasick not installed",
        }

    hits: list[dict[str, Any]] = []
    seen: set[tuple[str, int]] = set()
    block_count = 0
    review_count = 0
    log_count = 0
    max_sev: Severity | None = None

    for end_index, (category, severity, phrase) in automaton.iter(t):
        if phrase == "__empty__":
            continue
        key = (phrase, end_index)
        if key in seen:
            continue
        seen.add(key)
        start_index = end_index - len(phrase) + 1
        hits.append(
            {
                "phrase": phrase,
                "category": category,
                "severity": severity,
                "startIndex": start_index,
                "endIndex": end_index + 1,
            }
        )
        if severity == "block":
            block_count += 1
        elif severity == "review":
            review_count += 1
        else:
            log_count += 1
        if max_sev is None or _SEVERITY_RANK[severity] > _SEVERITY_RANK[max_sev]:
            max_sev = severity

    disposition: str = "pass"
    if block_count > 0:
        disposition = "block"
    elif review_count > 0:
        disposition = "review"

    return {
        **meta,
        "profileId": meta.get("profileId") or pid,
        "hitCount": len(hits),
        "blockCount": block_count,
        "reviewCount": review_count,
        "logCount": log_count,
        "hits": hits[:200],
        "maxSeverity": max_sev or "none",
        "disposition": disposition,
    }


def should_fail_critic(sensitive_report: dict[str, Any]) -> bool:
    """block 级命中则 Critic 不通过。"""
    return sensitive_report.get("disposition") == "block" and int(sensitive_report.get("blockCount") or 0) > 0
