package com.mythosforge.narrative.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.mythosforge.narrative.NarrativeConfluenceEntity;

import java.time.Instant;

/** API 层汇合点视图，由 {@link NarrativeConfluenceEntity} 映射。 */
public record NarrativeConfluenceResponse(
        String id,
        String projectId,
        String primaryStorylineId,
        String secondaryStorylineId,
        int targetChapter,
        String confluenceType,
        boolean resolved,
        String notes,
        String contextSummary,
        String preRevealHint,
        JsonNode behaviorGuards,
        Instant createdAt
) {
    public static NarrativeConfluenceResponse from(NarrativeConfluenceEntity e) {
        return new NarrativeConfluenceResponse(
                e.getId(),
                e.getProjectId(),
                e.getPrimaryStorylineId(),
                e.getSecondaryStorylineId(),
                e.getTargetChapter(),
                e.getConfluenceType(),
                e.isResolved(),
                e.getNotes(),
                e.getContextSummary(),
                e.getPreRevealHint(),
                e.getBehaviorGuards(),
                e.getCreatedAt()
        );
    }
}
