"""LangGraph 章节生成全局状态（Pydantic）；与 Java / SSE artifact 对齐字段语义。"""

from __future__ import annotations

from typing import Any, TypedDict

from pydantic import BaseModel, Field


class ChapterGraphState(TypedDict, total=False):
    """LangGraph 状态 schema：按键合并更新。

    不可用裸 ``dict`` 作为 ``StateGraph`` 的 schema——LangGraph 会把它当成单个 ``__root__``
    通道，节点返回值会整份替换状态，导致 ``decision_gate`` 之后丢失 ``chapter_text`` 等字段。
    """

    project_id: str
    chapter_no: int
    story_contract: dict[str, Any]
    chapter_contract: dict[str, Any]
    history_summaries: list[dict[str, Any]]
    recent_summaries: list[dict[str, Any]]
    context_pack: dict[str, Any]
    context_pack_items: list[dict[str, Any]]
    token_budget_status: dict[str, Any]
    llm_usage_summary: dict[str, Any]
    scene_plan: dict[str, Any] | None
    chapter_text: str | None
    styled_text: str | None
    critic_report: dict[str, Any] | None
    accepted: bool
    rejected: bool
    retry_count: int
    user_rewrite_notes: str
    rewrite_mode: str
    confirmed_chapter_plan_summary: str
    chapter_obligations: dict[str, Any] | None


class ChapterGenerationState(BaseModel):
    """单章生成 Graph 的权威状态快照（可由 Python 最终 artifact 回传 Java）。"""

    model_config = {"extra": "ignore"}

    project_id: str = Field(description="项目 ID")
    chapter_no: int = Field(description="章节号", ge=1)
    story_contract: dict[str, Any] = Field(description="当前初始化快照下的 Story Contract JSON")
    chapter_contract: dict[str, Any] = Field(description="本章 Chapter Contract JSON")
    history_summaries: list[dict[str, Any]] = Field(
        default_factory=list,
        description="前几章 accepted commit 的滚动摘要（chapterNo + summary）；Curator 注入 context_pack",
    )
    recent_summaries: list[dict[str, Any]] = Field(
        default_factory=list,
        description="兼容旧字段；若 history_summaries 为空则回退使用",
    )
    context_pack: dict[str, Any] = Field(
        default_factory=dict,
        description="上下文包：须含最近摘要等，供 Planner / Ghostwriter 使用",
    )
    context_pack_items: list[dict[str, Any]] = Field(
        default_factory=list,
        description="Day 11：可裁剪上下文条目（Budget 前后均可能更新）",
    )
    token_budget_status: dict[str, Any] = Field(
        default_factory=dict,
        description="Day 11：Token 预算裁剪统计（Java/SSE 可展示）",
    )
    llm_usage_summary: dict[str, Any] = Field(
        default_factory=dict,
        description="本章单次生成：LLM 调用次数与各节点 token 汇总（含 COALESCE 实际/估算）",
    )
    scene_plan: dict[str, Any] | None = Field(default=None, description="Scene Plan（场景规划）")
    chapter_text: str | None = Field(default=None, description="章节正文")
    styled_text: str | None = Field(default=None, description="Critic 通过后文笔润色稿（定稿优先）")
    critic_report: dict[str, Any] | None = Field(default=None, description="Critic 审查报告 JSON")
    accepted: bool = Field(default=False, description="是否通过闸门（AI Critic）")
    rejected: bool = Field(default=False, description="是否被拒绝")
    retry_count: int = Field(default=0, description="Critic 失败后已触发重写次数（不含首轮）")
    user_rewrite_notes: str = Field(default="", description="用户打回时的补充意见（首轮写入 Ghostwriter）")
    rewrite_mode: str = Field(default="plot", description="plot | anti_ai")
    confirmed_chapter_plan_summary: str = Field(
        default="",
        description="作者已确认的本章动笔前摘要（由 Curator 注入 context_pack）",
    )
    chapter_obligations: dict[str, Any] | None = Field(
        default=None,
        description="Java PG 真源「本章任务单」；Curator 合并入 context_pack.narrative_obligations",
    )
