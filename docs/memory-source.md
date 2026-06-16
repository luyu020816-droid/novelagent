# 记忆数据源：chapter_commits 与 memory_summaries

## 结论（单一读路径）

- **章节生成 / Curator 滚动记忆**：仍以 **`chapter_commits.summary`（JSON）** 与 Java 组装的 `historySummaries` 为准（见 `ChapterCommitRepository`）。行为与升级前一致。
- **`memory_summaries` 表**：在每次 **accept 定稿** 时与 `chapter_commits` **双写**一行，便于 SQL 报表、外部分析或后续「只查一张表」的演进；**不得**在未定稿路径写入。

## 双写字段映射

| `chapter_commits.summary` (JSON) | `memory_summaries` |
|----------------------------------|--------------------|
| `key_events` | `key_events` (JSONB) |
| `character_state` | `character_state_changes` → `{"narrative":"…"}` |
| `pending_foreshadowing` | `new_foreshadowing` (JSONB) |
| 整份 summary | `summary_text`（截断存全文 JSON 字符串） |

## 删除与清理

- 删除已定稿版本时：`ChapterReviewService.deleteVersion` 会删除对应 `chapter_commits` 行，并 **`deleteByProjectIdAndChapterNo`** 清理 `memory_summaries`。
- 删除整个项目：`ProjectService` 中原有 `DELETE FROM memory_summaries WHERE project_id = ?` 保留。
