import unittest

from app.services.critic_schema import (
    CRITIC_DIMENSION_IDS,
    dimension_ok,
    validate_critic_report,
)


class CriticSchemaTest(unittest.TestCase):
    def test_valid_minimal(self) -> None:
        report = {
            "pass": True,
            "dimensions": [{"id": "beat_coverage", "ok": True, "note": ""}],
        }
        self.assertEqual(validate_critic_report(report), [])

    def test_invalid_dimension_id(self) -> None:
        report = {
            "pass": True,
            "dimensions": [{"id": "not_a_real_dim", "ok": True}],
        }
        errs = validate_critic_report(report)
        self.assertTrue(any("invalid" in e for e in errs))

    def test_dimension_ok_lookup(self) -> None:
        report = {
            "pass": False,
            "dimensions": [{"id": "beat_coverage", "ok": False, "note": "x"}],
        }
        self.assertFalse(dimension_ok(report, "beat_coverage"))
        self.assertIsNone(dimension_ok(report, "missing_dim"))

    def test_enum_count(self) -> None:
        self.assertIn("beat_coverage", CRITIC_DIMENSION_IDS)


if __name__ == "__main__":
    unittest.main()
