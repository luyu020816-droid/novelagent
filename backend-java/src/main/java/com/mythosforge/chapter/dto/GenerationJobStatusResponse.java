package com.mythosforge.chapter.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** 前端轮询异步任务状态。 */
public record GenerationJobStatusResponse(
        String jobId,
        String projectId,
        int chapterNo,
        String status,
        String currentStage,
        int progressPct,
        String errorMessage,
        String chapterVersionId,
        Long totalTokens,
        Long retryWasteTokens,
        Integer trimmedOptionalCount,
        Integer criticRejectRounds,
        JsonNode llmUsageSummary,
        JsonNode tokenBudgetStatus,
        String createdAt,
        String updatedAt
) {}
