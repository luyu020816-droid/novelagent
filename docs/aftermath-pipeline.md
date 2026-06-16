# 定稿 Aftermath 流水线

> 借鉴 PlotPilot `ChapterAftermathPipeline`：同步阻塞 + 异步可观测；状态以 PG 字段与日志为准。

## 状态机总览

```text
acceptVersion 触发
  │
  ├─[同步·阻塞] Writer POST /chapters/aftermath
  │     1. 滚动摘要 summary
  │     2. Lore Keeper → Neo4j
  │     3. 伏笔回收 foreshadowResolve
  │     → 事务内写 chapter_commits + memory_summaries（summary 必填）
  │     → vector_sync_status = PENDING
  │
  └─[异步·可重试/可查] 并行任务（失败记 warn，不 rollback 已定稿）
        A. narrativeMetricsService.recordMetricsAsync
        B. runVectorSyncAsync → knowledge/sync → vector_sync_status OK|FAILED
        C. runNarrativePostAcceptAsync → PG 叙事 + Neo4j chapter context + structure sync
        D. runFulfillmentAsync → fulfillment_report_json
```

## 同步路径（accept 阻塞）

Java `ChapterReviewService.acceptVersion`：

`POST /api/writer/chapters/aftermath`

顺序执行：

1. **滚动摘要** → `summary`（`chapter_commits.summary` + `memory_summaries` 双写）
2. **Lore Keeper** → Neo4j 人物/事件/关系/开放伏笔
3. **伏笔回收判定** → `foreshadowResolve` 元数据
4. **aftermath_enrich**（Writer）→ summary 内 `narrative` / `completed_beats` 等扩展字段

日志关键字：`[AftermathPipeline] sync aftermath ok`

旧接口 `POST /api/writer/chapters/summarize` 保留，内部转调 aftermath。

## 异步路径（accept 后后台）

| 步骤 | 服务 | 可观测 |
|------|------|--------|
| 叙事指标 | `NarrativeMetricsService` | `chapter_narrative_metrics` |
| 向量切块 | `POST /api/writer/knowledge/sync` | `chapter_commits.vector_sync_status` / `vector_sync_error` / `vector_sync_at` |
| 叙事落库 | `NarrativePostAcceptService` | PG checkpoint、`narrative_debts` / `narrative_causal_edges` |
| 结构同步 | `narrative-structure-sync` | Neo4j + warn 日志 |
| 章纲履约 | `runFulfillmentAsync` | `chapter_versions.fulfillment_report_json` |

日志关键字：

- `[AftermathPipeline] async vector sync start|ok|failed`
- `[AftermathPipeline] async narrative post-accept ok|failed`
- `[AftermathPipeline] async fulfillment ok|failed`

向量失败且项目开启 `pause_on_vector_sync_failed` 时 Autopilot 可暂停。

## 向量检索阈值

Writer `query_knowledge`：Qdrant cosine，默认 `VECTOR_MIN_SCORE=0.72`，低于阈值不进入 Curator `vector_context`。

## 与下一章记忆

仅 **accepted** 的 `chapter_commits.summary` 进入 `historySummaries`；向量 **OK** 后 Curator 向量 Top3 更稳定。
