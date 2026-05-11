"""章节 Graph 运行期间，把 SSE emit 函数放进 ContextVar，供节点内 LLMGateway 发 llm_delta。"""

from __future__ import annotations

import contextvars
from collections.abc import Callable

EmitFn = Callable[[str, dict], None]

_sse_emit: contextvars.ContextVar[EmitFn | None] = contextvars.ContextVar("chapter_sse_emit", default=None)


def set_chapter_sse_emit(fn: EmitFn | None) -> contextvars.Token[EmitFn | None]:
    return _sse_emit.set(fn)


def reset_chapter_sse_emit(token: contextvars.Token[EmitFn | None]) -> None:
    _sse_emit.reset(token)


def get_chapter_sse_emit() -> EmitFn:
    fn = _sse_emit.get()
    if fn is None:

        def _noop(_event: str, _payload: dict) -> None:
            return

        return _noop
    return fn
