package com.mythosforge.chapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GenerationJobRepository extends JpaRepository<GenerationJobEntity, String> {

    Optional<GenerationJobEntity> findTopByProjectIdAndChapterNoOrderByCreatedAtDesc(String projectId, int chapterNo);

    List<GenerationJobEntity> findByProjectIdOrderByCreatedAtDesc(String projectId);
}
