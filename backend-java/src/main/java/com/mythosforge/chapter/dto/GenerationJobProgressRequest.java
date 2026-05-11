package com.mythosforge.chapter.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record GenerationJobProgressRequest(
        String currentStage,
        Integer progressPct,
        String node
) {}
