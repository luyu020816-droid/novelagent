"""SSE 帧编码：事件名 + JSON data，供 FastAPI StreamingResponse 与 Java 透传解析。"""

from __future__ import annotations

import json


def format_sse(event: str, payload: dict) -> bytes:
    """标准 SSE：`event:` + `data:`（单行 JSON）+ 空行。"""
    data = json.dumps(payload, ensure_ascii=False)
    return f"event: {event}\ndata: {data}\n\n".encode("utf-8")
