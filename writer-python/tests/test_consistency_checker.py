"""设定一致性检查单测。"""

from __future__ import annotations

import unittest

from app.services.consistency_checker import run_consistency_check


class ConsistencyCheckerTest(unittest.TestCase):
    def test_must_retain_missing_fails(self) -> None:
        state = {
            "chapter_text": "主角独自在山上修行。",
            "story_contract": {
                "mustRetainFacts": ["师父仍存活且隐居后山"],
            },
            "context_pack": {},
        }
        rep = run_consistency_check(state)
        self.assertFalse(rep["ok"])
        self.assertTrue(rep["violations"])

    def test_canon_fact_present_passes(self) -> None:
        state = {
            "chapter_text": "师父仍存活且隐居后山，主角前来请安。",
            "story_contract": {
                "mustRetainFacts": ["师父仍存活且隐居后山"],
            },
            "context_pack": {"story_canon": {"must_retain_facts": ["师父仍存活且隐居后山"]}},
        }
        rep = run_consistency_check(state)
        self.assertTrue(rep["ok"])


if __name__ == "__main__":
    unittest.main()
