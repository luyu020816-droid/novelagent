"""在线程中跑阻塞流水线，通过 Queue 向主线程产出 SSE 字节块（用于 StreamingResponse）。"""

from __future__ import annotations

import queue
import threading
from collections.abc import Callable, Iterator

from app.services.sse_events import format_sse


def sse_threaded_generator(
    worker: Callable[[Callable[[str, dict], None]], None],
) -> Iterator[bytes]:
    """
    worker 接收 emit(event, payload)，内部应完成整条流水线并在结束时返回。
    """
    q: queue.Queue[bytes | None] = queue.Queue()

    def emit(event: str, payload: dict) -> None:
        q.put(format_sse(event, payload))

    def run() -> None:
        try:
            worker(emit)
        finally:
            q.put(None)

    threading.Thread(target=run, daemon=True).start()
    while True:
        item = q.get()
        if item is None:
            break
        yield item
