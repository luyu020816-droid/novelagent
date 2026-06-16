"""可选：章节生成过程中回调 Java 更新任务进度（与 InternalGenerationJobController 对应）。"""

from __future__ import annotations

import json
import logging
import os
from typing import Any

from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

LOG = logging.getLogger(__name__)

JAVA_BASE = os.environ.get("MYTHOSFORGE_JAVA_URL", "http://127.0.0.1:8080").rstrip("/")
INTERNAL_TOKEN = os.environ.get("MYTHOSFORGE_INTERNAL_TOKEN", "dev-internal-token")

_NODE_STAGE: dict[str, tuple[str, int]] = {
    "context_curator": ("上下文策展", 10),
    "planner": ("场景规划中", 22),
    "scene_director": ("场记约束", 26),
    "budget": ("Token 预算裁剪", 30),
    "ghostwriter": ("主笔生成中", 48),
    "critic": ("审查中", 58),
    "decision_gate": ("闸门裁定", 68),
    "bump_retry": ("准备重写", 72),
    "stylist": ("文笔润色", 88),
}


def _post_json(path: str, body: dict[str, Any], *, timeout: int = 30) -> None:
    url = f"{JAVA_BASE}{path}"
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = Request(
        url,
        data=data,
        headers={
            "Content-Type": "application/json; charset=utf-8",
            "X-Mythosforge-Internal-Token": INTERNAL_TOKEN,
        },
        method="POST",
    )
    try:
        with urlopen(req, timeout=timeout) as resp:
            if resp.status >= 400:
                LOG.warning("POST %s -> HTTP %s", path, resp.status)
    except HTTPError as e:
        LOG.warning("POST %s HTTP error %s: %s", path, e.code, e.read()[:500])
    except URLError as e:
        LOG.warning("POST %s URL error: %s", path, e)


def notify_node_end(job_id: str | None, node: str | None) -> None:
    if not job_id or not node:
        return
    if node in _NODE_STAGE:
        label, pct = _NODE_STAGE[node]
    else:
        label, pct = f"运行中 · {node}", 40
    _post_json(
        f"/api/internal/generation-jobs/{job_id}/progress",
        {"currentStage": label, "progressPct": pct, "node": node},
        timeout=30,
    )
