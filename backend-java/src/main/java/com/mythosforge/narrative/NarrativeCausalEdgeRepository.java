package com.mythosforge.narrative;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NarrativeCausalEdgeRepository extends JpaRepository<NarrativeCausalEdgeEntity, String> {

    List<NarrativeCausalEdgeEntity> findByProjectIdAndStatusOrderByImportanceDescPlantedChapterAsc(
            String projectId,
            String status
    );
}
