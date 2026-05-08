package com.mythosforge.project.dto;

import com.mythosforge.project.Project;

import java.time.Instant;

public record ProjectResponse(
        String id,
        String name,
        String language,
        int targetChapters,
        int currentChapter,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
    public static ProjectResponse from(Project p) {
        return new ProjectResponse(
                p.getId(),
                p.getName(),
                p.getLanguage(),
                p.getTargetChapters(),
                p.getCurrentChapter(),
                p.getStatus(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
