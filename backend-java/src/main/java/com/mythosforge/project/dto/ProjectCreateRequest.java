package com.mythosforge.project.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code POST /api/projects} 请求体。 */
public record ProjectCreateRequest(
        @NotBlank String name,
        String language,
        Integer targetChapters,
        String fanSeriesPreset
) {
}
