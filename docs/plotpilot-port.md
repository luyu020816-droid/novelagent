# PlotPilot 高价值能力移植说明

已在 MythosForge Writer 落地（不改 Java 单体、不引入 DAG 画布）：

| 能力 | 实现位置 |
|------|----------|
| 节拍分段成稿 | `ghostwriter_beats.py` + `scene_plan.beats`≥2 时触发 |
| MemoryEngine-lite | `memory_engine_lite.py` → Curator `memory_engine` |
| Anti-AI 协议 | `prompts/anti_ai_protocol_v1.md` → Ghostwriter/Curator |
| CPMS-lite | `prompts/cpms_manifest.json` + `prompt_cpms.load_node_prompt` |
| T0–T3 裁剪 | `token_budget_service.TOKEN_TIER_BY_CATEGORY` |
| Aftermath 加厚 | `aftermath_enrich.py` + summary `narrative`/`completed_beats` |
| Critic 维度 | `beat_coverage`, `anti_ai_prose` |
| 评测 | `eval_critic_dimensions.py` + golden `fixtures/eval/golden` + `eval_harness_report.py` + `eval_chapter_qa.py` |
| CI / QA | `.github/workflows/ci.yml` + `docs/qa-runbook.md` |
| CPMS PG | `V21__cpms_prompt_versions.sql` + `seed_cpms_prompts.py` + `llm_usage_log.prompt_version` |

Java 侧已有：`narrative_debts`、`narrative_causal_edges`、`NarrativePostAcceptService`（读 summary.key_events / narrative）。

未移植：Tauri 桌面 **DAG 拖拽画布**（拓扑已 JSON 化 + API）、Chroma 替换 Qdrant、CPMS 运营 UI。

**已移植 DAG**：`app/dag/` — 36 种节点类型、默认 23 节点流水线、编译为 LangGraph；见 [dag-pipeline.md](dag-pipeline.md)。
