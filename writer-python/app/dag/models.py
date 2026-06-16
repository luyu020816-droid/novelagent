"""DAG 核心数据模型（对齐 PlotPilot application.engine.dag.models 子集）。"""

from __future__ import annotations

import hashlib
import json
from datetime import datetime, timezone
from enum import Enum
from typing import Any

from pydantic import BaseModel, Field, field_validator


class NodeCategory(str, Enum):
    CONTEXT = "context"
    EXECUTION = "execution"
    VALIDATION = "validation"
    GATEWAY = "gateway"
    WORLD = "world"
    REVIEW = "review"
    ANTI_AI = "anti-ai"
    PLANNING = "planning"


class NodeStatus(str, Enum):
    IDLE = "idle"
    RUNNING = "running"
    SUCCESS = "success"
    ERROR = "error"
    BYPASSED = "bypassed"


class EdgeCondition(str, Enum):
    ALWAYS = "always"
    ON_SUCCESS = "on_success"
    ON_ACCEPT = "on_accept"
    ON_REJECT = "on_reject"
    ON_RETRY = "on_retry"


class PortDataType(str, Enum):
    TEXT = "text"
    JSON = "json"
    BOOLEAN = "boolean"
    OBJECT = "object"


class NodePort(BaseModel):
    name: str
    data_type: PortDataType = PortDataType.TEXT
    required: bool = False
    description: str = ""


class PromptMode(str, Enum):
    CPMS_FIRST = "cpms_first"
    TEMPLATE_ONLY = "template_only"
    GENERIC = "generic"


class NodeConfig(BaseModel):
    prompt_template: str | None = None
    system_prompt: str | None = None
    thresholds: dict[str, float] = Field(default_factory=dict)
    max_retries: int = 1
    state_read_keys: list[str] = Field(default_factory=list)
    state_write_keys: list[str] = Field(default_factory=list)
    description: str = ""


class NodeMeta(BaseModel):
    node_type: str
    display_name: str
    category: NodeCategory
    icon: str = ""
    color: str = "#6366f1"
    input_ports: list[NodePort] = Field(default_factory=list)
    output_ports: list[NodePort] = Field(default_factory=list)
    cpms_node_key: str = ""
    prompt_mode: PromptMode = PromptMode.CPMS_FIRST
    description: str = ""
    is_configurable: bool = True
    can_disable: bool = True


class NodeDefinition(BaseModel):
    id: str = Field(pattern=r"^[a-z][a-z0-9_]*$")
    type: str
    label: str = ""
    enabled: bool = True
    position: dict[str, float] = Field(default_factory=lambda: {"x": 0.0, "y": 0.0})
    config: NodeConfig = Field(default_factory=NodeConfig)


class EdgeDefinition(BaseModel):
    id: str = Field(pattern=r"^edge_[a-z0-9_]+$")
    source: str
    target: str
    source_port: str = ""
    target_port: str = ""
    condition: EdgeCondition = EdgeCondition.ALWAYS


class DAGDefinition(BaseModel):
    id: str
    name: str
    version: int = Field(default=1, ge=1)
    description: str = ""
    nodes: list[NodeDefinition] = Field(default_factory=list)
    edges: list[EdgeDefinition] = Field(default_factory=list)

    def fingerprint(self) -> str:
        data = {
            "nodes": [{"id": n.id, "type": n.type, "enabled": n.enabled} for n in sorted(self.nodes, key=lambda n: n.id)],
            "edges": [{"id": e.id, "source": e.source, "target": e.target, "condition": e.condition.value}
                      for e in sorted(self.edges, key=lambda e: e.id)],
        }
        raw = json.dumps(data, sort_keys=True, ensure_ascii=False)
        return hashlib.sha256(raw.encode()).hexdigest()[:16]

    def get_node(self, node_id: str) -> NodeDefinition | None:
        return next((n for n in self.nodes if n.id == node_id), None)

    def get_entry_nodes(self) -> list[NodeDefinition]:
        targets = {e.target for e in self.edges}
        return [n for n in self.nodes if n.enabled and n.id not in targets]

    def get_successors(self, node_id: str) -> list[str]:
        return [e.target for e in self.edges if e.source == node_id]

    def get_predecessors(self, node_id: str) -> list[str]:
        return [e.source for e in self.edges if e.target == node_id]

    @field_validator("nodes")
    @classmethod
    def unique_node_ids(cls, nodes: list[NodeDefinition]) -> list[NodeDefinition]:
        ids = [n.id for n in nodes]
        if len(ids) != len(set(ids)):
            raise ValueError("duplicate node id in DAG")
        return nodes
