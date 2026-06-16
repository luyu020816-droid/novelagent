"""路径 B：多轮互动采访 Agent。"""

from __future__ import annotations

import json

from pydantic import ValidationError

from app.schemas.storyline_interview import GenreInterviewRequest, InterviewerResponse
from app.skills.loader import SeriesPreset, get_series_preset
from app.services.llm_gateway import LLMGateway
from app.services.prompt_registry import load_prompt


def _skill_block(preset: SeriesPreset) -> str:
    lines = [
        f"【Skill 名称】{preset['label']}",
        "",
        "【丛书摘要 / chapter_digest】",
        preset["chapter_digest"],
    ]

    def sec(title: str, key: str) -> None:
        items = preset[key]  # type: ignore[literal-required]
        if items:
            lines.append("")
            lines.append(f"【{title}】")
            lines.extend(f"- {x}" for x in items)

    sec("世界观规则 init_world_rules", "init_world_rules")
    sec("能力/规则 init_ability_rules", "init_ability_rules")
    sec("禁止情节 init_forbidden_moves", "init_forbidden_moves")
    sec("必须保留设定 init_must_retain_facts", "init_must_retain_facts")
    sec("忌讳题材 init_taboo_topics", "init_taboo_topics")
    suf = str(preset["init_style_voice_suffix"] or "").strip()
    if suf:
        lines.extend(["", "【文风后缀 init_style_voice_suffix】", suf])
    return "\n".join(lines).strip()


_SKILL_MODE_SUFFIX = """

## Skill 模式附加规则（仅当上方提供了「作者已加载的丛书 Skill」时生效）

1. 必须先消化 Skill 中的规则、禁忌与摘要，追问应优先核对：是否与 Skill 冲突、是否有 Skill 要求但未说明的细节。
2. 对话前几轮应优先澄清：作者希望落笔的题材/子类型（若 Skill 已限定则复述确认）、以及至少一个关键场景或开篇基调假设；不要罗列多套路让读者「选题」。
3. 完成的 `final_summary` 与 `core_settings` 不得违背 Skill 中的硬性约束（禁忌、必须保留事实等）。
4. 若作者构思明显违反 Skill，应在 `reply_to_user` 中指出冲突并请作者调整；仍不清楚则用 `"asking"` 继续追问。
"""


def _parse_llm_json(text: str) -> dict:
    t = text.strip()
    if t.startswith("```"):
        lines = t.split("\n")
        if lines and lines[0].lstrip().startswith("```"):
            lines = lines[1:]
        if lines and lines[-1].strip() == "```":
            lines = lines[:-1]
        t = "\n".join(lines)
    data = json.loads(t)
    if not isinstance(data, dict):
        raise ValueError("Interviewer output must be a JSON object")
    return data


def run(req: GenreInterviewRequest, gateway: LLMGateway) -> InterviewerResponse:
    system = load_prompt("storyline_interviewer_v1.md")
    skill_id = (req.writer_skill_id or "").strip()
    skill_extra = ""
    if skill_id:
        preset = get_series_preset(skill_id)
        if preset is None:
            raise ValueError(f"未知的 writerSkillId（library 中未找到）: {skill_id!r}")
        skill_extra = (
            "\n\n=== 作者已加载的丛书 Skill（你必须完整阅读）===\n"
            + _skill_block(preset)
            + "\n=== Skill 结束 ==="
            + _SKILL_MODE_SUFFIX
        )
    schema_hint = json.dumps(InterviewerResponse.model_json_schema(), ensure_ascii=False)[:8000]
    system_full = (
        f"{system}{skill_extra}\n\n"
        f"=== InterviewerResponse JSON Schema（节选）===\n{schema_hint}\n"
        "你必须只输出一个 JSON 对象，键名使用 camelCase：replyToUser、finalSummary、coreSettings。"
    )
    messages: list[dict[str, str]] = [{"role": "system", "content": system_full}]
    for turn in req.chat_history:
        messages.append({"role": turn.role, "content": turn.content})

    gr = gateway.chat_completion(
        messages=messages,
        response_format_json=True,
        temperature=0.4,
        agent_name="storyline_interviewer",
        node_name="main",
        project_id=req.project_id,
    )
    try:
        raw = _parse_llm_json(gr.text)
    except json.JSONDecodeError as e:
        raise RuntimeError(f"Interviewer returned non-JSON: {e}") from e

    # 允许模型偶尔输出 snake_case，统一转一层
    normalized: dict = {}
    for k, v in raw.items():
        if k == "reply_to_user":
            normalized["reply_to_user"] = v
        elif k == "replyToUser":
            normalized["reply_to_user"] = v
        elif k == "final_summary":
            normalized["final_summary"] = v
        elif k == "finalSummary":
            normalized["final_summary"] = v
        elif k == "core_settings":
            normalized["core_settings"] = v
        elif k == "coreSettings":
            normalized["core_settings"] = v
        elif k == "status":
            normalized["status"] = v
    try:
        return InterviewerResponse.model_validate(normalized)
    except ValidationError as e:
        raise ValueError(f"Interviewer JSON failed schema: {e}") from e
