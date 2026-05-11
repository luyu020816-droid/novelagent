package com.mythosforge.project.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.mythosforge.genre.GenreDecisionContract;

import java.time.Instant;

/** 工作区里一条题材方案的列表摘要（非完整 raw_json）。 */
public record GenreContractListItem(
        String id,
        Instant createdAt,
        String source,
        String primaryGenreLabel,
        String storyHookPreview
) {
    public static GenreContractListItem from(GenreDecisionContract row) {
        String label = "";
        JsonNode sel = row.getSelectedDirection();
        if (sel != null && !sel.isNull()) {
            JsonNode g = sel.get("genre");
            if (g != null && g.isTextual()) {
                label = g.asText();
            }
        }
        String hook = row.getStoryHookText();
        String preview = "";
        if (hook != null && !hook.isBlank()) {
            preview = hook.length() > 120 ? hook.substring(0, 120) + "…" : hook;
        }
        return new GenreContractListItem(
                row.getId(),
                row.getCreatedAt(),
                row.getSource(),
                label,
                preview
        );
    }
}
