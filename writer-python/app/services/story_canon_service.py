"""从 Story Contract JSON 抽取「设定契约」——人设锚点、关系摘要、不可丢约束，供裁剪预算与审查共用。"""

from __future__ import annotations

import json
from typing import Any


def _pick(d: dict[str, Any], *keys: str) -> Any:
    for k in keys:
        if k in d and d[k] is not None:
            return d[k]
    return None


def _str(v: Any, limit: int = 400) -> str:
    if v is None:
        return ""
    s = str(v).strip()
    return s[:limit] + ("…" if len(s) > limit else "")


def _protagonist_contract(raw: dict[str, Any]) -> dict[str, Any]:
    return {
        "name": _str(raw.get("name"), 64),
        "desire": _str(raw.get("desire"), 280),
        "weakness": _str(raw.get("weakness"), 280),
        "secret": _str(raw.get("secret"), 280),
        "growth_arc": _str(_pick(raw, "growthArc", "growth_arc"), 320),
        "golden_finger": _str(_pick(raw, "goldenFinger", "golden_finger"), 320),
    }


def _supporting_contracts(chars: Any) -> list[dict[str, Any]]:
    out: list[dict[str, Any]] = []
    if not isinstance(chars, list):
        return out
    for c in chars[:8]:
        if not isinstance(c, dict):
            continue
        rel = _pick(c, "relationshipToProtagonist", "relationship_to_protagonist")
        hook = _pick(c, "oneLineHook", "one_line_hook")
        out.append(
            {
                "name": _str(c.get("name"), 48),
                "role": _str(c.get("role"), 120),
                "relationship_to_protagonist": _str(rel, 200),
                "one_line_hook": _str(hook, 200),
            }
        )
    return out


def _must_retain_facts(story: dict[str, Any], protagonist: dict[str, Any], supporting: list[dict[str, Any]]) -> list[str]:
    bullets: list[str] = []
    pos = story.get("positioning")
    if isinstance(pos, dict):
        for label, key in (
            ("体裁", "genre"),
            ("核心钩子", "coreHook"),
            ("基调", "tone"),
            ("目标读者", "targetReader"),
        ):
            v = pos.get(key)
            if isinstance(v, str) and v.strip():
                bullets.append(f"{label}：{_str(v, 180)}")

    fv = story.get("firstVolumeDirection") or story.get("first_volume_direction")
    if isinstance(fv, str) and fv.strip():
        bullets.append(f"第一卷走向：{_str(fv, 320)}")

    for camel, snake in (("worldRules", "world_rules"), ("abilityRules", "ability_rules")):
        xs = story.get(camel) or story.get(snake)
        if isinstance(xs, list):
            for x in xs[:10]:
                if isinstance(x, str) and x.strip():
                    bullets.append(f"规则：{_str(x, 160)}")

    pm = story.get("mustRetainFacts") or story.get("must_retain_facts")
    if isinstance(pm, list):
        for x in pm[:12]:
            if isinstance(x, str) and x.strip():
                bullets.append(f"不可丢：{_str(x, 200)}")

    pn = protagonist.get("name") or "主角"
    gf = str(protagonist.get("golden_finger") or "").strip()
    if gf and gf not in ("无", "无。"):
        bullets.append(f"【{pn}】人物优势或处境：{_str(protagonist.get('golden_finger'), 220)}")
    if protagonist.get("secret"):
        bullets.append(f"【{pn}】秘密/隐患（勿随意改写）：{_str(protagonist.get('secret'), 220)}")

    for sc in supporting[:6]:
        nm = sc.get("name") or "配角"
        rel = sc.get("relationship_to_protagonist")
        if rel:
            bullets.append(f"【{nm}】与主角关系：{_str(rel, 180)}")

    seen: set[str] = set()
    uniq: list[str] = []
    for b in bullets:
        if b not in seen:
            seen.add(b)
            uniq.append(b)
        if len(uniq) >= 22:
            break
    return uniq


def build_story_canon(story_contract: dict[str, Any]) -> dict[str, Any]:
    """生成紧凑设定契约；字段尽量稳定，便于 Budget 整段保留、Critic 对照。"""
    if not isinstance(story_contract, dict):
        story_contract = {}

    praw = story_contract.get("protagonist")
    protagonist: dict[str, Any] = {}
    if isinstance(praw, dict):
        protagonist = _protagonist_contract(praw)

    chars_raw = story_contract.get("characters")
    supporting = _supporting_contracts(chars_raw)

    sg_raw = story_contract.get("styleGuide") or story_contract.get("style_guide")
    style_anchor: dict[str, Any] = {}
    if isinstance(sg_raw, dict):
        style_anchor = {
            "narrative_voice": _str(_pick(sg_raw, "narrativeVoice", "narrative_voice"), 200),
            "pacing": _str(sg_raw.get("pacing"), 120),
            "dialogue_ratio": _str(_pick(sg_raw, "dialogueRatio", "dialogue_ratio"), 120),
            "taboo_topics": [
                _str(x, 80) for x in (sg_raw.get("tabooTopics") or sg_raw.get("taboo_topics") or [])[:8]
                if isinstance(x, str) and x.strip()
            ],
        }

    must_retain = _must_retain_facts(story_contract, protagonist, supporting)

    author_gov: dict[str, Any] = {}
    aint = _pick(story_contract, "authorIntent", "author_intent")
    if isinstance(aint, str) and aint.strip():
        author_gov["intent"] = _str(aint, 2000)
    nn = story_contract.get("nonNegotiables") or story_contract.get("non_negotiables")
    if isinstance(nn, list):
        lines = [_str(x, 400) for x in nn[:12] if isinstance(x, str) and x.strip()]
        if lines:
            author_gov["non_negotiables"] = lines
    sfp = story_contract.get("styleFingerprint") or story_contract.get("style_fingerprint")
    if isinstance(sfp, dict):
        gmd = sfp.get("styleGuideMd") or sfp.get("style_guide_md")
        if isinstance(gmd, str) and gmd.strip():
            author_gov["style_reference_guide"] = _str(gmd, 4000)

    canon = {
        "version": 1,
        "protagonist_contract": protagonist,
        "supporting_contracts": supporting,
        "style_anchor": style_anchor,
        "must_retain_facts": must_retain,
        "reader_notes": (
            "以下条目为全书级约束：正文与人设须一致；不得凭空改写已给出的关系与秘密；"
            "支线发挥不得否定 must_retain_facts 中的事实。"
        ),
    }
    if author_gov:
        canon["author_governance"] = author_gov
    return canon


def shrink_story_canon_json(content: str, max_tokens: int, count_tokens: Any) -> tuple[str, int]:
    """在保护预算内压缩 story_canon：先删配角条目，再删 must_retain 尾部。"""
    try:
        obj = json.loads(content)
    except json.JSONDecodeError:
        return content, count_tokens(content)

    if not isinstance(obj, dict):
        return content, count_tokens(content)

    def tok(s: str) -> int:
        return int(count_tokens(s))

    current = json.dumps(obj, ensure_ascii=False)
    guard = 0
    while tok(current) > max_tokens and guard < 24:
        guard += 1
        sup = obj.get("supporting_contracts")
        if isinstance(sup, list) and len(sup) > 1:
            sup.pop()
            obj["supporting_contracts"] = sup
        else:
            facts = obj.get("must_retain_facts")
            if isinstance(facts, list) and facts:
                facts.pop()
                obj["must_retain_facts"] = facts
            else:
                pc = obj.get("protagonist_contract")
                if isinstance(pc, dict):
                    for k in ("growth_arc", "desire", "weakness"):
                        if k in pc and len(str(pc[k])) > 40:
                            pc[k] = _str(pc[k], max(40, len(str(pc[k])) * 2 // 3))
                            break
                    else:
                        break
                else:
                    break
        current = json.dumps(obj, ensure_ascii=False)

    return current, tok(current)
