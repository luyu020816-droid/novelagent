package com.mythosforge.project.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;

/** 替换全书叙事域快照（storylines / confluences 等 JSON）。 */
public record NarrativeDomainPatchBody(
        @JsonAlias("narrativeDomainJson") JsonNode narrativeDomainJson
) {}
