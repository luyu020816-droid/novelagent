package com.mythosforge.chapter.dto;

import com.mythosforge.chapter.SubtextLedgerEntity;

import java.time.Instant;

public record SubtextLedgerItemResponse(
        String id,
        String projectId,
        int chapterNo,
        String characterRef,
        String question,
        String status,
        Integer suggestedResolveChapter,
        Integer consumedAtChapter,
        String importance,
        Instant createdAt
) {
    public static SubtextLedgerItemResponse from(SubtextLedgerEntity e) {
        return new SubtextLedgerItemResponse(
                e.getId(),
                e.getProjectId(),
                e.getChapterNo(),
                e.getCharacterRef(),
                e.getQuestion(),
                e.getStatus(),
                e.getSuggestedResolveChapter(),
                e.getConsumedAtChapter(),
                e.getImportance(),
                e.getCreatedAt()
        );
    }
}
