"""章后叙事指标启发式（无 pytest 依赖，可用 python -m unittest）。"""

from __future__ import annotations

import unittest

from app.services.narrative_metrics_heuristic import heuristic_metrics_no_llm


class HeuristicMetricsTest(unittest.TestCase):
    def test_tension_in_range(self) -> None:
        m = heuristic_metrics_no_llm("某" * 2000)
        self.assertGreaterEqual(m["tensionScore"], 1.0)
        self.assertLessEqual(m["tensionScore"], 10.0)

    def test_style_in_unit_interval(self) -> None:
        m = heuristic_metrics_no_llm("一句。" * 400)
        self.assertGreaterEqual(m["styleSimilarity"], 0.0)
        self.assertLessEqual(m["styleSimilarity"], 1.0)
        self.assertIn("stub", m["raw"])


if __name__ == "__main__":
    unittest.main()
