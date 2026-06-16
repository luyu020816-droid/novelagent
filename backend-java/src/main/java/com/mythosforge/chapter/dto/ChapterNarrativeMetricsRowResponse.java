package com.mythosforge.chapter.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.mythosforge.chapter.ChapterNarrativeMetricsEntity;

import java.time.Instant;

/** 章后叙事指标列表项（供前端表格）。 */
public record ChapterNarrativeMetricsRowResponse(
        String id,
        int chapterNo,
        Double tensionScore,
        Double styleSimilarity,
        String commitId,
        Instant createdAt,
        JsonNode rawJson
) {
    public static ChapterNarrativeMetricsRowResponse from(ChapterNarrativeMetricsEntity e) {
        return new ChapterNarrativeMetricsRowResponse(
                e.getId(),
                e.getChapterNo(),
                e.getTensionScore(),
                e.getStyleSimilarity(),
                e.getCommitId(),
                e.getCreatedAt(),
                e.getRawJson()
        );
    }
}
