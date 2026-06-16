"""定稿（accept）后：从正文抽取 Lore 并写入 Neo4j（Lore Keeper）。"""

from __future__ import annotations

import json
import logging
from typing import Any

from app.config import get_settings
from app.services.llm_gateway import LLMGateway
from app.services.neo4j_lore_store import (
    get_driver,
    list_unresolved_foreshadow_planted_before_chapter,
    mark_foreshadows_resolved,
    upsert_lore_bundle,
)

logger = logging.getLogger(__name__)

_LORE_SYS = """你是长篇小说世界观图谱编辑。从给定章节正文抽取结构化信息，输出严格 JSON（不要 markdown）：
{
  "characters": [{"name":"姓名","role_hint":"主角/配角等可选","evidence":"正文短摘录≤120字"}],
  "events": [{"summary":"一句事件事实","participants":["涉及人物名"],"evidence":"正文短摘录"}],
  "relationships": [{"from":"人物A","to":"人物B","type":"TRUSTS","evidence":"正文短摘录"}],
  "open_foreshadowing": [{"text":"未回收悬念/伏笔描述","evidence":"正文短摘录"}]
}
约束：
- type 必须是以下之一：TRUSTS, ENEMY_OF, ALLIED_WITH, FAMILY_OF, RIVAL_OF, LOVES, KNOWS, OWES, OTHER
- 仅抽取正文明确支持的信息；不要臆造
- evidence 必须摘自正文
"""


def extract_lore_struct(
    gateway: LLMGateway,
    *,
    project_id: str,
    chapter_no: int,
    chapter_text: str,
) -> dict[str, Any]:
    text = (chapter_text or "").strip()
    if not text:
        raise ValueError("chapter_text is empty")
    user = f"project={project_id} chapter_no={chapter_no}\n\n章节正文：\n" + text[:56000]
    res = gateway.chat_completion(
        messages=[{"role": "system", "content": _LORE_SYS}, {"role": "user", "content": user}],
        response_format_json=True,
        temperature=0.15,
        agent_name="chapter_gen",
        node_name="lore_keeper",
        project_id=project_id,
        chapter_no=chapter_no,
        on_delta=None,
    )
    obj = json.loads(res.text)
    if not isinstance(obj, dict):
        raise ValueError("lore response not object")
    return obj


def ingest_chapter_lore(
    gateway: LLMGateway,
    *,
    project_id: str,
    chapter_no: int,
    chapter_text: str,
) -> None:
    if not get_settings().lore_graph_enabled or not get_settings().neo4j_enabled:
        return
    if get_driver() is None:
        logger.info("[LoreKeeper] skip ingest (Neo4j unreachable or disabled)")
        return
    try:
        raw = extract_lore_struct(
            gateway,
            project_id=project_id,
            chapter_no=chapter_no,
            chapter_text=chapter_text,
        )
    except Exception as e:
        logger.warning("[LoreKeeper] extract failed project=%s ch=%s: %s", project_id, chapter_no, e)
        return

    def _list(key: str) -> list[dict[str, Any]]:
        v = raw.get(key)
        if not isinstance(v, list):
            return []
        out: list[dict[str, Any]] = []
        for x in v:
            if isinstance(x, dict):
                out.append(x)
        return out

    try:
        upsert_lore_bundle(
            project_id=project_id,
            chapter_no=chapter_no,
            characters=_list("characters"),
            events=_list("events"),
            relationships=_list("relationships"),
            open_foreshadowing=_list("open_foreshadowing"),
        )
    except Exception as e:
        logger.warning("[LoreKeeper] neo4j upsert failed project=%s ch=%s: %s", project_id, chapter_no, e)


_RESOLVE_SYS = """你是长篇小说伏笔编辑。给定「本章定稿正文」与「在此前章节埋设、仍未图谱标记回收的伏笔」候选（每条含 fs_key 与 text）。
判定哪些伏笔在本章已被明确揭晓、兑现或收尾（不是仅加深悬念）。

输出严格 JSON（不要 markdown）：
{"resolved_fs_keys":["…"],"rationale_short":"一句话"}

规则：
- resolved_fs_keys 中的每个字符串必须**完全等于**候选列表中的某个 fs_key；不得编造 id。
- 若某条与「本章结束后仍应保持开放」的摘要条目语义一致，不要放入 resolved_fs_keys。
- 若候选为空，返回 {"resolved_fs_keys":[],"rationale_short":"no candidates"}。
"""


def run_foreshadow_resolve_pass(
    gateway: LLMGateway,
    *,
    project_id: str,
    chapter_no: int,
    chapter_text: str,
    pending_still_open: list[Any] | None = None,
) -> dict[str, Any]:
    """定稿 ingest 之后：将在此前章节埋设且本章已回收的伏笔标记为 resolved（幂等可重跑）。"""
    if not get_settings().lore_graph_enabled or not get_settings().neo4j_enabled:
        return {"skipped": True, "reason": "lore or neo4j disabled"}
    if get_driver() is None:
        return {"skipped": True, "reason": "neo4j unreachable"}

    candidates = list_unresolved_foreshadow_planted_before_chapter(
        project_id=project_id, before_chapter_no=chapter_no, limit=40
    )
    if not candidates:
        return {"resolved_count": 0, "resolved_fs_keys": [], "candidates": 0}

    allowed = {str(c.get("fs_key") or "") for c in candidates if c.get("fs_key")}
    allowed.discard("")

    hints = pending_still_open if isinstance(pending_still_open, list) else []
    hint_lines = [str(x).strip() for x in hints if str(x).strip()][:24]

    body = {
        "project_id": project_id,
        "chapter_no": chapter_no,
        "candidates": candidates,
        "still_open_hints_after_chapter": hint_lines,
    }
    user = (
        json.dumps(body, ensure_ascii=False)
        + "\n\n章节正文（截断）：\n"
        + (chapter_text or "").strip()[:50000]
    )
    try:
        res = gateway.chat_completion(
            messages=[{"role": "system", "content": _RESOLVE_SYS}, {"role": "user", "content": user}],
            response_format_json=True,
            temperature=0.1,
            agent_name="chapter_gen",
            node_name="foreshadow_resolve",
            project_id=project_id,
            chapter_no=chapter_no,
            on_delta=None,
        )
        obj = json.loads(res.text)
    except Exception as e:
        logger.warning("[ForeshadowResolve] LLM failed project=%s ch=%s: %s", project_id, chapter_no, e)
        return {"resolved_count": 0, "error": str(e)}

    raw_keys = obj.get("resolved_fs_keys") if isinstance(obj, dict) else None
    if not isinstance(raw_keys, list):
        return {"resolved_count": 0, "resolved_fs_keys": [], "parse": "bad list"}

    resolved_fs_keys = [k for k in raw_keys if isinstance(k, str) and k.strip() in allowed]
    updated = mark_foreshadows_resolved(
        project_id=project_id,
        resolved_in_chapter_no=chapter_no,
        fs_keys=resolved_fs_keys,
    )
    return {
        "resolved_count": updated,
        "resolved_fs_keys": resolved_fs_keys,
        "candidates": len(candidates),
    }
