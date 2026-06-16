package com.mythosforge.narrative.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;

/** 创建/更新故事线请求体；PATCH 时未传字段保持原值（由 Service 按 null 判断）。 */
public record StorylineUpsertBody(
        @JsonAlias("storylineKey") String storylineKey,
        String title,
        @JsonAlias("parentStorylineId") String parentStorylineId,
        @JsonAlias("storylineRole") String storylineRole,
        String status,
        @JsonAlias("estStartChapter") Integer estStartChapter,
        @JsonAlias("estEndChapter") Integer estEndChapter,
        @JsonAlias("milestonesJson") JsonNode milestonesJson,
        @JsonAlias("currentMilestoneIndex") Integer currentMilestoneIndex,
        @JsonAlias("lastActiveChapterNo") Integer lastActiveChapterNo,
        @JsonAlias("sortOrder") Integer sortOrder,
        @JsonAlias("progressSummary") String progressSummary
) {}
