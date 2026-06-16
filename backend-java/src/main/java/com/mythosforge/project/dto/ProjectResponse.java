package com.mythosforge.project.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.mythosforge.project.Project;

import java.time.Instant;

/** 对外暴露的项目摘要（由 {@link com.mythosforge.project.Project} 映射）。 */
public record ProjectResponse(
        String id,
        String name,
        String language,
        int targetChapters,
        int currentChapter,
        String status,
        String fanSeriesPreset,
        String narrativePhase,
        JsonNode narrativeCheckpointJson,
        String autopilotMode,
        String autoAcceptPolicy,
        int maxAutoChaptersPerRun,
        int autopilotChaptersThisRun,
        boolean autopilotPaused,
        String autopilotPauseReason,
        boolean pauseOnVectorSyncFailed,
        JsonNode narrativeDomainJson,
        JsonNode autopilotLastActionJson,
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
                p.getNarrativePhase(),
                p.getNarrativeCheckpointJson(),
                p.getAutopilotMode(),
                p.getAutoAcceptPolicy(),
                p.getMaxAutoChaptersPerRun(),
                p.getAutopilotChaptersThisRun(),
                p.getAutopilotPaused(),
                p.getAutopilotPauseReason(),
                p.isPauseOnVectorSyncFailed(),
                p.getNarrativeDomainJson(),
                p.getAutopilotLastActionJson(),
                p.getCreatedAt(),
                p.getUpdatedAt()
        );
    }
}
