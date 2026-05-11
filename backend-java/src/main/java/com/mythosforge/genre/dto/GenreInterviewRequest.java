package com.mythosforge.genre.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/** 路径 B：整段对话历史发往 Writer {@code /genre/interview}。 */
public record GenreInterviewRequest(
        @NotEmpty @Valid List<GenreInterviewChatTurn> chatHistory
) {
}
