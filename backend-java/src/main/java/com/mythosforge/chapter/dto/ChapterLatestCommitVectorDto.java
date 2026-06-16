package com.mythosforge.chapter.dto;

import com.fasterxml.jackson.databind.JsonNode;

/** 最新已定稿 commit 的向量同步状态（工作区展示用）。 */
public record ChapterLatestCommitVectorDto(
        String commitId,
        int chapterNo,
        int version,
        String vectorSyncStatus,
        String vectorSyncError,
        String vectorSyncAt,
        int vectorSyncAttempts,
        JsonNode summary
) {}
