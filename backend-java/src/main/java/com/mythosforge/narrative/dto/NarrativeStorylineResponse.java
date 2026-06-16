package com.mythosforge.narrative.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.mythosforge.narrative.NarrativeStorylineEntity;

import java.time.Instant;

/** API 层故事线视图，由 {@link NarrativeStorylineEntity} 映射。 */
public record NarrativeStorylineResponse(
        String id,
        String projectId,
        String storylineKey,
        String title,
        String parentStorylineId,
        String storylineRole,
        String status,
        Integer estStartChapter,
        Integer estEndChapter,
        JsonNode milestonesJson,
        int currentMilestoneIndex,
        Integer lastActiveChapterNo,
        int sortOrder,
        String progressSummary,
        Instant updatedAt
) {
    public static NarrativeStorylineResponse from(NarrativeStorylineEntity e) {
        return new NarrativeStorylineResponse(
                e.getId(),
                e.getProjectId(),
                e.getStorylineKey(),
                e.getTitle(),
                e.getParentStorylineId(),
                e.getStorylineRole() != null ? e.getStorylineRole() : "SUB",
                e.getStatus(),
                e.getEstStartChapter(),
                e.getEstEndChapter(),
                e.getMilestonesJson(),
                e.getCurrentMilestoneIndex(),
                e.getLastActiveChapterNo(),
                e.getSortOrder(),
                e.getProgressSummary(),
                e.getUpdatedAt()
        );
    }
}
