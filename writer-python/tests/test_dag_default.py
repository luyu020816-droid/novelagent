"""默认 DAG 结构与上下文阶段节点测试（无 LLM）。"""

from __future__ import annotations

import unittest
from unittest.mock import MagicMock, patch

from app.dag.defaults import get_default_dag
from app.dag.nodes.builtin_nodes import ctx_blueprint, ctx_memory, planning_outline_partition
from app.dag.registry import NodeRegistry


class DagDefaultTest(unittest.TestCase):
    def setUp(self) -> None:
        NodeRegistry.ensure_builtins_loaded()

    def test_default_dag_node_count(self) -> None:
        dag = get_default_dag()
        self.assertGreaterEqual(len(dag.nodes), 20)
        self.assertGreaterEqual(len(dag.edges), 20)

    def test_ctx_blueprint_sets_story_canon(self) -> None:
        state = {
            "story_contract": {"title": "测试", "mustRetainFacts": ["师父存活"]},
            "chapter_contract": {"titleHint": "第一章"},
        }
        out = ctx_blueprint(state)
        pack = out.get("context_pack")
        self.assertIsInstance(pack, dict)
        self.assertIn("story_canon", pack)

    def test_ctx_memory_sets_memory_engine(self) -> None:
        state = {
            "history_summaries": [{"chapterNo": 1, "summary": "开端"}],
            "chapter_no": 2,
            "story_contract": {},
        }
        out = ctx_memory(state)
        pack = out.get("context_pack")
        self.assertIsInstance(pack, dict)

    def test_planning_outline_partition_pads_beats(self) -> None:
        state = {
            "scene_plan": {"beats": [{"beat": "开场", "goal": "入戏"}]},
            "chapter_contract": {},
        }
        out = planning_outline_partition(state)
        beats = out["scene_plan"]["beats"]
        self.assertGreaterEqual(len(beats), 4)

    @patch("app.dag.nodes.builtin_nodes.context_curator_node")
    def test_ctx_assemble_delegates_curator(self, mock_curator: MagicMock) -> None:
        from app.dag.nodes.builtin_nodes import ctx_assemble

        mock_curator.return_value = {"context_pack": {"ok": True}}
        state = {"_dag_gateway": MagicMock()}
        out = ctx_assemble(state)
        mock_curator.assert_called_once()
        self.assertEqual(out["context_pack"]["ok"], True)


if __name__ == "__main__":
    unittest.main()
