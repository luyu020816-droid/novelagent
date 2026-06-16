"""节点注册表 — 类型元数据 + LangGraph 可执行函数工厂。"""

from __future__ import annotations

import logging
from collections.abc import Callable
from typing import Any

from app.dag.models import NodeConfig, NodeMeta

logger = logging.getLogger(__name__)

NodeRunFn = Callable[[dict[str, Any]], dict[str, Any]]

_registry: dict[str, NodeRunFn] = {}
_meta_registry: dict[str, NodeMeta] = {}
_configurable: dict[str, type] = {}


class NodeRegistry:
    @classmethod
    def register(cls, node_type: str, meta: NodeMeta):
        def decorator(fn: NodeRunFn) -> NodeRunFn:
            if node_type in _registry:
                logger.warning("节点类型 %s 重复注册，已覆盖", node_type)
            _registry[node_type] = fn
            _meta_registry[node_type] = meta
            return fn

        return decorator

    @classmethod
    def register_class(cls, node_type: str, node_cls: type) -> None:
        _configurable[node_type] = node_cls
        if hasattr(node_cls, "meta") and hasattr(node_cls, "run"):
            _meta_registry[node_type] = node_cls.meta
            _registry[node_type] = node_cls.run

    @classmethod
    def has(cls, node_type: str) -> bool:
        return node_type in _registry

    @classmethod
    def get_meta(cls, node_type: str) -> NodeMeta:
        if node_type not in _meta_registry:
            raise KeyError(f"未注册节点类型: {node_type}")
        return _meta_registry[node_type]

    @classmethod
    def all_types(cls) -> set[str]:
        return set(_registry.keys())

    @classmethod
    def all_meta(cls) -> dict[str, NodeMeta]:
        return dict(_meta_registry)

    @classmethod
    def ensure_builtins_loaded(cls) -> None:
        import app.dag.nodes  # noqa: F401

    @classmethod
    def create_executor(
        cls,
        node_type: str,
        node_id: str,
        config: NodeConfig | None = None,
        *,
        gateway: Any = None,
    ) -> NodeRunFn:
        cls.ensure_builtins_loaded()
        if node_type not in _registry:
            raise KeyError(f"未注册节点类型: {node_type}，已注册: {sorted(_registry.keys())}")
        base_fn = _registry[node_type]
        cfg = config or NodeConfig()

        def executor(state: dict[str, Any]) -> dict[str, Any]:
            if not state.get("_dag_enabled", True):
                return {}
            disabled = state.get("disabled_nodes") or []
            if node_id in disabled:
                return {"_dag_bypassed": {node_id: True}}
            run_state = dict(state)
            run_state["_dag_node_id"] = node_id
            run_state["_dag_node_type"] = node_type
            run_state["_dag_node_config"] = cfg.model_dump()
            if gateway is not None:
                run_state["_dag_gateway"] = gateway
            try:
                out = base_fn(run_state)
                if not isinstance(out, dict):
                    return {}
                out.setdefault("_dag_trace", [])
                trace = out.get("_dag_trace")
                if isinstance(trace, list):
                    trace.append({"id": node_id, "type": node_type, "ok": True})
                return out
            except Exception as e:
                logger.exception("DAG 节点 %s (%s) 执行失败", node_id, node_type)
                return {
                    "_dag_error": {node_id: str(e)},
                    "_dag_trace": [{"id": node_id, "type": node_type, "ok": False, "error": str(e)}],
                }

        return executor
