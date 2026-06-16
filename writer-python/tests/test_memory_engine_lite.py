import unittest

from app.services.memory_engine_lite import build_memory_engine_blocks, format_memory_engine_for_pack


class MemoryEngineLiteTest(unittest.TestCase):
    def test_builds_blocks_from_history(self) -> None:
        hist = [
            {
                "chapterNo": 1,
                "summary": {
                    "key_events": ["主角获得密钥"],
                    "character_state": "主角受伤",
                    "pending_foreshadowing": ["密钥来历未明"],
                    "completed_beats": ["首次交锋"],
                },
            }
        ]
        blocks = build_memory_engine_blocks(hist, chapter_no=2, story_contract={"mustRetainFacts": ["师父存活"]})
        self.assertIn("fact_lock", blocks)
        self.assertIn("completed_beats", blocks)
        blob = format_memory_engine_for_pack(blocks)
        self.assertIn("FACT_LOCK", blob)
        self.assertIn("密钥", blob)


if __name__ == "__main__":
    unittest.main()
