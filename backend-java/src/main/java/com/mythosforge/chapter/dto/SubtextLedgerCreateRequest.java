package com.mythosforge.chapter.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** POST 子文本账本条目。 */
public record SubtextLedgerCreateRequest(
        @NotNull @JsonAlias("chapterNo") Integer chapterNo,
        @JsonAlias("characterRef") String characterRef,
        @NotBlank @JsonAlias("question") String question,
        @JsonAlias("suggestedResolveChapter") Integer suggestedResolveChapter,
        @JsonAlias("importance") String importance
) {}
