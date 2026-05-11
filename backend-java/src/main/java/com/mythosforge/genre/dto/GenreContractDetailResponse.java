package com.mythosforge.genre.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/** 单条题材方案详情（含完整 raw_json）。 */
public record GenreContractDetailResponse(
        String id,
        String projectId,
        Instant createdAt,
        String source,
        String storyHookText,
        JsonNode rawJson
) {
}
