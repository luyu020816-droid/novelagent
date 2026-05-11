package com.mythosforge.story.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** PUT governance：作者意图 + 不可违背条目（JSON 数组）+ 可选风格指纹（写入 story_contract.raw_json.styleFingerprint）。 */
public record StoryGovernanceUpdateRequest(String authorIntent, JsonNode nonNegotiables, String styleGuideMd) {}
