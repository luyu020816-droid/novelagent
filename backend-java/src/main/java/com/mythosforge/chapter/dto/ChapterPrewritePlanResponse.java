package com.mythosforge.chapter.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** 本章动笔前计划 + 可选的上一章已定稿摘要（JSON）。 */
public record ChapterPrewritePlanResponse(
        int chapterNo,
        JsonNode prevChapterCommitSummary,
        String planSummary,
        boolean confirmed
) {}
