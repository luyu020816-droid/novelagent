"""PlotPilot 风格可配置 DAG — 编译为 LangGraph 执行。"""

from app.dag.compiler import build_chapter_graph_from_dag, compile_dag_to_langgraph, validate_dag
from app.dag.defaults import get_default_dag, list_manual_add_node_types
from app.dag.models import DAGDefinition
from app.dag.node_factory import scaffold_node_from_description
from app.dag.registry import NodeRegistry

__all__ = [
    "DAGDefinition",
    "NodeRegistry",
    "build_chapter_graph_from_dag",
    "compile_dag_to_langgraph",
    "get_default_dag",
    "list_manual_add_node_types",
    "scaffold_node_from_description",
    "validate_dag",
]
