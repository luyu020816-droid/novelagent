import unittest

from app.services.story_canon_consistency import (
    check_must_retain_facts_present,
    check_story_canon_shrink_safe,
)
from app.services.story_canon_service import build_story_canon


class StoryCanonConsistencyTest(unittest.TestCase):
    def test_must_retain_present(self) -> None:
        sc = {"mustRetainFacts": ["师父存活"]}
        ctx = "本章确认师父存活，仍在山上。"
        self.assertEqual(check_must_retain_facts_present(sc, context_text=ctx), [])

    def test_must_retain_missing(self) -> None:
        sc = {"mustRetainFacts": ["师父存活"]}
        self.assertTrue(check_must_retain_facts_present(sc, context_text="无关正文"))

    def test_canon_has_governance(self) -> None:
        canon = build_story_canon({"authorIntent": "偏热血", "nonNegotiables": ["不写系统面板"]})
        self.assertEqual(check_story_canon_shrink_safe(canon), [])


if __name__ == "__main__":
    unittest.main()
