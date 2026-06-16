import unittest

from app.services.vector_service import filter_vector_hits


class VectorFilterTest(unittest.TestCase):
    def test_drops_low_scores(self) -> None:
        hits = [
            {"text": "a", "score": 0.9},
            {"text": "b", "score": 0.5},
            {"text": "c", "score": 0.72},
        ]
        kept, dropped = filter_vector_hits(hits, 0.72)
        self.assertEqual(dropped, 1)
        self.assertEqual(len(kept), 2)
        self.assertEqual(kept[0]["text"], "a")
        self.assertEqual(kept[1]["text"], "c")

    def test_keeps_missing_score(self) -> None:
        hits = [{"text": "legacy", "score": None}]
        kept, dropped = filter_vector_hits(hits, 0.72)
        self.assertEqual(dropped, 0)
        self.assertEqual(len(kept), 1)

    def test_disabled_when_min_zero(self) -> None:
        hits = [{"text": "x", "score": 0.1}]
        kept, dropped = filter_vector_hits(hits, 0.0)
        self.assertEqual(dropped, 0)
        self.assertEqual(len(kept), 1)


if __name__ == "__main__":
    unittest.main()
