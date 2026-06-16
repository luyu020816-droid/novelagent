"""DAG 注册表与默认 30 节点类型测试。"""

from __future__ import annotations

import unittest

from app.dag.defaults import get_default_dag, list_manual_add_node_types
from app.dag.registry import NodeRegistry


class DagRegistryTest(unittest.TestCase):
    def setUp(self) -> None:
        NodeRegistry.ensure_builtins_loaded()

    def test_at_least_30_node_types(self) -> None:
        types = NodeRegistry.all_types()
        self.assertGreaterEqual(len(types), 30, sorted(types))

    def test_default_dag_nodes_registered(self) -> None:
        dag = get_default_dag()
        for n in dag.nodes:
            self.assertTrue(NodeRegistry.has(n.type), n.type)

    def test_manual_add_types_non_empty(self) -> None:
        extra = list_manual_add_node_types()
        self.assertIn("generic_llm", extra)
        self.assertIn("world_bible_all", extra)

    def test_meta_has_chinese_display_name(self) -> None:
        meta = NodeRegistry.get_meta("exec_writer")
        self.assertEqual(meta.node_type, "exec_writer")
        self.assertTrue(meta.display_name)


if __name__ == "__main__":
    unittest.main()
