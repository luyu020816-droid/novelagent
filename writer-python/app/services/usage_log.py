from __future__ import annotations

from typing import Any

from app.db import get_connection


def insert_llm_usage_log(row: dict[str, Any]) -> None:
    """Insert one row into llm_usage_log (matches Day 2 Flyway schema)."""
    cols = (
        "job_id",
        "project_id",
        "chapter_no",
        "agent_name",
        "node_name",
        "provider",
        "model",
        "estimated_input_tokens",
        "estimated_output_tokens",
        "estimated_total_tokens",
        "actual_input_tokens",
        "actual_output_tokens",
        "actual_total_tokens",
        "latency_ms",
        "status",
        "error_message",
    )
    values = {k: row.get(k) for k in cols}
    placeholders = ", ".join(f"%({k})s" for k in cols)
    columns_sql = ", ".join(cols)
    sql = f"INSERT INTO llm_usage_log ({columns_sql}) VALUES ({placeholders})"
    with get_connection() as conn:
        with conn.cursor() as cur:
            cur.execute(sql, values)
        conn.commit()
