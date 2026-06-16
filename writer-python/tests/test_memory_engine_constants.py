import unittest

from app.services.memory_engine_constants import MEMORY_SLOT_TIER
from app.services.token_budget_service import TOKEN_TIER_BY_CATEGORY


class MemoryEngineConstantsTest(unittest.TestCase):
    def test_memory_slots_aligned_with_token_tier(self) -> None:
        for cat, tier in MEMORY_SLOT_TIER.items():
            self.assertEqual(
                TOKEN_TIER_BY_CATEGORY.get(cat),
                tier,
                f"category {cat} tier mismatch",
            )


if __name__ == "__main__":
    unittest.main()
