package com.mythosforge.narrative.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.databind.JsonNode;

/** 创建汇合点请求体；mergeType 为 confluenceType 的别名。reveal 类型须带 behaviorGuards。 */
public record ConfluenceUpsertBody(
        @JsonAlias("primaryStorylineId") String primaryStorylineId,
        @JsonAlias("secondaryStorylineId") String secondaryStorylineId,
        @JsonAlias("targetChapter") Integer targetChapter,
        @JsonAlias({"confluenceType", "mergeType"}) String confluenceType,
        String notes,
        @JsonAlias("contextSummary") String contextSummary,
        @JsonAlias("preRevealHint") String preRevealHint,
        @JsonAlias("behaviorGuards") JsonNode behaviorGuards
) {}
