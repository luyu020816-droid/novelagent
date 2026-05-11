package com.mythosforge.project.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** {@code POST /api/projects/{id}/fan-series-preset}：置空或省略字段表示清除预设。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FanSeriesPresetBody(String fanSeriesPreset) {}
