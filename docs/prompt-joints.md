# Writer 提示接点目录（Prompt joints）

每条记录对应一次 `LLMGateway.chat_completion` 的 `node_name` / 业务场景，便于换 Skill、对表排错。

| 接点 / `node_name` | 模块 | 说明 |
|--------------------|------|------|
| `context_curator` | `context_curator_node` | 组装 context_pack、Neo4j/Qdrant 召回 |
| `planner` | `planner_node` | 章内场景规划 |
| `ghostwriter` | `ghostwriter_node` | 正文生成 |
| `critic` | `critic_node` | 审查 JSON + 疲劳扫描 |
| `stylist` | `stylist_node` | 风格层 |
| `summarize` | `chapter_summarize.summarize_chapter_text` | 定稿后滚动摘要 |
| `lore_keeper` | `lore_keeper_service.extract_lore_struct` | 定稿后图谱抽取 |
| `foreshadow_resolve` | `lore_keeper_service.run_foreshadow_resolve_pass` | 定稿后伏笔回收判定 |
| `budget` | （无 LLM） | Token 预算节点 |
| `consistency_spotcheck` | `api/audit.py` | 可选两章摘录一致性抽查 |

丛书级 YAML 见 [writer-python/app/skills/library/README.md](../writer-python/app/skills/library/README.md)。
