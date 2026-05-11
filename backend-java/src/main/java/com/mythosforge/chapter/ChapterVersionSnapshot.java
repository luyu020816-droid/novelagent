package com.mythosforge.chapter;

import com.fasterxml.jackson.databind.JsonNode;

/** 章节工作区轮询：最新一条 chapter_versions。 */
public record ChapterVersionSnapshot(
        String id,
        String projectId,
        int chapterNo,
        int version,
        String status,
        String chapterText,
        String styledText,
        JsonNode tokenBudgetStatus,
        JsonNode llmUsageSummary,
        JsonNode scenePlanJson,
        JsonNode criticReportJson,
        boolean aiCriticPass
) {}
