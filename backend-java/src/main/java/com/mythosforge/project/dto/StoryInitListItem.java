package com.mythosforge.project.dto;

import com.mythosforge.story.StoryContractEntity;

import java.time.Instant;

/** 工作区里一条「初始化快照」（Story Contract）的列表项。 */
public record StoryInitListItem(
        String storyContractId,
        String novelSeedContractId,
        Instant createdAt
) {
    public static StoryInitListItem from(StoryContractEntity row) {
        return new StoryInitListItem(
                row.getId(),
                row.getNovelSeedContractId(),
                row.getCreatedAt()
        );
    }
}
