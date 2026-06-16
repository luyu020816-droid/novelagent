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
        "prompt_version",
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
            try:
                cur.execute(sql, values)
            except Exception as e:
                if "prompt_version" in str(e).lower() and values.get("prompt_version") is not None:
                    values.pop("prompt_version", None)
                    cols_fallback = [c for c in cols if c != "prompt_version"]
                    ph2 = ", ".join(f"%({k})s" for k in cols_fallback)
                    sql2 = f"INSERT INTO llm_usage_log ({', '.join(cols_fallback)}) VALUES ({ph2})"
                    cur.execute(sql2, {k: values.get(k) for k in cols_fallback})
                else:
                    raise
        conn.commit()
