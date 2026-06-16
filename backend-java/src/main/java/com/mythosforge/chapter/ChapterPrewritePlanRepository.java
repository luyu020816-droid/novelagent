package com.mythosforge.chapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChapterPrewritePlanRepository extends JpaRepository<ChapterPrewritePlanEntity, String> {

    Optional<ChapterPrewritePlanEntity> findByProjectIdAndStoryContractIdAndChapterNo(
            String projectId,
            String storyContractId,
            int chapterNo
    );

    void deleteByStoryContractId(String storyContractId);
}
