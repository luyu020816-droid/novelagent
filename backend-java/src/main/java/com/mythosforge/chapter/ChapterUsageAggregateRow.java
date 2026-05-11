package com.mythosforge.chapter;

/** {@code llm_usage_log} 按章聚合：历史累计调用次数与 token（COALESCE 实际/估算）。 */
public record ChapterUsageAggregateRow(int chapterNo, long callCount, long totalTokens) {}
