"""Pydantic 示例：LLM JSON 校验的常见写法（与 app/schemas/chapter.py 同风格）。

运行（在 writer-python 目录）:
  python examples/pydantic_demo.py
"""

from __future__ import annotations

import json
from typing import Any

from pydantic import BaseModel, ConfigDict, Field, ValidationError, field_validator, model_validator


def _to_camel(snake: str) -> str:
    parts = snake.split("_")
    return parts[0] + "".join(p.title() for p in parts[1:])


_CONFIG = ConfigDict(
    alias_generator=_to_camel,
    populate_by_name=True,  # chapterNo 和 chapter_no 都能解析
    serialize_by_alias=True,  # 出站 JSON 用 camelCase，对齐 Java
    extra="ignore",  # LLM 多塞字段时不报错，直接丢掉
)


class BeatItem(BaseModel):
    """场景节拍：scene_director / ghostwriter_beats 会用类似结构。"""

    model_config = _CONFIG

    order: int = Field(ge=1, le=20, description="节拍序号，从 1 开始")
    goal: str = Field(min_length=1, description="本节拍要完成的叙事目标")
    emotion: str = Field(default="", description="情绪基调，可为空")


class ScenePlanDemo(BaseModel):
    """示例：Planner 产出的 scene_plan（简化版）。"""

    model_config = _CONFIG

    chapter_no: int = Field(ge=1, le=200)
    title_hint: str = Field(min_length=1)
    beats: list[BeatItem] = Field(default_factory=list, description="至少 4 个节拍才过离线 eval")

    @field_validator("title_hint")
    @classmethod
    def strip_title(cls, v: str) -> str:
        s = (v or "").strip()
        if not s:
            raise ValueError("title_hint 不能为空")
        return s

    @model_validator(mode="after")
    def beats_count_and_order(self) -> ScenePlanDemo:
        if len(self.beats) < 4:
            raise ValueError(f"beats 至少 4 个，当前 {len(self.beats)}")
        orders = [b.order for b in self.beats]
        if sorted(orders) != list(range(1, len(orders) + 1)):
            raise ValueError(f"beat order 须从 1 连续递增，got {orders}")
        return self


def parse_llm_json(raw: str) -> ScenePlanDemo:
    """项目里真实路径：json.loads → model_validate（见 app/services/json_repair.py）。"""
    data: Any = json.loads(raw)
    return ScenePlanDemo.model_validate(data)


def main() -> None:
    # 1) 给 Prompt 用的 JSON Schema（Showrunner 等 Agent 也是 model_json_schema()）
    schema = ScenePlanDemo.model_json_schema()
    print("=== model_json_schema()（节选）===")
    print(json.dumps(schema.get("properties", {}), ensure_ascii=False, indent=2)[:800])
    print()

    # 2) 合法输入：camelCase（Java/前端习惯）
    ok_json = """
    {
      "chapterNo": 1,
      "titleHint": "穿越即 BUG",
      "beats": [
        {"order": 1, "goal": "主角醒来发现穿越", "emotion": "错愕"},
        {"order": 2, "goal": "确认世界规则与身份", "emotion": "警惕"},
        {"order": 3, "goal": "第一次冲突", "emotion": "紧张"},
        {"order": 4, "goal": "章末钩子", "emotion": "悬念"}
      ]
    }
    """
    model = parse_llm_json(ok_json)
    print("=== 校验通过 ===")
    print(model.model_dump(by_alias=True))
    print()

    # 3) 非法输入：beats 只有 2 个 → model_validator 报错
    bad_json = """
    {
      "chapterNo": 1,
      "titleHint": "测试",
      "beats": [
        {"order": 1, "goal": "a"},
        {"order": 2, "goal": "b"}
      ]
    }
    """
    print("=== 校验失败（预期）===")
    try:
        parse_llm_json(bad_json)
    except ValidationError as e:
        print(e.errors()[0]["msg"])


if __name__ == "__main__":
    main()
