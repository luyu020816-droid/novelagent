"""审阅规则层单测。"""

from __future__ import annotations

import unittest

from app.dag.review_helpers import review_storyline, review_timeline


class ReviewHelpersTest(unittest.TestCase):
    def test_timeline_flags_future_summary(self) -> None:
        state = {
            "chapter_no": 3,
            "history_summaries": [{"chapterNo": 5, "summary": {"oneLiner": "未来章"}}],
        }
        rep = review_timeline(state)
        self.assertFalse(rep["ok"])
        self.assertTrue(rep["issues"])

    def test_storyline_missing_hint(self) -> None:
        state = {
            "chapter_text": "今天天气不错",
            "chapter_obligations": {
                "narrativePromptLines": ["师徒必须在山门对决"],
                "activeStorylines": [{"title": "复仇线"}],
            },
        }
        rep = review_storyline(state)
        self.assertFalse(rep["ok"])
        self.assertTrue(rep["missing_hints"])


if __name__ == "__main__":
    unittest.main()
