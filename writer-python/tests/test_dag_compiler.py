"""DAG 编译与校验测试。"""

from __future__ import annotations

import unittest

from app.dag.compiler import compile_dag_to_langgraph, validate_dag
from app.dag.defaults import get_default_dag
from app.dag.models import DAGDefinition, EdgeDefinition, NodeDefinition
from app.dag.registry import NodeRegistry
from app.services.llm_gateway import LLMGateway


class DagCompilerTest(unittest.TestCase):
    def setUp(self) -> None:
        NodeRegistry.ensure_builtins_loaded()

    def test_default_dag_validates(self) -> None:
        dag = get_default_dag()
        errs = validate_dag(dag)
        self.assertEqual(errs, [], errs)

    def test_default_dag_compiles(self) -> None:
        dag = get_default_dag()
        g = compile_dag_to_langgraph(dag, gateway=LLMGateway())
        compiled = g.compile()
        self.assertIsNotNone(compiled)

    def test_fingerprint_stable(self) -> None:
        dag = get_default_dag()
        self.assertEqual(dag.fingerprint(), dag.fingerprint())

    def test_unknown_type_fails(self) -> None:
        dag = DAGDefinition(
            id="bad",
            name="bad",
            nodes=[NodeDefinition(id="n1", type="not_a_real_type")],
            edges=[],
        )
        errs = validate_dag(dag)
        self.assertTrue(any("未注册" in e for e in errs))

    def test_insert_generic_node_validates(self) -> None:
        from app.dag.node_factory import scaffold_node_from_description
        from app.dag.models import NodeCategory

        dag = get_default_dag()
        extra, _ = scaffold_node_from_description(
            "检查感情线是否突兀",
            instance_id="val_romance",
            category=NodeCategory.VALIDATION,
        )
        nodes = list(dag.nodes) + [extra]
        edges = [
            e for e in dag.edges if not (e.source == "val_tension" and e.target == "val_narrative")
        ] + [
            EdgeDefinition(id="edge_x1", source="val_tension", target="val_romance"),
            EdgeDefinition(id="edge_x2", source="val_romance", target="val_narrative"),
        ]
        custom = DAGDefinition(
            id="dag_custom",
            name="custom",
            nodes=nodes,
            edges=edges,
        )
        errs = validate_dag(custom)
        self.assertEqual(errs, [], errs)


if __name__ == "__main__":
    unittest.main()
