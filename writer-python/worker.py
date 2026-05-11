#!/usr/bin/env python3
"""Day 14：RabbitMQ 消费者 — 拉取章节生成任务，本地执行 LangGraph，节点结束时回调 Java 更新进度。"""

from __future__ import annotations

import json
import logging
import os
import sys
from pathlib import Path
from typing import Any
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

import pika

ROOT = Path(__file__).resolve().parent
sys.path.insert(0, str(ROOT))

from app.graph.graph_runner import run_chapter_graph

logging.basicConfig(level=logging.INFO)
LOG = logging.getLogger("chapter_worker")

JAVA_BASE = os.environ.get("MYTHOSFORGE_JAVA_URL", "http://127.0.0.1:8080").rstrip("/")
INTERNAL_TOKEN = os.environ.get("MYTHOSFORGE_INTERNAL_TOKEN", "dev-internal-token")
QUEUE = os.environ.get("RABBITMQ_QUEUE", "chapter.generation.queue")

NODE_STAGE: dict[str, tuple[str, int]] = {
    "context_curator": ("上下文策展", 10),
    "planner": ("场景规划中", 22),
    "budget": ("Token 预算裁剪", 30),
    "ghostwriter": ("主笔生成中", 48),
    "critic": ("审查中", 58),
    "decision_gate": ("闸门裁定", 68),
    "bump_retry": ("准备重写", 72),
    "stylist": ("文笔润色", 88),
}


def _post_json(path: str, body: dict[str, Any], *, timeout: int = 120) -> None:
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


def _get_payload(job_id: str) -> dict[str, Any]:
    url = f"{JAVA_BASE}/api/internal/generation-jobs/{job_id}/payload"
    req = Request(
        url,
        headers={"X-Mythosforge-Internal-Token": INTERNAL_TOKEN},
        method="GET",
    )
    with urlopen(req, timeout=120) as resp:
        raw = resp.read().decode("utf-8")
    obj = json.loads(raw)
    if not isinstance(obj, dict):
        raise ValueError("payload not object")
    return obj


def _progress(job_id: str, node: str | None) -> None:
    if node and node in NODE_STAGE:
        label, pct = NODE_STAGE[node]
    elif node:
        label, pct = f"运行中 · {node}", 40
    else:
        label, pct = "Worker 已领取任务", 3
    _post_json(
        f"/api/internal/generation-jobs/{job_id}/progress",
        {"currentStage": label, "progressPct": pct, "node": node},
        timeout=30,
    )


def _handle_job(job_id: str) -> None:
    _progress(job_id, None)
    initial = _get_payload(job_id)

    def emit(event: str, payload: dict[str, Any]) -> None:
        if event == "node_end":
            node = payload.get("node")
            if isinstance(node, str):
                _progress(job_id, node)

    try:
        result = run_chapter_graph(initial, emit)
        _post_json(f"/api/internal/generation-jobs/{job_id}/complete", result, timeout=60)
        LOG.info("job %s complete", job_id)
    except Exception as e:
        LOG.exception("job %s failed", job_id)
        _post_json(f"/api/internal/generation-jobs/{job_id}/fail", {"message": str(e)}, timeout=30)


def on_message(ch: Any, method: Any, _properties: Any, body: bytes) -> None:
    job_id: str | None = None
    try:
        msg = json.loads(body.decode("utf-8"))
        job_id = msg.get("jobId") or msg.get("job_id")
        if not job_id:
            LOG.error("missing jobId in %s", body[:200])
            return
        LOG.info("consume jobId=%s", job_id)
        _handle_job(str(job_id))
    except Exception:
        LOG.exception("consume error jobId=%s", job_id)
        if job_id:
            _post_json(f"/api/internal/generation-jobs/{job_id}/fail", {"message": "worker internal error"}, timeout=30)
    finally:
        ch.basic_ack(delivery_tag=method.delivery_tag)


def main() -> None:
    host = os.environ.get("RABBITMQ_HOST", "localhost")
    user = os.environ.get("RABBITMQ_USER", "mythosforge")
    password = os.environ.get("RABBITMQ_PASSWORD", "mythosforge")
    credentials = pika.PlainCredentials(user, password)
    params = pika.ConnectionParameters(host=host, credentials=credentials)
    connection = pika.BlockingConnection(params)
    channel = connection.channel()
    channel.queue_declare(queue=QUEUE, durable=True)
    channel.basic_qos(prefetch_count=1)
    channel.basic_consume(queue=QUEUE, on_message_callback=on_message)
    LOG.info("Worker listening queue=%s java=%s", QUEUE, JAVA_BASE)
    channel.start_consuming()


if __name__ == "__main__":
    main()
