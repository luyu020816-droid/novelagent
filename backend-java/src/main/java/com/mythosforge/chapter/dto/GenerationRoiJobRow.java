package com.mythosforge.chapter.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** 单次任务的 ROI 快照（看板行）。 */
public record GenerationRoiJobRow(
        String jobId,
        int chapterNo,
        String status,
        Long totalTokens,
        Long retryWasteTokens,
        Integer trimmedOptionalCount,
        Integer criticRejectRounds,
        JsonNode llmUsageSummary,
        JsonNode tokenBudgetStatus,
        String chapterVersionId,
        String createdAt
) {}
