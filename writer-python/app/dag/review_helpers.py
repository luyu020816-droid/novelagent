"""审阅/校验节点共用的规则层逻辑。"""

from __future__ import annotations

import re
from typing import Any


def review_timeline(state: dict[str, Any]) -> dict[str, Any]:
    """检查章节号与历史摘要顺序、近期事件章节是否超前。"""
    ch_no = int(state.get("chapter_no") or 0)
    hist = state.get("history_summaries") or state.get("recent_summaries") or []
    issues: list[str] = []
    max_prior = 0
    if isinstance(hist, list):
        for item in hist:
            if not isinstance(item, dict):
                continue
            pn = item.get("chapterNo", item.get("chapter_no"))
            try:
                n = int(pn)
            except (TypeError, ValueError):
                continue
            if n >= ch_no:
                issues.append(f"历史摘要含未来章 chapterNo={n}（当前 {ch_no}）")
            max_prior = max(max_prior, n)
    pack = state.get("context_pack") if isinstance(state.get("context_pack"), dict) else {}
    ue = pack.get("unresolved_events") if isinstance(pack.get("unresolved_events"), dict) else {}
    for key in ("recent_events", "recentEvents"):
        events = ue.get(key)
        if not isinstance(events, list):
            continue
        for ev in events:
            if not isinstance(ev, dict):
                continue
            en = ev.get("chapterNo", ev.get("chapter_no"))
            try:
                en_i = int(en)
            except (TypeError, ValueError):
                continue
            if en_i > ch_no:
                issues.append(f"近期事件章节 {en_i} 超前于当前章 {ch_no}")
    text = str(state.get("chapter_text") or "")
    if max_prior > 0 and ch_no > max_prior + 1:
        issues.append(f"当前章 {ch_no} 与已定稿最大章 {max_prior} 不连续")
    if re.search(r"十[年载]|百年|千年", text) and ch_no <= 3:
        issues.append("早期章节出现大跨度时间跳跃表述，请核对时间线")
    ok = len(issues) == 0
    return {"ok": ok, "issues": issues}


def review_storyline(state: dict[str, Any]) -> dict[str, Any]:
    """检查活跃故事线/任务单关键词是否在正文出现。"""
    text = str(state.get("chapter_text") or "")
    co = state.get("chapter_obligations") or state.get("chapterObligations")
    missing: list[str] = []
    if isinstance(co, dict):
        lines = co.get("narrativePromptLines") or co.get("narrative_prompt_lines") or []
        if isinstance(lines, list):
            for line in lines[:8]:
                s = str(line).strip()
                if len(s) < 4:
                    continue
                token = s[:8]
                if token not in text:
                    missing.append(token)
        active = co.get("activeStorylines") or co.get("active_storylines") or []
        if isinstance(active, list):
            for sl in active[:6]:
                if not isinstance(sl, dict):
                    continue
                title = str(sl.get("title") or sl.get("name") or "").strip()
                if len(title) >= 2 and title not in text:
                    missing.append(title)
    ok = len(missing) == 0
    return {"ok": ok, "missing_hints": missing}


def review_foreshadow(state: dict[str, Any]) -> dict[str, Any]:
    """开放伏笔是否在本章有推进（关键词命中）。"""
    text = str(state.get("chapter_text") or "")
    pack = state.get("context_pack") if isinstance(state.get("context_pack"), dict) else {}
    ue = pack.get("unresolved_events") if isinstance(pack.get("unresolved_events"), dict) else {}
    opens = ue.get("open_foreshadowing") or ue.get("openForeshadowing") or []
    if not isinstance(opens, list) or not opens:
        return {"ok": True, "checked": 0, "touched": 0}
    touched = 0
    for fs in opens[:12]:
        if not isinstance(fs, dict):
            continue
        hint = str(fs.get("text") or fs.get("description") or fs.get("fs_key") or "").strip()
        if len(hint) < 2:
            continue
        frag = hint[:6]
        if frag in text:
            touched += 1
    checked = min(len(opens), 12)
    ok = touched > 0 or checked == 0
    return {"ok": ok, "checked": checked, "touched": touched}


def ext_summary_from_history(state: dict[str, Any]) -> dict[str, Any]:
    """从已定稿摘要拼一行 ext 摘要（aftermath 前预览）。"""
    hist = state.get("history_summaries") or []
    ch_no = int(state.get("chapter_no") or 0)
    lines: list[str] = []
    if isinstance(hist, list):
        for item in hist[-3:]:
            if not isinstance(item, dict):
                continue
            sm = item.get("summary")
            if isinstance(sm, dict):
                one = sm.get("oneLiner") or sm.get("one_liner") or sm.get("summaryLine")
                if isinstance(one, str) and one.strip():
                    lines.append(one.strip()[:200])
            elif isinstance(sm, str) and sm.strip():
                lines.append(sm.strip()[:200])
    body = state.get("chapter_text") or ""
    preview = (lines[-1] if lines else "") or str(body)[:120]
    return {"chapter_no": ch_no, "preview_line": preview, "prior_lines": lines}
