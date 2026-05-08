package com.mythosforge.project.dto;

import jakarta.validation.constraints.NotBlank;

public record ProjectCreateRequest(
        @NotBlank String name,
        String language,
        Integer targetChapters
) {
}
