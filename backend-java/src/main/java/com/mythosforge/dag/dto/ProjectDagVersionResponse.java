package com.mythosforge.dag.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.mythosforge.dag.ProjectDagVersionEntity;

import java.time.Instant;

public record ProjectDagVersionResponse(
        String id,
        String projectId,
        int versionNo,
        String label,
        JsonNode dag,
        boolean active,
        Instant createdAt,
        boolean usingSystemDefault
) {
    public static ProjectDagVersionResponse from(ProjectDagVersionEntity e, String projectId) {
        boolean systemDefault = e.getId() == null;
        return new ProjectDagVersionResponse(
                e.getId(),
                projectId,
                e.getVersionNo(),
                e.getLabel(),
                e.getDagJson(),
                e.isActive(),
                e.getCreatedAt(),
                systemDefault
        );
    }
}
