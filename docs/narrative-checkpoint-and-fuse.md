# 叙事 Checkpoint 与生成熔断

## `projects.narrative_checkpoint_json`

在每次 **accept 定稿** 成功后由 `ChapterReviewService` 更新，典型字段：

- `lastAcceptedChapterNo`：最近已定稿章节号  
- `lastCommitId`：对应 `chapter_commits.id`  
- `updatedAt`：ISO-8601 时间  

可用于全书进度页、断点恢复或外部编排；**不等同于**「自动生成下一章」开关。

## `projects.narrative_phase`

可选字符串占位（默认在首次 accept 时若为空则置为 `ACTIVE`）。后续可扩展为 `PAUSED_ERROR` 等与 UI/队列联动。

## 生成任务熔断（`GenerationJobService`）

- 配置：`mythosforge.generation.consecutive-failed-jobs-to-block-enqueue`（默认 `5`）。  
- 含义：同一 `projectId + chapterNo` 下，按创建时间**从新到旧**连续 `FAILED` 的条数达到阈值时，**拒绝新的异步入队**（`409 CONFLICT`），避免无限重试刷爆 Writer。  
- 设为 `0` 关闭该检查。  
- 成功后链上最近的非 `FAILED` 任务会打断连续计数。
