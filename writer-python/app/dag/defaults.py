"""默认章节 DAG — 对齐 PlotPilot 单幕全流程，映射 MythosForge 现有节点能力。"""

from __future__ import annotations

from app.dag.models import DAGDefinition, EdgeCondition, EdgeDefinition, NodeDefinition


def get_default_dag() -> DAGDefinition:
    """默认 DAG：上下文链 → 规划 → 执行 → 多层校验 → 网关 → 润色/重写。"""
    nodes = [
        NodeDefinition(id="ctx_blueprint", type="ctx_blueprint", label="剧本基建", position={"x": 80, "y": 80}),
        NodeDefinition(id="ctx_memory", type="ctx_memory", label="记忆引擎", position={"x": 80, "y": 180}),
        NodeDefinition(id="ctx_foreshadow", type="ctx_foreshadow", label="伏笔注入", position={"x": 80, "y": 280}),
        NodeDefinition(id="ctx_voice", type="ctx_voice", label="角色声线", position={"x": 80, "y": 380}),
        NodeDefinition(id="ctx_debt", type="ctx_debt", label="叙事债务", position={"x": 80, "y": 480}),
        NodeDefinition(id="ctx_vector", type="ctx_vector", label="向量召回", position={"x": 80, "y": 580}),
        NodeDefinition(id="ctx_assemble", type="ctx_assemble", label="上下文组装", position={"x": 280, "y": 330}),
        NodeDefinition(id="anti_ai_behavior", type="anti_ai_behavior", label="行为协议", position={"x": 480, "y": 120}),
        NodeDefinition(id="planning_beat_sheet", type="planning_beat_sheet", label="节拍表", position={"x": 480, "y": 240}),
        NodeDefinition(id="planning_act", type="planning_act", label="场景规划", position={"x": 480, "y": 360}),
        NodeDefinition(id="planning_outline_partition", type="planning_outline_partition", label="章纲分区", position={"x": 480, "y": 480}),
        NodeDefinition(id="budget_token", type="budget_token", label="Token 预算", position={"x": 680, "y": 300}),
        NodeDefinition(id="exec_beat", type="exec_beat", label="节拍放大", position={"x": 880, "y": 200}),
        NodeDefinition(id="exec_writer", type="exec_writer", label="剧情引擎", position={"x": 880, "y": 320}),
        NodeDefinition(id="val_compliance", type="val_compliance", label="合规", position={"x": 1080, "y": 120}),
        NodeDefinition(id="val_length", type="val_length", label="篇幅", position={"x": 1080, "y": 220}),
        NodeDefinition(id="val_anti_ai", type="val_anti_ai", label="反 AI", position={"x": 1080, "y": 320}),
        NodeDefinition(id="val_tension", type="val_tension", label="张力", position={"x": 1080, "y": 420}),
        NodeDefinition(id="val_narrative", type="val_narrative", label="叙事审查", position={"x": 1280, "y": 320}),
        NodeDefinition(id="review_timeline", type="review_timeline", label="时间线审查", position={"x": 1380, "y": 220}),
        NodeDefinition(id="review_storyline", type="review_storyline", label="故事线审查", position={"x": 1380, "y": 420}),
        NodeDefinition(id="review_character", type="review_character", label="设定一致性", position={"x": 1480, "y": 320}),
        NodeDefinition(id="gw_circuit", type="gw_circuit", label="熔断网关", position={"x": 1680, "y": 320}),
        NodeDefinition(id="exec_polish", type="exec_polish", label="润色", position={"x": 1680, "y": 220}),
        NodeDefinition(id="anti_ai_finale", type="anti_ai_finale", label="终稿 Anti-AI", position={"x": 1680, "y": 420}),
        NodeDefinition(id="gw_retry", type="gw_retry", label="重写", position={"x": 1280, "y": 520}),
    ]
    edges = [
        EdgeDefinition(id="edge_01", source="ctx_blueprint", target="ctx_memory"),
        EdgeDefinition(id="edge_02", source="ctx_memory", target="ctx_foreshadow"),
        EdgeDefinition(id="edge_03", source="ctx_foreshadow", target="ctx_voice"),
        EdgeDefinition(id="edge_04", source="ctx_voice", target="ctx_debt"),
        EdgeDefinition(id="edge_05", source="ctx_debt", target="ctx_vector"),
        EdgeDefinition(id="edge_06", source="ctx_vector", target="ctx_assemble"),
        EdgeDefinition(id="edge_07", source="ctx_assemble", target="anti_ai_behavior"),
        EdgeDefinition(id="edge_08", source="anti_ai_behavior", target="planning_beat_sheet"),
        EdgeDefinition(id="edge_09", source="planning_beat_sheet", target="planning_act"),
        EdgeDefinition(id="edge_10", source="planning_act", target="planning_outline_partition"),
        EdgeDefinition(id="edge_11", source="planning_outline_partition", target="budget_token"),
        EdgeDefinition(id="edge_12", source="budget_token", target="exec_beat"),
        EdgeDefinition(id="edge_13", source="exec_beat", target="exec_writer"),
        EdgeDefinition(id="edge_14", source="exec_writer", target="val_compliance"),
        EdgeDefinition(id="edge_15", source="val_compliance", target="val_length"),
        EdgeDefinition(id="edge_16", source="val_length", target="val_anti_ai"),
        EdgeDefinition(id="edge_17", source="val_anti_ai", target="val_tension"),
        EdgeDefinition(id="edge_18", source="val_tension", target="val_narrative"),
        EdgeDefinition(id="edge_19", source="val_narrative", target="review_timeline"),
        EdgeDefinition(id="edge_19a", source="review_timeline", target="review_storyline"),
        EdgeDefinition(id="edge_19b", source="review_storyline", target="review_character"),
        EdgeDefinition(id="edge_19c", source="review_character", target="gw_circuit"),
        EdgeDefinition(id="edge_20", source="gw_circuit", target="exec_polish", condition=EdgeCondition.ON_ACCEPT),
        EdgeDefinition(id="edge_21", source="gw_circuit", target="gw_retry", condition=EdgeCondition.ON_REJECT),
        EdgeDefinition(id="edge_22", source="exec_polish", target="anti_ai_finale"),
        EdgeDefinition(id="edge_23", source="gw_retry", target="exec_writer", condition=EdgeCondition.ON_RETRY),
    ]
    return DAGDefinition(
        id="dag_default_chapter",
        name="章节全流程（默认）",
        version=1,
        description="PlotPilot 风格可配置 DAG，运行时编译为 LangGraph",
        nodes=nodes,
        edges=edges,
    )


def list_manual_add_node_types() -> list[str]:
    """不在默认链上、可手动插入的类型（与注册表补集）。"""
    from app.dag.registry import NodeRegistry

    NodeRegistry.ensure_builtins_loaded()
    default_types = {n.type for n in get_default_dag().nodes}
    return sorted(NodeRegistry.all_types() - default_types)
