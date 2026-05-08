from __future__ import annotations

import time
from dataclasses import dataclass
from typing import Any

from openai import OpenAI

from app.config import Settings, get_settings
from app.services.token_estimator import estimate_messages_tokens
from app.services.usage_log import insert_llm_usage_log


@dataclass
class GatewayResult:
    text: str
    model: str
    latency_ms: int
    estimated_input_tokens: int
    estimated_output_tokens: int
    estimated_total_tokens: int
    actual_input_tokens: int | None
    actual_output_tokens: int | None
    actual_total_tokens: int | None


class LLMGateway:
    """Single entry for chat completions; logs each call to llm_usage_log."""

    def __init__(self, settings: Settings | None = None) -> None:
        self._settings = settings or get_settings()
        kwargs: dict[str, Any] = {}
        if self._settings.openai_api_key:
            kwargs["api_key"] = self._settings.openai_api_key
        if self._settings.openai_base_url:
            kwargs["base_url"] = self._settings.openai_base_url
        self._client = OpenAI(**kwargs)

    def chat_completion(
        self,
        *,
        messages: list[dict[str, str]],
        model: str | None = None,
        temperature: float = 0.2,
        response_format_json: bool = False,
        agent_name: str | None = None,
        node_name: str | None = None,
        job_id: str | None = None,
        project_id: str | None = None,
        chapter_no: int | None = None,
    ) -> GatewayResult:
        use_model = model or self._settings.llm_model
        est_in = estimate_messages_tokens(messages, use_model)
        est_out_guess = max(256, min(4096, est_in // 4 + 128))
        est_total = est_in + est_out_guess

        t0 = time.perf_counter()
        status = "ok"
        err_msg: str | None = None
        text = ""
        act_in: int | None = None
        act_out: int | None = None
        act_tot: int | None = None
        try:
            kwargs_cc: dict[str, Any] = {
                "model": use_model,
                "messages": messages,
                "temperature": temperature,
            }
            if response_format_json:
                kwargs_cc["response_format"] = {"type": "json_object"}
            resp = self._client.chat.completions.create(**kwargs_cc)
            text = (resp.choices[0].message.content or "").strip()
            if resp.usage:
                act_in = resp.usage.prompt_tokens
                act_out = resp.usage.completion_tokens
                act_tot = resp.usage.total_tokens
        except Exception as e:
            status = "error"
            err_msg = str(e)
            text = ""
        latency_ms = int((time.perf_counter() - t0) * 1000)

        insert_llm_usage_log(
            {
                "job_id": job_id,
                "project_id": project_id,
                "chapter_no": chapter_no,
                "agent_name": agent_name,
                "node_name": node_name,
                "provider": "openai",
                "model": use_model,
                "estimated_input_tokens": est_in,
                "estimated_output_tokens": est_out_guess,
                "estimated_total_tokens": est_total,
                "actual_input_tokens": act_in,
                "actual_output_tokens": act_out,
                "actual_total_tokens": act_tot,
                "latency_ms": latency_ms,
                "status": status,
                "error_message": err_msg,
            }
        )

        if status == "error":
            raise RuntimeError(err_msg or "LLM call failed")

        return GatewayResult(
            text=text,
            model=use_model,
            latency_ms=latency_ms,
            estimated_input_tokens=est_in,
            estimated_output_tokens=est_out_guess,
            estimated_total_tokens=est_total,
            actual_input_tokens=act_in,
            actual_output_tokens=act_out,
            actual_total_tokens=act_tot,
        )
