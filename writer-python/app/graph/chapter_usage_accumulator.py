"""单次章节 Graph 运行期间累加 LLM 用量（ContextVar），供 chapter_generation_final 汇总。"""

from __future__ import annotations

import contextvars
from typing import Any

_CallBucket = list[dict[str, Any]] | None
_calls: contextvars.ContextVar[_CallBucket] = contextvars.ContextVar("chapter_llm_usage_calls", default=None)


def chapter_usage_accumulator_set_active() -> contextvars.Token[_CallBucket]:
    """进入 graph_runner 时调用；返回 token 供 reset。"""
    return _calls.set([])


def chapter_usage_accumulator_reset(token: contextvars.Token[_CallBucket]) -> None:
    _calls.reset(token)


def chapter_usage_accumulator_append(record: dict[str, Any]) -> None:
    bucket = _calls.get()
    if bucket is None:
        return
    bucket.append(dict(record))


def _coalesce_tokens(
    actual: int | None,
    estimated: int | None,
) -> tuple[int, bool]:
    """返回 (数值, 是否使用了估算)。"""
    if actual is not None:
        return actual, False
    if estimated is not None:
        return max(0, estimated), True
    return 0, True


def chapter_usage_accumulator_build_summary() -> dict[str, Any]:
    """从当前 bucket 生成 llm_usage_summary；无激活 bucket 时返回空汇总。"""
    bucket = _calls.get()
    if not bucket:
        return {
            "calls": 0,
            "total_prompt_tokens": 0,
            "total_completion_tokens": 0,
            "total_tokens": 0,
            "includes_estimates": False,
            "by_node": {},
        }

    tot_p = tot_c = tot_all = 0
    any_est = False
    by_node: dict[str, dict[str, Any]] = {}

    for row in bucket:
        pin, e_in = row.get("actual_input_tokens"), row.get("estimated_input_tokens")
        pout, e_out = row.get("actual_output_tokens"), row.get("estimated_output_tokens")
        ptot, etot = row.get("actual_total_tokens"), row.get("estimated_total_tokens")

        np, u_in = _coalesce_tokens(pin, e_in)
        nc, u_out = _coalesce_tokens(pout, e_out)
        if ptot is not None:
            nt = ptot
        else:
            nt_sum = np + nc
            nt_et, _ = _coalesce_tokens(None, etot)
            nt = nt_sum if nt_sum > 0 else nt_et
        row_used_estimate = pin is None or pout is None or ptot is None
        any_est = any_est or row_used_estimate

        tot_p += np
        tot_c += nc
        tot_all += nt

        node = str(row.get("node_name") or "unknown")
        acc = by_node.setdefault(
            node,
            {"calls": 0, "total_prompt_tokens": 0, "total_completion_tokens": 0, "total_tokens": 0},
        )
        acc["calls"] += 1
        acc["total_prompt_tokens"] += np
        acc["total_completion_tokens"] += nc
        acc["total_tokens"] += nt

    return {
        "calls": len(bucket),
        "total_prompt_tokens": tot_p,
        "total_completion_tokens": tot_c,
        "total_tokens": tot_all,
        "includes_estimates": any_est,
        "by_node": by_node,
    }
