package com.mythosforge.genre.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record GenreRecommendRequest(
        @NotBlank String targetPlatform,
        @NotBlank String genderChannel,
        List<String> preferredGenres,
        List<String> avoid,
        List<String> writingStrength,
        String riskPreference
) {
    public GenreRecommendRequest {
        if (preferredGenres == null) {
            preferredGenres = List.of();
        }
        if (avoid == null) {
            avoid = List.of();
        }
        if (writingStrength == null) {
            writingStrength = List.of();
        }
        if (riskPreference == null || riskPreference.isBlank()) {
            riskPreference = "medium";
        }
    }
}
