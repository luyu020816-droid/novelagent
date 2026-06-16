package com.mythosforge.chapter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;

import java.util.List;
import java.util.Optional;

public interface MemorySummaryRepository extends JpaRepository<MemorySummaryEntity, String> {

    List<MemorySummaryEntity> findByProjectIdOrderByChapterNoAsc(String projectId);

    Optional<MemorySummaryEntity> findByProjectIdAndChapterNo(String projectId, int chapterNo);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    void deleteByProjectIdAndChapterNo(String projectId, int chapterNo);
}
