# 手测清单（P1 验收闭环）

本地需：Docker（Postgres / Qdrant / Neo4j）、Java :8080、Writer :8000、前端 :5173，`writer-python/.env` 含 `OPENAI_API_KEY` 与（可选）`EMBEDDING_*`、`VECTOR_MIN_SCORE=0.72`。

## 0. 烟囱（可选）

```powershell
cd novel
.\scripts\smoke_stack.ps1
```

离线单测应全绿；服务未起时健康检查 SKIP 正常。

## 1. Setup 向导

- [ ] 打开 `/projects/{id}/setup`
- [ ] 「按当前进度一键生成草案」→ 自动出现**待确认**面板（题材/故事/结构按进度）
- [ ] 确认题材 → 生成故事草案 → 预览含 **「章契约 20 章」** 与走向摘要
- [ ] 确认故事 → PG 有 `chapter_contracts`（第 1 章工作台可见章纲）
- [ ] 确认故事结构 → `readyToWrite` 为真

## 2. 写章与审查

- [ ] 第 1 章工作台生成（SSE 或异步 Job）
- [ ] Critic 未过时可重试；敏感词 block 时 **fail 重生**（非打星替换）
- [ ] 人工 **接受定稿**

## 3. 定稿后链路

- [ ] Java 调 Writer `aftermath` 成功（日志无 502）
- [ ] `chapter_commits.summary` 有 JSON
- [ ] `memory_summaries` 同行存在
- [ ] 异步向量：`vector_sync_status` 最终 OK（需 Embedding + Qdrant）
- [ ] Neo4j 有本章 lore（`lore_graph_enabled=true`）

## 4. 第 2 章记忆

- [ ] 生成第 2 章时请求体含第 1 章 `historySummaries`
- [ ] Writer 日志 `[VectorSearch]` 有 hits 与 scores（已 accept 且向量同步成功时）
- [ ] 低于 `VECTOR_MIN_SCORE` 的块被过滤（日志 `filtered N hits`）

## 6. DAG 画布（长篇小说流水线）

- [ ] `/projects/{id}/workflow` 加载默认或 active DAG
- [ ] 禁用节点、修改 gw_circuit 出边条件后保存通过校验
- [ ] 「恢复系统默认」→ 校验并保存
- [ ] 生成章节时 Java payload 含 `dagDefinition`

## 7. 回归

- [ ] 双书页 `/books/dual` 嵌入的是 **SetupStudio**（非旧 init 工作台）
- [ ] `/story/init` 重定向到 `/setup`

---

全部勾选 ≈ **P0+P1 业务闭环**；未勾选项对照 [plotpilot-gap.md](plotpilot-gap.md) 是否为已知 P2 能力。
