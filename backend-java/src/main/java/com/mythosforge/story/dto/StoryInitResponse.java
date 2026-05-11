package com.mythosforge.story.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** 初始化完成（或加载选中快照）时返回给前端的一组并列 JSON + 大纲文本。 */
public record StoryInitResponse(
        String novelSeedContractId,
        String storyContractId,
        JsonNode novelSeed,
        JsonNode storyContract,
        String firstVolumeOutline,
        JsonNode chapterContracts,
        String authorIntent,
        JsonNode nonNegotiables
) {
}
