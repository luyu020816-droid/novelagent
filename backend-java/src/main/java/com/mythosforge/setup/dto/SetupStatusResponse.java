package com.mythosforge.setup.dto;

import com.fasterxml.jackson.databind.JsonNode;

public record SetupStatusResponse(
        String setupMode,
        String currentStage,
        boolean genreConfirmed,
        boolean storyConfirmed,
        boolean narrativeConfirmed,
        boolean readyToWrite,
        String nextActionHint,
        String pendingGenreProposalId,
        String pendingStoryProposalId,
        String pendingNarrativeProposalId,
        JsonNode confirmedGenrePreview,
        JsonNode confirmedStoryPreview,
        JsonNode confirmedNarrativePreview,
        int storylineCount,
        /** 已有章节版本或定稿，表示创作已开始 */
        boolean writingStarted,
        /** 创作中锁定 Setup：不可重新初始化题材/故事契约 */
        boolean setupLocked,
        int acceptedChapterCount,
        int draftVersionCount,
        /** 建议继续写作的章节号 */
        int resumeChapterNo
) {}
