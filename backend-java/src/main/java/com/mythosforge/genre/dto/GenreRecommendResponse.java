package com.mythosforge.genre.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record GenreRecommendResponse(String contractId, JsonNode contract) {
}
