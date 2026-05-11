"""从 app/skills/library 加载 Skill：根目录 YAML、任意深度文件夹（skill.yaml / SKILL.md）。"""

from __future__ import annotations

import logging
import re
from pathlib import Path
from typing import Any, TypedDict

import yaml

logger = logging.getLogger(__name__)

SKILL_LIBRARY_DIR = Path(__file__).resolve().parent / "library"
_ID_RE = re.compile(r"^[a-z0-9][a-z0-9_-]{0,62}$")


class SeriesPreset(TypedDict):
    """单套 Skill（与合并逻辑字段一致）。"""

    label: str
    init_world_rules: list[str]
    init_ability_rules: list[str]
    init_forbidden_moves: list[str]
    init_must_retain_facts: list[str]
    init_style_voice_suffix: str
    init_taboo_topics: list[str]
    chapter_digest: str


def _as_str_list(v: Any) -> list[str]:
    if v is None:
        return []
    if not isinstance(v, list):
        return []
    out: list[str] = []
    for x in v:
        if isinstance(x, str) and x.strip():
            out.append(x.strip())
    return out


def _folder_slug(folder: Path) -> str | None:
    s = folder.name.strip().lower().replace(" ", "_")
    if not _ID_RE.match(s):
        return None
    return s


def _normalize_doc(
    doc: dict[str, Any],
    path: Path,
    *,
    default_id: str | None = None,
) -> tuple[str, SeriesPreset]:
    raw_id = doc.get("id")
    if raw_id is None or str(raw_id).strip() == "":
        sid = (default_id or path.stem).strip().lower().replace(" ", "_")
    else:
        sid = str(raw_id).strip().lower()
    if not _ID_RE.match(sid):
        raise ValueError(f"Skill id 非法（须匹配 {_ID_RE.pattern}）: {sid!r} @ {path}")

    label = str(doc.get("label") or sid).strip() or sid
    digest = doc.get("chapter_digest")
    if digest is None or not str(digest).strip():
        raise ValueError(f"chapter_digest 必填 @ {path}")

    preset: SeriesPreset = {
        "label": label,
        "init_world_rules": _as_str_list(doc.get("init_world_rules")),
        "init_ability_rules": _as_str_list(doc.get("init_ability_rules")),
        "init_forbidden_moves": _as_str_list(doc.get("init_forbidden_moves")),
        "init_must_retain_facts": _as_str_list(doc.get("init_must_retain_facts")),
        "init_style_voice_suffix": str(doc.get("init_style_voice_suffix") or "").strip(),
        "init_taboo_topics": _as_str_list(doc.get("init_taboo_topics")),
        "chapter_digest": str(digest).strip()[:8000],
    }
    return sid, preset


def _pick_skill_entry_yaml(folder: Path) -> Path | None:
    """仅认 skill.yaml / index.yaml，避免误把 agents/*.yaml 当成主 Skill。"""
    for name in ("skill.yaml", "skill.yml", "index.yaml", "index.yml"):
        p = folder / name
        if p.is_file():
            return p
    return None


def _skill_md_path(folder: Path) -> Path | None:
    for name in ("SKILL.md", "skill.md"):
        p = folder / name
        if p.is_file():
            return p
    return None


def _folder_is_skill_package(folder: Path) -> bool:
    return _pick_skill_entry_yaml(folder) is not None or _skill_md_path(folder) is not None


def _parse_skill_md(md_path: Path) -> tuple[dict[str, Any], str]:
    raw = md_path.read_text(encoding="utf-8").lstrip("\ufeff")
    if not raw.startswith("---"):
        return {}, raw.strip()
    parts = raw.split("---", 2)
    if len(parts) < 3:
        return {}, raw.strip()
    fm = yaml.safe_load(parts[1])
    body = parts[2].strip()
    return (fm if isinstance(fm, dict) else {}), body


def _preset_from_skill_md(folder: Path, md_path: Path) -> tuple[str, SeriesPreset]:
    fm, body = _parse_skill_md(md_path)
    raw_id = fm.get("id") if fm.get("id") is not None else fm.get("name")
    slug = _folder_slug(folder)
    if raw_id is not None and str(raw_id).strip():
        sid = str(raw_id).strip().lower().replace(" ", "_")
    elif slug:
        sid = slug
    else:
        raise ValueError(f"SKILL.md 需在 frontmatter 写 name/id，且文件夹名为合法 slug：{folder}")

    if not _ID_RE.match(sid):
        raise ValueError(f"id/name 非法: {sid!r} @ {md_path}")

    desc = str(fm.get("description") or "").strip()
    label = str(fm.get("display_name") or fm.get("title") or "").strip()
    if not label:
        label = sid.replace("-", " ").replace("_", " ").strip()[:80] or sid
    if len(label) > 160:
        label = label[:157] + "…"

    digest_src = desc + ("\n\n" if desc and body else "") + body
    digest = digest_src.strip()[:12000]

    preset: SeriesPreset = {
        "label": label,
        "init_world_rules": [],
        "init_ability_rules": [],
        "init_forbidden_moves": [],
        "init_must_retain_facts": [],
        "init_style_voice_suffix": "",
        "init_taboo_topics": [],
        "chapter_digest": digest[:8000],
    }
    return sid, preset


def _all_skill_folders(base: Path) -> list[Path]:
    out: list[Path] = []
    for p in base.rglob("*"):
        if not p.is_dir() or p == base or p.name.startswith("."):
            continue
        if _folder_is_skill_package(p):
            out.append(p)
    return sorted(out, key=lambda x: str(x))


def load_all_skills() -> dict[str, SeriesPreset]:
    """根目录 *.yaml + 任意深度文件夹（skill.yaml / index.yaml 或 SKILL.md）。"""
    out: dict[str, SeriesPreset] = {}
    base = SKILL_LIBRARY_DIR
    if not base.is_dir():
        logger.info("[skills] library dir missing: %s", SKILL_LIBRARY_DIR)
        return out

    seen_files: set[str] = set()

    for pattern in ("*.yaml", "*.yml"):
        for p in sorted(base.glob(pattern)):
            rp = str(p.resolve())
            if rp in seen_files:
                continue
            seen_files.add(rp)
            try:
                raw = p.read_text(encoding="utf-8")
                doc = yaml.safe_load(raw)
                if not isinstance(doc, dict):
                    logger.warning("[skills] skip %s: root must be mapping", p.name)
                    continue
                sid, preset = _normalize_doc(doc, p, default_id=None)
                if sid in out:
                    logger.warning("[skills] duplicate id %s, skip %s", sid, p.name)
                    continue
                out[sid] = preset
            except Exception as e:
                logger.warning("[skills] skip %s: %s", p.name, e)

    for folder in _all_skill_folders(base):
        yml = _pick_skill_entry_yaml(folder)
        md = _skill_md_path(folder)
        slug = _folder_slug(folder)
        try:
            if yml:
                rp = str(yml.resolve())
                if rp in seen_files:
                    continue
                seen_files.add(rp)
                if not slug:
                    logger.warning("[skills] skip folder (invalid slug): %s", folder)
                    continue
                raw = yml.read_text(encoding="utf-8")
                doc = yaml.safe_load(raw)
                if not isinstance(doc, dict):
                    logger.warning("[skills] skip %s: root must be mapping", yml)
                    continue
                sid, preset = _normalize_doc(doc, yml, default_id=slug)
            elif md:
                rp = str(md.resolve())
                if rp in seen_files:
                    continue
                seen_files.add(rp)
                sid, preset = _preset_from_skill_md(folder, md)
            else:
                continue
            if sid in out:
                logger.warning("[skills] duplicate id %s, skip %s", sid, folder)
                continue
            out[sid] = preset
        except Exception as e:
            logger.warning("[skills] skip %s: %s", folder, e)

    return out


def list_skill_summaries() -> list[dict[str, str]]:
    return [{"id": k, "label": v["label"]} for k, v in sorted(load_all_skills().items(), key=lambda t: t[0])]


def get_series_preset(preset_id: str | None) -> SeriesPreset | None:
    if not preset_id or not str(preset_id).strip():
        return None
    key = str(preset_id).strip().lower()
    return load_all_skills().get(key)


def list_known_preset_ids() -> list[str]:
    return sorted(load_all_skills().keys())
