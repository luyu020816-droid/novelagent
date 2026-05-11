package com.mythosforge.story.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** 可选参数：阻塞或 SSE 初始化小说流水线。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StoryInitOptionsBody(String wizardNotes) {}
