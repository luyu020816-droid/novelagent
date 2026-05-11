package com.mythosforge.genre.dto;

import jakarta.validation.constraints.NotBlank;

/** {@code PUT .../genre/selected-contract} 请求体。 */
public record SelectGenreContractRequest(@NotBlank String genreContractId) {
}
