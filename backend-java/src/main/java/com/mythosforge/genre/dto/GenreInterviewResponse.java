package com.mythosforge.genre.dto;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * 路径 B 互动采访：asking 时仅 {@link #replyToUser} 有效；
 * complete 时填充摘要、设定，并在落库后给出 {@link #persistedNovelSeedContractId}。
 */
public record GenreInterviewResponse(
        String status,
        String replyToUser,
        String finalSummary,
        JsonNode coreSettings,
        String persistedNovelSeedContractId
) {
}
