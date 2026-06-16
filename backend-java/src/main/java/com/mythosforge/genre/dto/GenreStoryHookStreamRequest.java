package com.mythosforge.genre.dto;

import jakarta.validation.constraints.NotBlank;

/** 路径 B 一句话故事线：走与偏好相同的 SSE 流水线，附加 {@code storyHook}。可选 {@code uniqueDirection} 走 Writer 单轮唯一锁定（Skill 路径）。 */
public record GenreStoryHookStreamRequest(
        @NotBlank String storyHook,
        String targetPlatform,
        String genderChannel,
        String riskPreference,
        Boolean uniqueDirection
) {
    public GenreStoryHookStreamRequest {
        if (targetPlatform == null || targetPlatform.isBlank()) {
            targetPlatform = "番茄";
        }
        if (genderChannel == null || genderChannel.isBlank()) {
            genderChannel = "男频";
        }
        if (riskPreference == null || riskPreference.isBlank()) {
            riskPreference = "medium";
        }
    }
}
