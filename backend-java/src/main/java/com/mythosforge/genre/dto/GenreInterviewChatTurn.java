package com.mythosforge.genre.dto;

import jakarta.validation.constraints.NotBlank;

/** 采访对话中单轮 {@code role/content}。 */
public record GenreInterviewChatTurn(
        @NotBlank String role,
        @NotBlank String content
) {
}
