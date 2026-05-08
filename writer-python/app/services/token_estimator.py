from __future__ import annotations

import tiktoken


def _encoding_for_model(model: str) -> tiktoken.Encoding:
    try:
        return tiktoken.encoding_for_model(model)
    except KeyError:
        return tiktoken.get_encoding("cl100k_base")


def estimate_tokens(text: str, model: str) -> int:
    """Best-effort token count for budgeting; aligns with OpenAI-style models when tiktoken knows the model."""
    if not text:
        return 0
    enc = _encoding_for_model(model)
    return len(enc.encode(text))


def estimate_messages_tokens(messages: list[dict[str, str]], model: str) -> int:
    parts: list[str] = []
    for m in messages:
        role = m.get("role", "")
        content = m.get("content", "")
        parts.append(f"{role}\n{content}")
    return estimate_tokens("\n\n".join(parts), model)
