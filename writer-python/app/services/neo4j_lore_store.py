"""Neo4j：世界观图谱幂等写入与 GraphRAG 召回（按 project_id 隔离）。"""

from __future__ import annotations

import hashlib
import json
import logging
import re
from datetime import datetime, timezone
from typing import Any

from neo4j import Driver, GraphDatabase, basic_auth

from app.config import get_settings

logger = logging.getLogger(__name__)

_driver: Driver | None = None


def _norm_name(name: str) -> str:
    return re.sub(r"\s+", "", (name or "").strip())


def _event_key(project_id: str, chapter_no: int, summary: str) -> str:
    raw = f"{project_id}|{chapter_no}|{summary.strip()[:400]}"
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()[:24]


def _fs_key(project_id: str, text: str) -> str:
    raw = f"{project_id}|{text.strip()[:320]}"
    return hashlib.sha256(raw.encode("utf-8")).hexdigest()[:24]


def get_driver() -> Driver | None:
    global _driver
    s = get_settings()
    if not s.neo4j_enabled:
        return None
    if _driver is not None:
        return _driver
    uri = (s.neo4j_uri or "").strip()
    if not uri:
        logger.warning("[Neo4j] NEO4J_URI empty; lore graph disabled")
        return None
    user = (s.neo4j_user or "neo4j").strip()
    password = (s.neo4j_password or "").strip()
    _driver = GraphDatabase.driver(uri, auth=basic_auth(user, password))
    try:
        _driver.verify_connectivity()
    except Exception as e:
        logger.warning("[Neo4j] verify_connectivity failed: %s", e)
        _driver.close()
        _driver = None
        return None
    _ensure_schema(_driver)
    return _driver


def _ensure_schema(driver: Driver) -> None:
    stmts = [
        "CREATE CONSTRAINT lore_char_pid_name IF NOT EXISTS "
        "FOR (c:LoreCharacter) REQUIRE (c.project_id, c.name) IS UNIQUE",
        "CREATE CONSTRAINT lore_evt_pid_key IF NOT EXISTS "
        "FOR (e:LoreEvent) REQUIRE (e.project_id, e.event_key) IS UNIQUE",
        "CREATE CONSTRAINT lore_fs_pid_key IF NOT EXISTS "
        "FOR (f:LoreForeshadow) REQUIRE (f.project_id, f.fs_key) IS UNIQUE",
        "CREATE CONSTRAINT lore_ncc_pid_ch IF NOT EXISTS "
        "FOR (n:LoreNarrativeChapterContext) REQUIRE (n.project_id, n.chapter_no) IS UNIQUE",
        "CREATE CONSTRAINT lore_sl_pid_sid IF NOT EXISTS "
        "FOR (s:LoreStoryline) REQUIRE (s.project_id, s.storyline_id) IS UNIQUE",
        "CREATE CONSTRAINT lore_cf_pid_cid IF NOT EXISTS "
        "FOR (c:LoreConfluence) REQUIRE (c.project_id, c.confluence_id) IS UNIQUE",
    ]
    with driver.session() as session:
        for q in stmts:
            try:
                session.run(q)
            except Exception as e:
                logger.debug("[Neo4j] constraint optional fail: %s — %s", q[:48], e)


def close_driver() -> None:
    global _driver
    if _driver is not None:
        _driver.close()
        _driver = None


ALLOWED_REL_KINDS = frozenset(
    {
        "TRUSTS",
        "ENEMY_OF",
        "ALLIED_WITH",
        "FAMILY_OF",
        "RIVAL_OF",
        "LOVES",
        "KNOWS",
        "OWES",
        "OTHER",
    }
)


def upsert_lore_bundle(
    *,
    project_id: str,
    chapter_no: int,
    characters: list[dict[str, Any]],
    events: list[dict[str, Any]],
    relationships: list[dict[str, Any]],
    open_foreshadowing: list[dict[str, Any]],
) -> None:
    driver = get_driver()
    if driver is None:
        return

    def work(tx) -> None:
        ch_evidence_default = f"chapter_{chapter_no}"
        for ch in characters:
            if not isinstance(ch, dict):
                continue
            nm = _norm_name(str(ch.get("name") or ""))
            if not nm:
                continue
            ev = str(ch.get("evidence") or ch_evidence_default)[:2000]
            tx.run(
                """
                MERGE (c:LoreCharacter {project_id: $pid, name: $name})
                SET c.last_chapter_no = CASE
                  WHEN coalesce(c.last_chapter_no, 0) < $ch THEN $ch ELSE coalesce(c.last_chapter_no, 0)
                END,
                c.evidence = CASE
                  WHEN coalesce(c.last_chapter_no, 0) <= $ch THEN $ev ELSE c.evidence
                END,
                c.role_hint = coalesce($role_hint, c.role_hint)
                """,
                pid=project_id,
                name=nm,
                ch=chapter_no,
                ev=ev,
                role_hint=str(ch.get("role_hint") or "")[:500] or None,
            )

        for ev in events:
            if not isinstance(ev, dict):
                continue
            summary = str(ev.get("summary") or "").strip()
            if not summary:
                continue
            ek = _event_key(project_id, chapter_no, summary)
            evidence = str(ev.get("evidence") or ch_evidence_default)[:4000]
            tx.run(
                """
                MERGE (e:LoreEvent {project_id: $pid, event_key: $ek})
                SET e.summary = $summary,
                    e.chapter_no = $ch,
                    e.evidence = $evidence
                """,
                pid=project_id,
                ek=ek,
                summary=summary[:4000],
                ch=chapter_no,
                evidence=evidence,
            )
            parts = ev.get("participants")
            if isinstance(parts, list):
                for p in parts:
                    pn = _norm_name(str(p))
                    if not pn:
                        continue
                    tx.run(
                        """
                        MERGE (c:LoreCharacter {project_id: $pid, name: $name})
                        SET c.last_chapter_no = CASE
                          WHEN coalesce(c.last_chapter_no, 0) < $ch THEN $ch ELSE coalesce(c.last_chapter_no, 0)
                        END
                        WITH c
                        MATCH (e:LoreEvent {project_id: $pid, event_key: $ek})
                        MERGE (c)-[rp:PARTICIPATED_IN {project_id: $pid, chapter_no: $ch}]->(e)
                        SET rp.evidence = coalesce($evidence, rp.evidence)
                        """,
                        pid=project_id,
                        name=pn,
                        ch=chapter_no,
                        ek=ek,
                        evidence=evidence[:2000],
                    )

        for rel in relationships:
            if not isinstance(rel, dict):
                continue
            fa = _norm_name(str(rel.get("from") or rel.get("source") or ""))
            tb = _norm_name(str(rel.get("to") or rel.get("target") or ""))
            if not fa or not tb:
                continue
            kind = str(rel.get("type") or rel.get("kind") or "OTHER").upper()
            if kind not in ALLOWED_REL_KINDS:
                kind = "OTHER"
            evidence = str(rel.get("evidence") or ch_evidence_default)[:4000]
            tx.run(
                """
                MERGE (a:LoreCharacter {project_id: $pid, name: $from_name})
                SET a.last_chapter_no = CASE
                  WHEN coalesce(a.last_chapter_no, 0) < $ch THEN $ch ELSE coalesce(a.last_chapter_no, 0)
                END
                MERGE (b:LoreCharacter {project_id: $pid, name: $to_name})
                SET b.last_chapter_no = CASE
                  WHEN coalesce(b.last_chapter_no, 0) < $ch THEN $ch ELSE coalesce(b.last_chapter_no, 0)
                END
                MERGE (a)-[r:LoreRelationship {project_id: $pid, kind: $kind}]->(b)
                WITH r, $ch AS ch, $evidence AS evid, coalesce(r.chapter_no, 0) AS oc
                SET r.chapter_no = CASE WHEN ch > oc THEN ch ELSE oc END,
                    r.evidence = CASE WHEN ch > oc THEN evid ELSE coalesce(r.evidence, evid) END
                """,
                pid=project_id,
                from_name=fa,
                to_name=tb,
                kind=kind,
                ch=chapter_no,
                evidence=evidence,
            )

        for fs in open_foreshadowing:
            if not isinstance(fs, dict):
                continue
            text = str(fs.get("text") or "").strip()
            if not text:
                continue
            fk = _fs_key(project_id, text)
            evidence = str(fs.get("evidence") or ch_evidence_default)[:4000]
            sug_raw = fs.get("suggested_resolve_chapter", fs.get("suggestedResolveChapter"))
            try:
                sug = int(sug_raw) if sug_raw is not None and str(sug_raw).strip() != "" else None
            except (TypeError, ValueError):
                sug = None
            imp = str(fs.get("importance") or "").strip() or None
            explicit_abandon = fs.get("abandoned") is True or fs.get("abandoned") == "true"
            tx.run(
                """
                MERGE (f:LoreForeshadow {project_id: $pid, fs_key: $fk})
                SET f.text = $text,
                    f.chapter_no = coalesce(f.chapter_no, $ch),
                    f.evidence = $evidence,
                    f.resolved = coalesce(f.resolved, false),
                    f.suggested_resolve_chapter = coalesce($sug, f.suggested_resolve_chapter),
                    f.importance = coalesce($imp, f.importance, 'medium'),
                    f.abandoned = CASE WHEN $explicit_abandon THEN true ELSE coalesce(f.abandoned, false) END
                """,
                pid=project_id,
                fk=fk,
                text=text[:4000],
                ch=chapter_no,
                evidence=evidence,
                sug=sug,
                imp=imp,
                explicit_abandon=explicit_abandon,
            )

    with driver.session() as session:
        session.execute_write(work)
    logger.info("[Neo4j] lore upsert project=%s chapter=%s", project_id, chapter_no)


def upsert_narrative_chapter_context(
    *, project_id: str, chapter_no: int, obligations: dict[str, Any]
) -> None:
    """定稿后同步：本章 PG 任务单快照（与 LoreForeshadow 等并列，便于图查询剧情节拍）。"""
    driver = get_driver()
    if driver is None:
        return
    summary = str(obligations.get("summaryLine") or "")[:4000]
    raw = json.dumps(obligations, ensure_ascii=False)
    if len(raw) > 120_000:
        raw = raw[:120_000]
    ts = datetime.now(timezone.utc).isoformat()

    def work(tx) -> None:
        tx.run(
            """
            MERGE (n:LoreNarrativeChapterContext {project_id: $pid, chapter_no: $ch})
            SET n.obligations_json = $json,
                n.summary_line = $sum,
                n.updated_at = $ts
            """,
            pid=project_id,
            ch=chapter_no,
            json=raw,
            sum=summary,
            ts=ts,
        )

    with driver.session() as session:
        session.execute_write(work)
    logger.info("[Neo4j] narrative chapter context project=%s chapter=%s", project_id, chapter_no)


def upsert_narrative_structure_sync(
    *, project_id: str, storylines: list[dict[str, Any]], confluences: list[dict[str, Any]]
) -> None:
    """将 PG 故事线 / 汇合点同步为 Neo4j 节点（与 Lore 图谱并列）。"""
    driver = get_driver()
    if driver is None:
        return
    ts = datetime.now(timezone.utc).isoformat()

    def work(tx) -> None:
        for sl in storylines:
            if not isinstance(sl, dict):
                continue
            sid = str(sl.get("id") or "")
            if not sid:
                continue
            tx.run(
                """
                MERGE (s:LoreStoryline {project_id: $pid, storyline_id: $sid})
                SET s.storyline_key = $key,
                    s.title = $title,
                    s.status = $status,
                    s.updated_at = $ts
                """,
                pid=project_id,
                sid=sid,
                key=str(sl.get("storylineKey") or sl.get("storyline_key") or "")[:64],
                title=str(sl.get("title") or "")[:512],
                status=str(sl.get("status") or "ACTIVE")[:32],
                ts=ts,
            )
            parent = sl.get("parentStorylineId") or sl.get("parent_storyline_id")
            if parent:
                tx.run(
                    """
                    MATCH (p:LoreStoryline {project_id: $pid, storyline_id: $parent})
                    MATCH (c:LoreStoryline {project_id: $pid, storyline_id: $child})
                    MERGE (p)-[r:LORE_PARENT_STORYLINE {project_id: $pid}]->(c)
                    SET r.updated_at = $ts
                    """,
                    pid=project_id,
                    parent=str(parent),
                    child=sid,
                    ts=ts,
                )
        for cf in confluences:
            if not isinstance(cf, dict):
                continue
            cid = str(cf.get("id") or "")
            if not cid:
                continue
            tx.run(
                """
                MERGE (c:LoreConfluence {project_id: $pid, confluence_id: $cid})
                SET c.confluence_type = $ctype,
                    c.target_chapter = $tch,
                    c.resolved = $resolved,
                    c.context_summary = $ctx,
                    c.updated_at = $ts
                """,
                pid=project_id,
                cid=cid,
                ctype=str(cf.get("confluenceType") or cf.get("confluence_type") or "intersect")[:32],
                tch=int(cf.get("targetChapter") or cf.get("target_chapter") or 0),
                resolved=bool(cf.get("resolved")),
                ctx=str(cf.get("contextSummary") or cf.get("context_summary") or "")[:4000],
                ts=ts,
            )
            primary = cf.get("primaryStorylineId") or cf.get("primary_storyline_id")
            secondary = cf.get("secondaryStorylineId") or cf.get("secondary_storyline_id")
            if primary:
                tx.run(
                    """
                    MATCH (s:LoreStoryline {project_id: $pid, storyline_id: $sid})
                    MATCH (c:LoreConfluence {project_id: $pid, confluence_id: $cid})
                    MERGE (s)-[r:LORE_CONFLUENCE_PRIMARY {project_id: $pid}]->(c)
                    SET r.updated_at = $ts
                    """,
                    pid=project_id,
                    sid=str(primary),
                    cid=cid,
                    ts=ts,
                )
            if secondary:
                tx.run(
                    """
                    MATCH (s:LoreStoryline {project_id: $pid, storyline_id: $sid})
                    MATCH (c:LoreConfluence {project_id: $pid, confluence_id: $cid})
                    MERGE (s)-[r:LORE_CONFLUENCE_SECONDARY {project_id: $pid}]->(c)
                    SET r.updated_at = $ts
                    """,
                    pid=project_id,
                    sid=str(secondary),
                    cid=cid,
                    ts=ts,
                )

    with driver.session() as session:
        session.execute_write(work)
    logger.info(
        "[Neo4j] narrative structure sync project=%s storylines=%s confluences=%s",
        project_id,
        len(storylines),
        len(confluences),
    )


def list_unresolved_foreshadow_planted_before_chapter(
    *, project_id: str, before_chapter_no: int, limit: int = 40
) -> list[dict[str, Any]]:
    """伏笔在本章之前埋设且仍未回收的候选项（供定稿回收判定）；不含本章刚 ingest 的新钩。"""
    driver = get_driver()
    if driver is None:
        return []

    def read_tx(tx):
        rows = tx.run(
            """
            MATCH (f:LoreForeshadow {project_id: $pid})
            WHERE coalesce(f.resolved, false) = false
              AND coalesce(f.abandoned, false) = false
              AND coalesce(f.chapter_no, 0) < $before_ch
            RETURN f.fs_key AS fs_key, f.text AS text, f.chapter_no AS chapter_no
            ORDER BY coalesce(f.chapter_no, 0) ASC
            LIMIT $lim
            """,
            pid=project_id,
            before_ch=before_chapter_no,
            lim=limit,
        )
        return [dict(r) for r in rows]

    with driver.session() as session:
        return list(session.execute_read(read_tx))


def mark_foreshadows_resolved(
    *, project_id: str, resolved_in_chapter_no: int, fs_keys: list[str]
) -> int:
    """将给定 fs_key 标为已回收（幂等：已 resolved 的节点不重复计数）。"""
    keys = [k for k in fs_keys if isinstance(k, str) and k.strip()]
    if not keys:
        return 0
    driver = get_driver()
    if driver is None:
        return 0

    def work(tx) -> int:
        rec = tx.run(
            """
            MATCH (f:LoreForeshadow {project_id: $pid})
            WHERE f.fs_key IN $keys AND coalesce(f.resolved, false) = false
            SET f.resolved = true,
                f.resolved_chapter_no = $rch
            RETURN count(f) AS c
            """,
            pid=project_id,
            keys=keys,
            rch=resolved_in_chapter_no,
        )
        row = rec.single()
        return int(row["c"]) if row and row.get("c") is not None else 0

    with driver.session() as session:
        updated = int(session.execute_write(work))
    if updated:
        logger.info(
            "[Neo4j] foreshadow resolved project=%s chapter=%s count=%s",
            project_id,
            resolved_in_chapter_no,
            updated,
        )
    return updated


def recall_for_chapter(
    *,
    project_id: str,
    outline_character_names: list[str],
    recent_event_limit: int = 12,
    foreshadow_limit: int = 16,
    relationship_limit: int = 48,
) -> dict[str, Any]:
    """根据章纲人物召回关系网、未回收伏笔、近期事件。"""
    driver = get_driver()
    out: dict[str, Any] = {
        "relationship_edges": [],
        "nodes_touched": [],
        "recent_events": [],
        "open_foreshadowing": [],
        "query_names": [],
    }
    if driver is None:
        return out

    names = [_norm_name(x) for x in outline_character_names if _norm_name(x)]
    out["query_names"] = names
    if not names:
        return out

    lowered = [x.casefold() for x in names]

    def read_tx(tx):
        rel_rows = tx.run(
            """
            MATCH (a:LoreCharacter {project_id: $pid})-[r:LoreRelationship]->(b:LoreCharacter {project_id: $pid})
            WHERE toLower(a.name) IN $ln OR toLower(b.name) IN $ln
            RETURN a.name AS from_name, b.name AS to_name, r.kind AS kind,
                   r.chapter_no AS chapter_no, r.evidence AS evidence
            ORDER BY coalesce(r.chapter_no, 0) DESC
            LIMIT $rlim
            """,
            pid=project_id,
            ln=lowered,
            rlim=relationship_limit,
        )
        edges = [dict(r) for r in rel_rows]

        ev_rows = tx.run(
            """
            MATCH (c:LoreCharacter {project_id: $pid})-[:PARTICIPATED_IN]->(e:LoreEvent {project_id: $pid})
            WHERE toLower(c.name) IN $ln
            RETURN DISTINCT e.summary AS summary, e.chapter_no AS chapter_no, e.evidence AS evidence
            ORDER BY coalesce(e.chapter_no, 0) DESC
            LIMIT $elim
            """,
            pid=project_id,
            ln=lowered,
            elim=recent_event_limit,
        )
        events = [dict(r) for r in ev_rows]
        if len(events) < recent_event_limit:
            extra = tx.run(
                """
                MATCH (e:LoreEvent {project_id: $pid})
                RETURN e.summary AS summary, e.chapter_no AS chapter_no, e.evidence AS evidence
                ORDER BY coalesce(e.chapter_no, 0) DESC
                LIMIT $elim
                """,
                pid=project_id,
                elim=recent_event_limit,
            )
            seen = {(r.get("summary"), r.get("chapter_no")) for r in events}
            for r in extra:
                d = dict(r)
                key = (d.get("summary"), d.get("chapter_no"))
                if key in seen:
                    continue
                seen.add(key)
                events.append(d)
                if len(events) >= recent_event_limit:
                    break

        fs_rows = tx.run(
            """
            MATCH (f:LoreForeshadow {project_id: $pid})
            WHERE coalesce(f.resolved, false) = false
              AND coalesce(f.abandoned, false) = false
            RETURN f.fs_key AS fs_key, f.text AS text, f.chapter_no AS chapter_no, f.evidence AS evidence,
                   f.suggested_resolve_chapter AS suggested_resolve_chapter,
                   f.importance AS importance, f.abandoned AS abandoned
            ORDER BY coalesce(f.chapter_no, 0) ASC
            LIMIT $flim
            """,
            pid=project_id,
            flim=foreshadow_limit,
        )
        fss = [dict(r) for r in fs_rows]

        nodes = tx.run(
            """
            MATCH (c:LoreCharacter {project_id: $pid})
            WHERE toLower(c.name) IN $ln
            RETURN c.name AS name, c.last_chapter_no AS last_chapter_no,
                   c.evidence AS evidence, c.role_hint AS role_hint
            """,
            pid=project_id,
            ln=lowered,
        )
        node_list = [dict(r) for r in nodes]
        return edges, events, fss, node_list

    with driver.session() as session:
        edges, events, fss, node_list = session.execute_read(read_tx)

    out["relationship_edges"] = edges
    out["recent_events"] = events[:recent_event_limit]
    out["open_foreshadowing"] = fss
    out["nodes_touched"] = node_list
    return out


def export_snapshot(*, project_id: str, limits: tuple[int, int, int] = (400, 400, 200)) -> dict[str, Any]:
    """前端表格：全量拉取（带上限）。"""
    lim_c, lim_e, lim_r = limits
    driver = get_driver()
    empty: dict[str, Any] = {
        "characters": [],
        "events": [],
        "relationships": [],
        "foreshadowing": [],
        "neo4j_enabled": False,
    }
    if driver is None:
        return empty

    def read_tx(tx):
        chars = list(
            tx.run(
                """
                MATCH (c:LoreCharacter {project_id: $pid})
                RETURN c.name AS name, c.last_chapter_no AS last_chapter_no,
                       c.evidence AS evidence, c.role_hint AS role_hint
                ORDER BY coalesce(c.last_chapter_no, 0) DESC
                LIMIT $lim
                """,
                pid=project_id,
                lim=lim_c,
            )
        )
        events = list(
            tx.run(
                """
                MATCH (e:LoreEvent {project_id: $pid})
                RETURN e.summary AS summary, e.chapter_no AS chapter_no, e.evidence AS evidence
                ORDER BY coalesce(e.chapter_no, 0) DESC
                LIMIT $lim
                """,
                pid=project_id,
                lim=lim_e,
            )
        )
        rels = list(
            tx.run(
                """
                MATCH (a:LoreCharacter {project_id: $pid})-[r:LoreRelationship]->(b:LoreCharacter {project_id: $pid})
                RETURN a.name AS from_name, b.name AS to_name, r.kind AS kind,
                       r.chapter_no AS chapter_no, r.evidence AS evidence
                ORDER BY coalesce(r.chapter_no, 0) DESC
                LIMIT $lim
                """,
                pid=project_id,
                lim=lim_r,
            )
        )
        fss = list(
            tx.run(
                """
                MATCH (f:LoreForeshadow {project_id: $pid})
                RETURN f.fs_key AS fs_key, f.text AS text, f.chapter_no AS chapter_no, f.evidence AS evidence,
                       coalesce(f.resolved, false) AS resolved,
                       f.resolved_chapter_no AS resolved_chapter_no,
                       f.suggested_resolve_chapter AS suggested_resolve_chapter,
                       f.importance AS importance, f.abandoned AS abandoned
                ORDER BY coalesce(f.chapter_no, 0) ASC
                LIMIT $lim
                """,
                pid=project_id,
                lim=lim_e,
            )
        )
        return chars, events, rels, fss

    with driver.session() as session:
        chars, events, rels, fss = session.execute_read(read_tx)

    return {
        "characters": [dict(r) for r in chars],
        "events": [dict(r) for r in events],
        "relationships": [dict(r) for r in rels],
        "foreshadowing": [dict(r) for r in fss],
        "neo4j_enabled": True,
    }
