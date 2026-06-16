"""从简短描述生成通用 LLM 节点配置（PlotPilot CPMS + Generic 节点工厂）。"""

from __future__ import annotations

from app.dag.models import NodeCategory, NodeConfig, NodeDefinition, NodeMeta, PromptMode


def scaffold_node_from_description(
    description: str,
    *,
    instance_id: str,
    category: NodeCategory = NodeCategory.VALIDATION,
    read_keys: list[str] | None = None,
    write_key: str = "_custom_node_result",
) -> tuple[NodeDefinition, NodeMeta]:
    """根据简短中文描述生成 generic_llm 节点实例与元数据。"""
    desc = description.strip()
    if not desc:
        raise ValueError("description 不能为空")
    reads = read_keys or ["chapter_text", "scene_plan", "context_pack"]
    sys_p = (
        "你是小说章节流水线中的专用审查/处理节点。"
        f"职责：{desc}。"
        "只输出 JSON，含 ok(boolean)、note(string)、details(object 可选)。"
    )
    user_t = (
        f"请根据下列输入完成：{desc}。"
        '输出 {"ok":true/false,"note":"…","details":{…}}'
    )
    cfg = NodeConfig(
        description=desc,
        system_prompt=sys_p,
        prompt_template=user_t,
        state_read_keys=reads,
        state_write_keys=[write_key],
    )
    meta = NodeMeta(
        node_type="generic_llm",
        display_name=desc[:32],
        category=category,
        prompt_mode=PromptMode.GENERIC,
        description=desc,
        is_configurable=True,
    )
    node = NodeDefinition(
        id=instance_id,
        type="generic_llm",
        label=desc[:24],
        config=cfg,
    )
    return node, meta
