"""Aftermath 编排单测（mock LLM，不联网）。"""

from __future__ import annotations

import unittest
from unittest.mock import MagicMock, patch

from app.services.aftermath_service import run_chapter_aftermath


class AftermathServiceTest(unittest.TestCase):
    @patch("app.services.aftermath_service.run_foreshadow_resolve_pass")
    @patch("app.services.aftermath_service.ingest_chapter_lore")
    @patch("app.services.aftermath_service.summarize_chapter_text")
    def test_run_chapter_aftermath_returns_contract(
        self,
        mock_summarize: MagicMock,
        mock_ingest: MagicMock,
        mock_resolve: MagicMock,
    ) -> None:
        mock_summarize.return_value = {
            "key_events": ["a"],
            "character_state": "b",
            "pending_foreshadowing": [],
        }
        mock_resolve.return_value = {"resolved_count": 0, "resolved_fs_keys": []}
        gw = MagicMock()

        out = run_chapter_aftermath(
            gw,
            project_id="p1",
            chapter_no=3,
            chapter_text="正文",
        )

        self.assertIn("summary", out)
        self.assertTrue(out["loreIngested"])
        self.assertIsNone(out["loreError"])
        self.assertEqual(out["foreshadowResolve"]["resolved_count"], 0)
        mock_summarize.assert_called_once()
        mock_ingest.assert_called_once()
        mock_resolve.assert_called_once()

    @patch("app.services.aftermath_service.run_foreshadow_resolve_pass")
    @patch("app.services.aftermath_service.ingest_chapter_lore", side_effect=RuntimeError("neo4j down"))
    @patch("app.services.aftermath_service.summarize_chapter_text")
    def test_lore_failure_still_returns_summary(
        self,
        mock_summarize: MagicMock,
        _mock_ingest: MagicMock,
        mock_resolve: MagicMock,
    ) -> None:
        mock_summarize.return_value = {
            "key_events": [],
            "character_state": "x",
            "pending_foreshadowing": [],
        }
        mock_resolve.return_value = {"resolved_count": 0}
        out = run_chapter_aftermath(
            MagicMock(),
            project_id="p1",
            chapter_no=1,
            chapter_text="t",
        )
        self.assertFalse(out["loreIngested"])
        self.assertIn("neo4j", (out["loreError"] or "").lower())
        self.assertIn("summary", out)


if __name__ == "__main__":
    unittest.main()
