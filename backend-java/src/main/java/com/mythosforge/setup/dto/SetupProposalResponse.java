package com.mythosforge.setup.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record SetupProposalResponse(
        String id,
        String projectId,
        String stage,
        String status,
        JsonNode payload,
        String assistantReply,
        int baseVersion,
        Instant createdAt
) {}
