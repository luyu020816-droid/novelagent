package com.mythosforge.chapter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SubtextLedgerRepository extends JpaRepository<SubtextLedgerEntity, String> {

    List<SubtextLedgerEntity> findByProjectIdAndStatusOrderByChapterNoAsc(String projectId, String status);

    List<SubtextLedgerEntity> findByProjectIdOrderByChapterNoAscCreatedAtAsc(String projectId);

    List<SubtextLedgerEntity> findByProjectIdAndStatusAndSuggestedResolveChapterOrderByChapterNoAsc(
            String projectId,
            String status,
            int suggestedResolveChapter
    );
}
