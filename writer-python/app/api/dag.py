"""DAG 元数据 API — 默认流程、节点类型、校验与节点工厂。"""

from __future__ import annotations

from typing import Any

from fastapi import APIRouter
from pydantic import BaseModel, Field

from app.dag.compiler import validate_dag
from app.dag.defaults import get_default_dag, list_manual_add_node_types
from app.dag.models import DAGDefinition, NodeCategory
from app.dag.node_factory import scaffold_node_from_description
from app.dag.registry import NodeRegistry

router = APIRouter(tags=["writer-dag"])


class DagValidateBody(BaseModel):
    dag: dict[str, Any] = Field(..., description="DAGDefinition JSON")


class ScaffoldNodeBody(BaseModel):
    description: str = Field(..., min_length=4, max_length=500)
    instance_id: str = Field(..., pattern=r"^[a-z][a-z0-9_]*$")
    category: str = Field(default="validation")


@router.get("/api/writer/dag/default")
def get_default_dag_spec() -> dict:
    return get_default_dag().model_dump()


@router.get("/api/writer/dag/node-types")
def list_dag_node_types() -> dict:
    NodeRegistry.ensure_builtins_loaded()
    meta = NodeRegistry.all_meta()
    return {
        "count": len(meta),
        "types": [
            {
                "node_type": m.node_type,
                "display_name": m.display_name,
                "category": m.category.value,
                "description": m.description,
                "cpms_node_key": m.cpms_node_key,
                "can_disable": m.can_disable,
            }
            for m in sorted(meta.values(), key=lambda x: x.node_type)
        ],
        "manual_add_types": list_manual_add_node_types(),
    }


@router.post("/api/writer/dag/validate")
def validate_dag_spec(body: DagValidateBody) -> dict:
    dag = DAGDefinition.model_validate(body.dag)
    errs = validate_dag(dag)
    return {"ok": len(errs) == 0, "errors": errs, "fingerprint": dag.fingerprint()}


@router.post("/api/writer/dag/scaffold-node")
def scaffold_node(body: ScaffoldNodeBody) -> dict:
    try:
        cat = NodeCategory(body.category)
    except ValueError:
        cat = NodeCategory.VALIDATION
    node, meta = scaffold_node_from_description(
        body.description,
        instance_id=body.instance_id,
        category=cat,
    )
    return {
        "node": node.model_dump(),
        "meta": {
            "node_type": meta.node_type,
            "display_name": meta.display_name,
            "category": meta.category.value,
            "description": meta.description,
        },
    }
