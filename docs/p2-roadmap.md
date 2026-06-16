# P2 路线图（PlotPilot 差距追赶）

**高价值移植（节拍/MemoryEngine/Anti-AI/CPMS-lite/Aftermath enrich）已完成**，见 [plotpilot-port.md](plotpilot-port.md)。

在 P0（Setup + 20 章契约）与 P1（向量阈值、aftermath、smoke、Setup UI）完成后建议顺序：

| 阶段 | 交付 | 对齐 PlotPilot |
|------|------|----------------|
| **P2.1** | Planner 输出 `beats[]`，Ghostwriter 按 beat 串写或 SSE 分段 | `planning_beat_sheet` / `exec_beat` |
| **P2.2** | Aftermath 扩展：因果边/叙事债写入 PG 或 Neo4j；可选文风分 | `ChapterAftermathPipeline` 后半 |
| **P2.3** | Curator 注入 FACT_LOCK / 已完成节拍（从 summary 规则抽取） | MemoryEngine 简化版 |
| **P2.4** | 固定章纲评测集 + CI 跑 `smoke_stack` + critic 维度统计 | PP eval 体系 |
| **P2.5** | 提示词版本表（PG）+ 按项目绑定 | CPMS -lite |

**不做或后置**：可视化 DAG 编辑器、Tauri 桌面端、SQLite 单体合并。

实现 P2.1 时优先改 `planner_node` / `ghostwriter_node` 与 `ChapterGraphState`，避免先动 Java 契约。
