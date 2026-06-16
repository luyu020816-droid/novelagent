"""规则审阅并入网关单测。"""

from __future__ import annotations

import unittest

from app.dag.gateway_helpers import apply_rule_review_gates


class GatewayHelpersTest(unittest.TestCase):
    def test_consistency_fail_blocks_pass(self) -> None:
        state = {
            "critic_report": {"pass": True, "dimensions": [{"id": "canon_facts", "ok": True, "note": ""}]},
            "_review_consistency": {"ok": False, "violations": ["师父存活设定被违背"]},
        }
        out = apply_rule_review_gates(state)
        report = out["critic_report"]
        self.assertFalse(report["pass"])
        canon = next(d for d in report["dimensions"] if d["id"] == "canon_facts")
        self.assertFalse(canon["ok"])

    def test_timeline_fail_adds_dimension(self) -> None:
        state = {
            "critic_report": {"pass": True, "dimensions": []},
            "_review_timeline": {"ok": False, "issues": ["未来章摘要"]},
        }
        out = apply_rule_review_gates(state)
        self.assertFalse(out["critic_report"]["pass"])
        ids = {d["id"] for d in out["critic_report"]["dimensions"]}
        self.assertIn("timeline_review", ids)

    def test_all_ok_keeps_pass(self) -> None:
        state = {
            "critic_report": {"pass": True, "dimensions": []},
            "_review_storyline": {"ok": True},
            "_review_consistency": {"ok": True},
            "_val_foreshadow": {"ok": True},
        }
        out = apply_rule_review_gates(state)
        self.assertTrue(out["critic_report"]["pass"])


if __name__ == "__main__":
    unittest.main()
