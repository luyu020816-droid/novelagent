package com.mythosforge.narrative;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface NarrativeDebtRepository extends JpaRepository<NarrativeDebtEntity, String> {

    List<NarrativeDebtEntity> findByProjectIdAndStatusOrderByImportanceDescPlantedChapterAsc(
            String projectId,
            String status
    );

    Optional<NarrativeDebtEntity> findByProjectIdAndSourceRef(String projectId, String sourceRef);
}
