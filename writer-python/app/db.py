from __future__ import annotations

import psycopg
from psycopg.rows import dict_row

from app.config import get_settings


def get_connection():
    s = get_settings()
    dsn = (
        f"host={s.postgres_host} port={s.postgres_port} dbname={s.postgres_db} "
        f"user={s.postgres_user} password={s.postgres_password}"
    )
    return psycopg.connect(dsn, row_factory=dict_row)
