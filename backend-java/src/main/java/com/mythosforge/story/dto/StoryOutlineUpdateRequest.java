package com.mythosforge.story.dto;

/** 覆盖当前选中快照的第一卷大纲正文（列 {@code story_contracts.first_volume_outline}）。 */
public record StoryOutlineUpdateRequest(String firstVolumeOutline) {}
