package com.mythosforge.project.dto;

import com.mythosforge.project.Project;

import java.time.Instant;

/** 对外暴露的项目摘要（由 {@link com.mythosforge.project.Project} 映射，不含选定快照字段）。 */
public record ProjectResponse(
        String id,
        String name,
        String language,
        int targetChapters,
        int currentChapter,
        String status,
        String fanSeriesPreset,
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
                p.getFanSeriesPreset(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
