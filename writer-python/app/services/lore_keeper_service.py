"""定稿（accept）后：从正文抽取 Lore 并写入 Neo4j（Lore Keeper）。"""

from __future__ import annotations

import json
import logging
from typing import Any

from app.services.llm_gateway import LLMGateway
from app.services.neo4j_lore_store import upsert_lore_bundle

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
    from app.config import get_settings
    from app.services.neo4j_lore_store import get_driver

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
