"""将丛书预设合并进 StoryContract（初始化后调用）。"""

from __future__ import annotations

from app.schemas.story import StoryContract, StyleGuide
from app.skills.loader import get_series_preset, list_known_preset_ids


def merge_story_contract_with_preset(contract: StoryContract, preset_id: str) -> StoryContract:
    preset = get_series_preset(preset_id)
    if preset is None:
        known = list_known_preset_ids()
        hint = ", ".join(known) if known else "（无：在 app/skills/library 下放 .yaml）"
        raise ValueError(f"未知 Skill id: {preset_id!r}。当前可用: {hint}")

    wr = _uniq_keep_order(list(contract.world_rules) + list(preset["init_world_rules"]))
    ar = _uniq_keep_order(list(contract.ability_rules) + list(preset["init_ability_rules"]))
    fm = _uniq_keep_order(list(contract.forbidden_moves) + list(preset["init_forbidden_moves"]))
    mrf = _uniq_keep_order(list(contract.must_retain_facts) + list(preset["init_must_retain_facts"]))

    sg = contract.style_guide
    suffix = (preset["init_style_voice_suffix"] or "").strip()
    voice = (sg.narrative_voice or "").strip()
    if suffix:
        narrative = voice + ("\n" if voice else "") + f"[丛书口吻] {suffix}"
    else:
        narrative = voice

    taboo = _uniq_keep_order(list(sg.taboo_topics) + list(preset["init_taboo_topics"]))
    new_sg = StyleGuide(
        narrative_voice=narrative[:2000],
        pacing=sg.pacing,
        dialogue_ratio=sg.dialogue_ratio,
        taboo_topics=taboo[:24],
    )

    return contract.model_copy(
        update={
            "world_rules": wr,
            "ability_rules": ar,
            "forbidden_moves": fm,
            "must_retain_facts": mrf,
            "style_guide": new_sg,
        }
    )


def _uniq_keep_order(items: list[str]) -> list[str]:
    seen: set[str] = set()
    out: list[str] = []
    for x in items:
        s = str(x).strip()
        if not s or s in seen:
            continue
        seen.add(s)
        out.append(s)
    return out
