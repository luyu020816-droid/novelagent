package com.mythosforge.genre.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** 阻塞推荐接口返回：已持久化的合同 ID + Writer 产出的合同 JSON。 */
public record GenreRecommendResponse(String contractId, JsonNode contract) {
}
