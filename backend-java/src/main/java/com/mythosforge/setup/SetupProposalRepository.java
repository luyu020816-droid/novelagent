package com.mythosforge.setup;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SetupProposalRepository extends JpaRepository<SetupProposalEntity, String> {

    List<SetupProposalEntity> findByProjectIdAndStageOrderByCreatedAtDesc(String projectId, String stage);

    Optional<SetupProposalEntity> findFirstByProjectIdAndStageAndStatusOrderByCreatedAtDesc(
            String projectId,
            String stage,
            String status
    );

    List<SetupProposalEntity> findByProjectIdOrderByCreatedAtDesc(String projectId);
}
