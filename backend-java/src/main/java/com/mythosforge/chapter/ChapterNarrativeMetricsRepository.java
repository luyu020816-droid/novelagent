package com.mythosforge.chapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChapterNarrativeMetricsRepository extends JpaRepository<ChapterNarrativeMetricsEntity, String> {

    Optional<ChapterNarrativeMetricsEntity> findFirstByProjectIdAndChapterNoOrderByCreatedAtDesc(
            String projectId,
            int chapterNo
    );

    List<ChapterNarrativeMetricsEntity> findTop200ByProjectIdOrderByChapterNoDescCreatedAtDesc(String projectId);
}
