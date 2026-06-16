import unittest

from app.services.memory_engine_constants import MIN_SCENE_PLAN_BEATS
from app.services.scene_plan_beats import enforce_scene_plan_beats, normalize_beats


class ScenePlanBeatsTest(unittest.TestCase):
    def test_pad_to_min_beats(self) -> None:
        plan = {"beats": [{"beat": "开场", "goal": "入戏"}]}
        cc = {"mustCover": ["师徒对峙", "埋设玉佩", "章末钩子"]}
        out = enforce_scene_plan_beats(plan, cc)
        beats = normalize_beats(out)
        self.assertGreaterEqual(len(beats), MIN_SCENE_PLAN_BEATS)

    def test_pad_without_must_cover(self) -> None:
        plan = {"beats": [{"beat": "开场", "goal": "入戏"}]}
        out = enforce_scene_plan_beats(plan, {})
        beats = normalize_beats(out)
        self.assertGreaterEqual(len(beats), MIN_SCENE_PLAN_BEATS)

    def test_normalize_string_beats(self) -> None:
        plan = {"beats": ["甲", "乙", "丙", "丁"]}
        out = normalize_beats(plan)
        self.assertEqual(len(out), 4)


if __name__ == "__main__":
    unittest.main()
