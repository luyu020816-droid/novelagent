package com.mythosforge.genre;

import com.fasterxml.jackson.databind.JsonNode;
import com.mythosforge.story.NovelSeedContract;
import com.mythosforge.story.NovelSeedContractRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * 路径 B 采访 {@code complete} 时：在新事务中写入 {@code novel_seed_contracts}，避免外层回滚吃掉快照。
 */
@Service
public class StorylineInterviewPersistence {

    private final NovelSeedContractRepository novelSeedContractRepository;

    public StorylineInterviewPersistence(NovelSeedContractRepository novelSeedContractRepository) {
        this.novelSeedContractRepository = novelSeedContractRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String saveNovelSeedSnapshot(String projectId, JsonNode rawJson) {
        NovelSeedContract row = new NovelSeedContract();
        row.setId(UUID.randomUUID().toString().replace("-", ""));
        row.setProjectId(projectId);
        row.setRawJson(rawJson);
        novelSeedContractRepository.save(row);
        return row.getId();
    }
}
