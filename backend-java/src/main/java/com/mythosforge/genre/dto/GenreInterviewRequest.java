package com.mythosforge.genre.dto;

import jakarta.validation.Valid;

import java.util.List;

/** 路径 B：整段对话历史发往 Writer {@code /genre/interview}；可选 {@code writerSkillId} 注入丛书 Skill。 */
public record GenreInterviewRequest(
        @Valid List<GenreInterviewChatTurn> chatHistory,
        String writerSkillId
) {
}
