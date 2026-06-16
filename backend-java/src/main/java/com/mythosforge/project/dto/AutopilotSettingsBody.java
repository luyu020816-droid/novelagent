package com.mythosforge.project.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;

/** PATCH 自动驾驶与闸门（字段均可选，未传则不改）。 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AutopilotSettingsBody(
        @JsonAlias("autopilotMode") String autopilotMode,
        @JsonAlias("autoAcceptPolicy") String autoAcceptPolicy,
        @JsonAlias("maxAutoChaptersPerRun") Integer maxAutoChaptersPerRun,
        @JsonAlias("pauseOnVectorSyncFailed") Boolean pauseOnVectorSyncFailed
) {}
